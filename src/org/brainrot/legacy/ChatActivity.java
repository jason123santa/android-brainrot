// SPDX-License-Identifier: Apache-2.0
package org.brainrot.legacy;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputFilter;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;

/** API 3-only UI. All model loading and inference stay off the main thread. */
public final class ChatActivity extends Activity {
    private final Handler handler = new Handler();
    private volatile boolean destroyed;
    private Model model;
    private Thread worker;
    private EditText input;
    private Button send, stop;
    private TextView status, transcript;
    private ScrollView scroll;
    private static final int CONTEXT = 128, NEW_TOKENS = 32;

    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (8 * getResources().getDisplayMetrics().density + 0.5f);
        root.setPadding(padding, padding, padding, padding);
        status = new TextView(this);
        status.setText("Loading offline model...");
        root.addView(status);
        scroll = new ScrollView(this);
        transcript = new TextView(this);
        transcript.setText("Brainrot 10M - offline\nEach message starts a fresh context. Keep messages short.\n");
        transcript.setTextSize(16);
        scroll.addView(transcript);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        input = new EditText(this);
        input.setHint("Message (short prompts work best)");
        input.setMaxLines(3);
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(512)});
        input.setEnabled(false);
        root.addView(input, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout buttons = new LinearLayout(this);
        send = new Button(this); send.setText("Send"); send.setEnabled(false);
        stop = new Button(this); stop.setText("Stop"); stop.setEnabled(false);
        buttons.addView(send, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(stop, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(buttons);
        setContentView(root);
        send.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) { beginGeneration(); }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (worker != null) { worker.interrupt(); status.setText("Stopping..."); }
                stop.setEnabled(false);
            }
        });
        worker = new Thread(new Runnable() {
            public void run() {
                try {
                    InputStream stream = getAssets().open("model.bin");
                    final Model loaded;
                    try { loaded = new Model(stream, CONTEXT); } finally { stream.close(); }
                    post(new Runnable() {
                        public void run() {
                            model = loaded; worker = null;
                            input.setEnabled(true); send.setEnabled(true);
                            status.setText("Ready - 128-token context, 32-token replies. CPU only.");
                        }
                    });
                } catch (Exception e) { failure("Could not load model: " + e.toString(), true); }
                catch (OutOfMemoryError e) { failure("Not enough memory to load the model. Try a device with a larger app heap.", true); }
            }
        }, "model-loader");
        worker.start();
    }

    private void post(final Runnable action) {
        if (destroyed) return;
        handler.post(new Runnable() {
            public void run() { if (!destroyed) action.run(); }
        });
    }

    private void beginGeneration() {
        if (worker != null || model == null) return;
        final String message = input.getText().toString().trim();
        if (message.length() == 0) return;
        String old = transcript.getText().toString();
        if (old.length() > 4000) old = old.substring(old.length() - 4000);
        final String prefix = old + "\nYou: " + message + "\nBot: ";
        transcript.setText(prefix);
        send.setEnabled(false); stop.setEnabled(true); input.setEnabled(false);
        status.setText("Thinking... Old CPUs may take a long time. Stop cancels.");
        worker = new Thread(new Runnable() {
            public void run() {
                try {
                    final String reply = model.generate(message, NEW_TOKENS, new Model.Listener() {
                        public void progress(final String text, final int generated) {
                            post(new Runnable() {
                                public void run() {
                                    transcript.setText(prefix + text);
                                    status.setText("Generated " + generated + "/" + NEW_TOKENS + " tokens");
                                    scroll.post(new Runnable() {
                                        public void run() { if (!destroyed) scroll.fullScroll(View.FOCUS_DOWN); }
                                    });
                                }
                            });
                        }
                    });
                    post(new Runnable() {
                        public void run() {
                            transcript.setText(prefix + (reply.length() == 0 ? "(no text)" : reply) + "\n");
                            input.setText("");
                            finishWork("Ready");
                        }
                    });
                } catch (InterruptedException e) { failure("Stopped", false); }
                catch (Exception e) { failure(e.toString(), false); }
                catch (OutOfMemoryError e) { failure("Not enough memory. Restart the app or use a larger-heap device.", true); }
            }
        }, "model-generation");
        worker.start();
    }

    private void failure(final String message, final boolean fatal) {
        post(new Runnable() {
            public void run() {
                if (fatal) model = null;
                finishWork(message);
            }
        });
    }

    private void finishWork(String message) {
        worker = null;
        send.setEnabled(model != null); input.setEnabled(model != null); stop.setEnabled(false);
        status.setText(message);
    }

    protected void onDestroy() {
        destroyed = true;
        if (worker != null) worker.interrupt();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
