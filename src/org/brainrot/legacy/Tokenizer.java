package org.brainrot.legacy;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The model's Digits + GPT-2 byte-level BPE, without a native tokenizer. */
public final class Tokenizer {
    private final byte[][] tokens;
    private final String[] special;
    private final int[] byteIds = new int[256];
    // Packed value: merge rank in high 32 bits, result token ID in low bits.
    private final HashMap<Long, Long> merges = new HashMap<Long, Long>();
    private static final String WS = "[\\p{Z}\\u0009-\\u000d\\u0085]";
    private static final Pattern PIECES = Pattern.compile(
        "'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\p{Z}\\u0009-\\u000d\\u0085\\p{L}\\p{N}]+|" +
        WS + "+(?![^\\p{Z}\\u0009-\\u000d\\u0085])|" + WS + "+");

    Tokenizer(DataInputStream in, int vocab, int specialCount) throws IOException {
        tokens = new byte[vocab][];
        special = new String[specialCount];
        for (int i = 0; i < 256; i++) byteIds[i] = -1;
        for (int i = 0; i < vocab; i++) {
            int size = in.readInt();
            if (size < 0 || size > 65536) throw new IOException("Invalid token size");
            tokens[i] = new byte[size];
            in.readFully(tokens[i]);
            if (i < specialCount) special[i] = new String(tokens[i], "UTF-8");
            else if (size == 1) byteIds[tokens[i][0] & 255] = i;
        }
        for (int i = 0; i < 256; i++) {
            if (byteIds[i] < 0) throw new IOException("Missing byte token");
        }
        int count = in.readInt();
        if (count < 0 || count > 1000000) throw new IOException("Invalid merge count");
        for (int i = 0; i < count; i++) {
            int left = in.readInt(), right = in.readInt(), result = in.readInt();
            if (left < 0 || left >= vocab || right < 0 || right >= vocab || result < 0 || result >= vocab)
                throw new IOException("Invalid BPE merge");
            merges.put(Long.valueOf(key(left, right)), Long.valueOf(((long) i << 32) | result));
        }
    }

    private static long key(int left, int right) {
        return ((long) left << 32) | (right & 0xffffffffL);
    }

    public int[] encode(String text) throws IOException {
        ArrayList<Integer> result = new ArrayList<Integer>();
        int start = 0;
        while (start < text.length()) {
            int at = text.length(), which = -1;
            for (int i = 0; i < special.length; i++) {
                int found = text.indexOf(special[i], start);
                if (found >= 0 && found < at) { at = found; which = i; }
            }
            encodeOrdinary(text.substring(start, at), result);
            if (which < 0) break;
            result.add(Integer.valueOf(which));
            start = at + special[which].length();
        }
        int[] ids = new int[result.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = result.get(i).intValue();
        return ids;
    }

    private void encodeOrdinary(String text, ArrayList<Integer> result) throws IOException {
        // Digits pre-tokenization happens BEFORE ByteLevel and isolates each number.
        int begin = 0;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            int next = i + Character.charCount(cp);
            int type = Character.getType(cp);
            if (type == Character.DECIMAL_DIGIT_NUMBER || type == Character.LETTER_NUMBER ||
                type == Character.OTHER_NUMBER) {
                encodePieces(text.substring(begin, i), result);
                encodePieces(text.substring(i, next), result);
                begin = next;
            }
            i = next;
        }
        encodePieces(text.substring(begin), result);
    }

    private void encodePieces(String text, ArrayList<Integer> result) throws IOException {
        Matcher matcher = PIECES.matcher(text);
        while (matcher.find()) {
            byte[] bytes = matcher.group().getBytes("UTF-8");
            int[] ids = new int[bytes.length];
            for (int i = 0; i < ids.length; i++) ids[i] = byteIds[bytes[i] & 255];
            int n = ids.length;
            while (n > 1) {
                long best = Long.MAX_VALUE;
                int index = -1, replacement = -1;
                for (int i = 0; i < n - 1; i++) {
                    Long merge = merges.get(Long.valueOf(key(ids[i], ids[i + 1])));
                    if (merge != null && merge.longValue() < best) {
                        best = merge.longValue(); index = i; replacement = (int) best;
                    }
                }
                if (index < 0) break;
                ids[index] = replacement;
                System.arraycopy(ids, index + 2, ids, index + 1, n - index - 2);
                n--;
            }
            for (int i = 0; i < n; i++) result.add(Integer.valueOf(ids[i]));
        }
    }

    public String decode(int[] ids, int count) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < count; i++) {
            if (ids[i] >= special.length) out.write(tokens[ids[i]]);
        }
        return new String(out.toByteArray(), "UTF-8");
    }
}
