// SPDX-License-Identifier: Apache-2.0
package org.brainrot.legacy;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;

/** API 3-only UI. All model loading and inference stay off the main thread. */
public final class ChatActivity extends Activity {
    private final Handler handler = new Handler();
    private volatile boolean destroyed;
    private Model model;
    private Thread worker;
    private EditText input;
    private Button send;
    private TextView status, transcript;
    private ScrollView scroll;
    private LinearLayout composer;
    private LinearLayout.LayoutParams chatSize;
    private UiLayout layoutPolicy;
    private static final int CONTEXT = 128, NEW_TOKENS = 32;

    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        LinearLayout root = new LinearLayout(this) {
            protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
                super.onSizeChanged(width, height, oldWidth, oldHeight);
                if (status == null || input == null || send == null || composer == null) return;
                ChatActivity.this.applyUiLayout(this, UiLayout.forWindow(width, height,
                    getResources().getDisplayMetrics().density,
                    getResources().getDisplayMetrics().scaledDensity));
            }
        };
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(4), dp(4), dp(4), dp(4));
        // Native window/theme provides the background, fonts and colors.
        // Avoid an extra in-app title that takes space from small screens.
        root.setFocusableInTouchMode(true);
        status = new TextView(this);
        status.setTextAppearance(this, android.R.style.TextAppearance_Small);
        status.setMaxLines(2);
        status.setText("Loading model...");
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setSmoothScrollingEnabled(false);
        transcript = new TextView(this);
        transcript.setTextAppearance(this, android.R.style.TextAppearance_Medium);
        transcript.setPadding(dp(4), dp(4), dp(4), dp(4));
        transcript.setGravity(Gravity.TOP | Gravity.LEFT);
        String previous = savedState == null ? null : savedState.getString("transcript");
        if (previous == null) previous = "Offline chat. Keep messages short; each starts fresh.\n";
        if (savedState != null && savedState.getBoolean("generating"))
            previous += "\n[The previous reply stopped when the screen was recreated.]\n";
        transcript.setText(previous);
        scroll.addView(transcript, new ScrollView.LayoutParams(-1, -2));
        chatSize = new LinearLayout.LayoutParams(-1, 0, 1);
        chatSize.topMargin = dp(2); chatSize.bottomMargin = dp(2);
        root.addView(scroll, chatSize);
        // Native controls keep their own padding, state drawables and minimum sizes.
        composer = new LinearLayout(this);
        composer.setGravity(Gravity.BOTTOM);
        input = new EditText(this);
        input.setHint("Message");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                           InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMinLines(1); input.setMaxLines(2);
        input.setGravity(Gravity.TOP | Gravity.LEFT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(512)});
        if (savedState != null && savedState.getString("draft") != null)
            input.setText(savedState.getString("draft"));
        input.setEnabled(false);
        composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        send = new Button(this);
        send.setText("Send");
        send.setEnabled(false);
        LinearLayout.LayoutParams actionSize = new LinearLayout.LayoutParams(-2, -2);
        actionSize.leftMargin = dp(2);
        composer.addView(send, actionSize);
        root.addView(composer, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        send.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (worker != null) {
                    worker.interrupt(); status.setText("Stopping...");
                    send.setEnabled(false);
                } else beginGeneration();
            }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView view, int action, KeyEvent event) {
                if (action != EditorInfo.IME_ACTION_SEND) return false;
                beginGeneration(); return true;
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
                            status.setText("Ready (offline)");
                        }
                    });
                } catch (Exception e) { failure("Could not load model: " + e.toString(), true); }
                catch (OutOfMemoryError e) { failure("Not enough memory to load the model. Try a device with a larger app heap.", true); }
            }
        }, "model-loader");
        worker.start();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void applyUiLayout(LinearLayout root, UiLayout policy) {
        if (policy.sameAs(layoutPolicy)) return;
        layoutPolicy = policy;
        int padding = dp(policy.paddingDp);
        root.setPadding(padding, padding, padding, padding);
        int chatPadding = dp(policy.chatPaddingDp);
        transcript.setPadding(chatPadding, chatPadding, chatPadding, chatPadding);
        status.setVisibility(policy.showStatus ? View.VISIBLE : View.GONE);
        status.setMaxLines(policy.statusLines);
        input.setMaxLines(policy.inputLines);
        chatSize.topMargin = dp(policy.gapDp); chatSize.bottomMargin = dp(policy.gapDp);
        scroll.setLayoutParams(chatSize);
        composer.setOrientation(policy.stackedComposer ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        composer.setGravity(policy.stackedComposer ? Gravity.RIGHT : Gravity.BOTTOM);
        input.setLayoutParams(new LinearLayout.LayoutParams(policy.stackedComposer ? -1 : 0, -2,
                              policy.stackedComposer ? 0 : 1));
        LinearLayout.LayoutParams actionSize = new LinearLayout.LayoutParams(-2, -2);
        actionSize.gravity = policy.stackedComposer ? Gravity.RIGHT : Gravity.BOTTOM;
        actionSize.leftMargin = policy.stackedComposer ? 0 : dp(policy.gapDp);
        actionSize.topMargin = policy.stackedComposer ? dp(policy.gapDp) : 0;
        send.setLayoutParams(actionSize);
    }

    private void updateTranscript(String text, boolean forceBottom) {
        // Follow output only if the reader was already near the bottom.
        boolean nearBottom = transcript.getHeight() - scroll.getHeight() - scroll.getScrollY() <= dp(32);
        transcript.setText(text);
        if (forceBottom || nearBottom) scroll.post(new Runnable() {
            public void run() {
                if (!destroyed) scroll.scrollTo(0, transcript.getHeight());
            }
        });
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
        updateTranscript(prefix, true);
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
        send.setText("Stop"); send.setEnabled(true); input.setEnabled(false);
        status.setText("Thinking...");
        worker = new Thread(new Runnable() {
            public void run() {
                try {
                    final String reply = model.generate(message, NEW_TOKENS, new Model.Listener() {
                        public void progress(final String text, final int generated) {
                            post(new Runnable() {
                                public void run() {
                                    updateTranscript(prefix + text, false);
                                    status.setText("Generated " + generated + "/" + NEW_TOKENS + " tokens");
                                }
                            });
                        }
                    });
                    post(new Runnable() {
                        public void run() {
                            updateTranscript(prefix + (reply.length() == 0 ? "(no text)" : reply) + "\n", false);
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
                // Native feedback stays visible even if a tiny keyboard window hides status.
                if (!"Stopped".equals(message))
                    Toast.makeText(ChatActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void finishWork(String message) {
        worker = null;
        send.setText("Send"); send.setEnabled(model != null); input.setEnabled(model != null);
        status.setText(message);
    }

    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("transcript", transcript.getText().toString());
        state.putString("draft", input.getText().toString());
        state.putBoolean("generating", worker != null && model != null);
    }

    protected void onDestroy() {
        destroyed = true;
        if (worker != null) worker.interrupt();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
