// SPDX-License-Identifier: Apache-2.0
import org.brainrot.legacy.Model;
import java.io.*;

/** Runs the exact Android engine on a desktop JVM, not an alternate implementation. */
public final class HostCheck {
    public static void main(String[] args) throws Exception {
        InputStream in = new FileInputStream(args[0]);
        Model model;
        try { model = new Model(in, 128); } finally { in.close(); }
        BufferedReader fixtures = new BufferedReader(new InputStreamReader(new FileInputStream(args[1]), "UTF-8"));
        PrintWriter ids = new PrintWriter(new OutputStreamWriter(new FileOutputStream(args[2]), "UTF-8"));
        String text;
        while ((text = fixtures.readLine()) != null) {
            // Fixtures are UTF-8 hex so embedded newlines are unambiguous.
            byte[] bytes = new byte[text.length() / 2];
            for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(text.substring(i*2, i*2+2), 16);
            text = new String(bytes, "UTF-8");
            int[] encoded = model.tokenizer.encode(text);
            for (int i = 0; i < encoded.length; i++) {
                if (i != 0) ids.print(",");
                ids.print(encoded[i]);
            }
            ids.println();
            if (text.indexOf("<|") < 0 && text.indexOf("<think>") < 0 && text.indexOf("</think>") < 0 &&
                !text.equals(model.tokenizer.decode(encoded, encoded.length)))
                throw new AssertionError("Token round trip failed");
        }
        ids.close(); fixtures.close();
        DataOutputStream logits = new DataOutputStream(new FileOutputStream(args[3]));
        int[] prompt = model.tokenizer.encode("User: hello\nAssistant:");
        float[] last = null;
        for (int i = 0; i < prompt.length; i++) {
            last = model.forward(prompt[i]);
            for (int j = 0; j < last.length; j++) logits.writeFloat(last[j]);
        }
        // KV reset must reproduce the same logits exactly.
        float[] original = (float[]) last.clone();
        model.reset();
        for (int i = 0; i < prompt.length; i++) last = model.forward(prompt[i]);
        for (int i = 0; i < last.length; i++) if (last[i] != original[i]) throw new AssertionError("Cache reset failed");
        // Exercise incremental generation through the same path as the UI.
        String reply = model.generate("hello", 12, null);
        System.out.println("Generation smoke test: " + reply);
        boolean rejected = false;
        try { model.generate("hello", 128, null); } catch (IOException expected) { rejected = true; }
        if (!rejected) throw new AssertionError("Context overflow was accepted");
        Thread.currentThread().interrupt();
        try { model.forward(prompt[0]); throw new AssertionError("Cancellation ignored"); }
        catch (InterruptedException expected) { Thread.interrupted(); }
        logits.close();
        System.out.println("Cache reset, context bounds and cancellation: PASS");
    }
}
