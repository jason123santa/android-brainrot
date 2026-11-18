// SPDX-License-Identifier: Apache-2.0
package org.brainrot.legacy;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.Random;

/** Scalar int8-weight/float32-activation Llama. Java 5 syntax; no JNI or SIMD. */
public final class Model {
    public final Tokenizer tokenizer;
    public final int context;
    private final int dim, hidden, layers, heads, kvHeads, headSize, vocab, kvDim;
    private final float epsilon;
    private final Matrix embedding;
    private final Layer[] blocks;
    private final float[] finalNorm;
    private final float[] x, norm, q, k, v, attention, projected, gate, up, scores, logits;
    private final float[] ropeCos, ropeSin;
    private int position;

    private static final class Matrix {
        final int rows, columns;
        final float[] scales;
        final byte[] weights;
        Matrix(DataInputStream in, int rows, int columns) throws IOException {
            this.rows = rows; this.columns = columns;
            scales = readFloats(in, rows);
            weights = new byte[rows * columns];
            in.readFully(weights);
        }
        void multiply(float[] input, float[] output) {
            for (int row = 0; row < rows; row++) {
                float sum = 0;
                int offset = row * columns;
                for (int col = 0; col < columns; col++) sum += weights[offset + col] * input[col];
                output[row] = sum * scales[row];
            }
        }
        void row(int index, float[] output) {
            int offset = index * columns;
            float scale = scales[index];
            for (int i = 0; i < columns; i++) output[i] = weights[offset + i] * scale;
        }
    }

    private final class Layer {
        final float[] inputNorm, postNorm, keys, values;
        final Matrix wq, wk, wv, wo, wg, wu, wd;
        Layer(DataInputStream in) throws IOException {
            inputNorm = readFloats(in, dim);
            wq = new Matrix(in, dim, dim);
            wk = new Matrix(in, kvDim, dim);
            wv = new Matrix(in, kvDim, dim);
            wo = new Matrix(in, dim, dim);
            postNorm = readFloats(in, dim);
            wg = new Matrix(in, hidden, dim);
            wu = new Matrix(in, hidden, dim);
            wd = new Matrix(in, dim, hidden);
            keys = new float[context * kvDim];
            values = new float[context * kvDim];
        }
    }

    /** Reads directly into final arrays. The caller owns and closes the input stream. */
    public Model(InputStream stream, int requestedContext) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(stream, 32768));
        if (in.readInt() != 0x42524a31) throw new IOException("Not a BRJ1 model");
        dim = in.readInt(); hidden = in.readInt(); layers = in.readInt();
        heads = in.readInt(); kvHeads = in.readInt(); headSize = in.readInt();
        vocab = in.readInt(); int maxContext = in.readInt(), special = in.readInt();
        epsilon = in.readFloat(); float theta = in.readFloat();
        // This format deliberately supports this small model, not arbitrary allocations.
        if (dim != 256 || hidden != 704 || layers != 12 || heads != 4 || kvHeads != 2 ||
            headSize != 64 || vocab != 4096 || special != 9 || maxContext != 1024 ||
            epsilon <= 0 || theta <= 0) throw new IOException("Unsupported model configuration");
        if (requestedContext < 1 || requestedContext > maxContext)
            throw new IOException("Context must be between 1 and " + maxContext);
        context = requestedContext;
        kvDim = kvHeads * headSize;
        tokenizer = new Tokenizer(in, vocab, special);
        embedding = new Matrix(in, vocab, dim);
        blocks = new Layer[layers];
        for (int i = 0; i < layers; i++) blocks[i] = new Layer(in);
        finalNorm = readFloats(in, dim);
        if (in.read() != -1) throw new IOException("Unexpected trailing model data");
        x = new float[dim]; norm = new float[dim]; q = new float[dim];
        k = new float[kvDim]; v = new float[kvDim]; attention = new float[dim];
        projected = new float[dim]; gate = new float[hidden]; up = new float[hidden];
        scores = new float[context]; logits = new float[vocab];
        ropeCos = new float[context * headSize / 2];
        ropeSin = new float[ropeCos.length];
        for (int p = 0; p < context; p++) {
            for (int j = 0; j < headSize / 2; j++) {
                double angle = p / Math.pow(theta, 2.0 * j / headSize);
                ropeCos[p * headSize / 2 + j] = (float) Math.cos(angle);
                ropeSin[p * headSize / 2 + j] = (float) Math.sin(angle);
            }
        }
    }

    private static float[] readFloats(DataInputStream in, int count) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Model loading cancelled");
        float[] values = new float[count];
        for (int i = 0; i < count; i++) values[i] = in.readFloat();
        return values;
    }

    public void reset() { position = 0; } // Old KV entries are never read after reset.

    private void rms(float[] input, float[] weights, float[] output) {
        float sum = 0;
        for (int i = 0; i < dim; i++) sum += input[i] * input[i];
        float scale = (float) (1.0 / Math.sqrt(sum / dim + epsilon));
        for (int i = 0; i < dim; i++) output[i] = input[i] * scale * weights[i];
    }

    private void rope(float[] data, int count) {
        int half = headSize / 2, offset = position * half;
        // HF Llama uses split-half rotate_half, not adjacent-pair RoPE.
        for (int h = 0; h < count; h++) {
            int base = h * headSize;
            for (int j = 0; j < half; j++) {
                float a = data[base + j], b = data[base + j + half];
                float c = ropeCos[offset + j], s = ropeSin[offset + j];
                data[base + j] = a * c - b * s;
                data[base + j + half] = b * c + a * s;
            }
        }
    }

    /** Returned logits are a reusable internal buffer; consume/copy before next call. */
    public float[] forward(int token) throws InterruptedException {
        if (position >= context) throw new IllegalStateException("Context is full");
        if (token < 0 || token >= vocab) throw new IllegalArgumentException("Invalid token");
        embedding.row(token, x);
        for (int l = 0; l < layers; l++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            Layer layer = blocks[l];
            rms(x, layer.inputNorm, norm);
            layer.wq.multiply(norm, q); layer.wk.multiply(norm, k); layer.wv.multiply(norm, v);
            rope(q, heads); rope(k, kvHeads);
            System.arraycopy(k, 0, layer.keys, position * kvDim, kvDim);
            System.arraycopy(v, 0, layer.values, position * kvDim, kvDim);
            for (int h = 0; h < heads; h++) {
                int qb = h * headSize, kb = (h / (heads / kvHeads)) * headSize;
                float max = -Float.MAX_VALUE;
                for (int t = 0; t <= position; t++) {
                    float sum = 0;
                    int offset = t * kvDim + kb;
                    for (int j = 0; j < headSize; j++) sum += q[qb + j] * layer.keys[offset + j];
                    scores[t] = sum / (float) Math.sqrt(headSize);
                    if (scores[t] > max) max = scores[t];
                }
                float total = 0;
                for (int t = 0; t <= position; t++) {
                    scores[t] = (float) Math.exp(scores[t] - max); total += scores[t];
                }
                for (int j = 0; j < headSize; j++) attention[qb + j] = 0;
                for (int t = 0; t <= position; t++) {
                    float weight = scores[t] / total;
                    int offset = t * kvDim + kb;
                    for (int j = 0; j < headSize; j++)
                        attention[qb + j] += weight * layer.values[offset + j];
                }
            }
            layer.wo.multiply(attention, projected);
            for (int j = 0; j < dim; j++) x[j] += projected[j];
            rms(x, layer.postNorm, norm);
            layer.wg.multiply(norm, gate); layer.wu.multiply(norm, up);
            for (int j = 0; j < hidden; j++) gate[j] =
                (float) (gate[j] / (1.0 + Math.exp(-gate[j]))) * up[j];
            layer.wd.multiply(gate, projected);
            for (int j = 0; j < dim; j++) x[j] += projected[j];
        }
        rms(x, finalNorm, norm);
        embedding.multiply(norm, logits);
        position++;
        return logits;
    }

    public interface Listener {
        void progress(String text, int generated);
    }

    /** Single-turn template matches the checkpoint's chat_template.jinja. */
    public String generate(String user, int maxNew, Listener listener) throws IOException, InterruptedException {
        int[] prompt = tokenizer.encode("User: " + user + "\nAssistant:");
        if (maxNew < 1 || prompt.length + maxNew > context)
            throw new IOException("Message too long: " + prompt.length + " tokens; maximum " +
                                  (context - maxNew) + ". Shorten it.");
        reset();
        float[] output = null;
        for (int i = 0; i < prompt.length; i++) output = forward(prompt[i]);
        int[] generated = new int[maxNew];
        int count = 0;
        String text = "";
        Random random = new Random();
        boolean[] seen = new boolean[vocab];
        for (int i = 0; i < prompt.length; i++) seen[prompt[i]] = true;
        for (int n = 0; n < maxNew; n++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            int next = sample(output, seen, random);
            if (next == 0 || next == 4 || next == 8) break;
            seen[next] = true;
            generated[count++] = next;
            text = tokenizer.decode(generated, count);
            String[] stops = {"\nUser:", "\nAssistant:", "\nSystem:"};
            boolean stop = false;
            for (int i = 0; i < stops.length; i++) {
                int at = text.indexOf(stops[i]);
                if (at >= 0) { text = text.substring(0, at); stop = true; }
            }
            if (listener != null) listener.progress(text, count);
            if (stop || n + 1 == maxNew) break;
            output = forward(next);
        }
        return text.trim();
    }

    private int sample(float[] output, boolean[] seen, Random random) {
        final int topK = 40;
        int[] ids = new int[topK];
        float[] best = new float[topK];
        for (int i = 0; i < topK; i++) best[i] = -Float.MAX_VALUE;
        for (int i = 0; i < vocab; i++) {
            if (i > 0 && i < 9 && i != 4 && i != 8) continue;
            float value = output[i];
            if (seen[i]) value = value < 0 ? value * 1.15f : value / 1.15f;
            value /= 0.8f;
            if (value <= best[topK - 1]) continue;
            int at = topK - 1;
            while (at > 0 && value > best[at - 1]) {
                best[at] = best[at - 1]; ids[at] = ids[at - 1]; at--;
            }
            best[at] = value; ids[at] = i;
        }
        float sum = 0, max = best[0];
        for (int i = 0; i < topK; i++) { best[i] = (float) Math.exp(best[i] - max); sum += best[i]; }
        float pick = random.nextFloat() * sum;
        for (int i = 0; i < topK; i++) { pick -= best[i]; if (pick <= 0) return ids[i]; }
        return ids[0];
    }
}
