// SPDX-License-Identifier: Apache-2.0
package org.brainrot.legacy;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
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
import java.io.InputStream;

/** API 3-only UI. All model loading and inference stay off the main thread. */
public final class ChatActivity extends Activity {
    private final Handler handler = new Handler();
    private volatile boolean destroyed;
    private Model model;
    private Thread worker;
    private EditText input;
    private Button send;
    private TextView title, status, transcript;
    private ScrollView scroll;
    private static final int CONTEXT = 128, NEW_TOKENS = 32;

    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        LinearLayout root = new LinearLayout(this) {
            protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
                super.onSizeChanged(width, height, oldWidth, oldHeight);
                if (title == null || status == null || input == null) return;
                // Use the actual available height (including IME/multi-window resizing)
                // and account for larger font settings, without newer display APIs.
                float textScale = getResources().getDisplayMetrics().scaledDensity;
                boolean compact = height < 280 * textScale;
                boolean veryCompact = height < 180 * textScale;
                title.setVisibility(compact ? View.GONE : View.VISIBLE);
                status.setVisibility(veryCompact ? View.GONE : View.VISIBLE);
                status.setMaxLines(compact ? 1 : 3);
                input.setMaxLines(veryCompact ? 1 : (compact ? 2 : 3));
            }
        };
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.setBackgroundColor(0xfff3f5f7);
        // Do not focus the editor/open the keyboard automatically on launch.
        root.setFocusableInTouchMode(true);
        title = new TextView(this);
        title.setText("Brainrot 10M");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xff17212b);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        status = new TextView(this);
        status.setTextSize(14);
        status.setTextColor(0xff455a64);
        status.setMaxLines(3);
        status.setText("Loading offline model...");
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setSmoothScrollingEnabled(false);
        scroll.setBackgroundColor(0xffffffff);
        transcript = new TextView(this);
        transcript.setTextSize(16);
        transcript.setTextColor(0xff17212b);
        transcript.setPadding(dp(12), dp(10), dp(12), dp(10));
        transcript.setGravity(Gravity.TOP | Gravity.LEFT);
        transcript.setLineSpacing(dp(2), 1.0f);
        String previous = savedState == null ? null : savedState.getString("transcript");
        if (previous == null) previous = "Send a short message to start. Replies are generated offline.\nEach message starts a fresh context.\n";
        if (savedState != null && savedState.getBoolean("generating"))
            previous += "\n[The previous reply stopped when the screen was recreated.]\n";
        transcript.setText(previous);
        scroll.addView(transcript, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams chatSize = new LinearLayout.LayoutParams(-1, 0, 1);
        chatSize.topMargin = dp(8); chatSize.bottomMargin = dp(8);
        root.addView(scroll, chatSize);
        // One compact composer row leaves room for chat above the software keyboard.
        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.BOTTOM);
        input = new EditText(this);
        input.setHint("Message");
        input.setTextSize(16);
        input.setTextColor(0xff17212b);
        input.setHintTextColor(0xff607d8b);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                           InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMinLines(1); input.setMaxLines(3); input.setMinHeight(dp(48));
        input.setGravity(Gravity.TOP | Gravity.LEFT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(512)});
        if (savedState != null && savedState.getString("draft") != null)
            input.setText(savedState.getString("draft"));
        input.setEnabled(false);
        composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        send = new Button(this);
        send.setText("Send"); send.setTextSize(16); send.setTextColor(0xffffffff);
        send.setMinWidth(dp(72)); send.setMinHeight(dp(48));
        send.setPadding(dp(12), dp(8), dp(12), dp(8));
        StateListDrawable buttonBackground = new StateListDrawable();
        buttonBackground.addState(new int[] {-android.R.attr.state_enabled}, rounded(0xff607d8b));
        buttonBackground.addState(new int[] {android.R.attr.state_pressed}, rounded(0xff0d47a1));
        buttonBackground.addState(new int[] {android.R.attr.state_focused}, rounded(0xff0d47a1));
        buttonBackground.addState(new int[0], rounded(0xff1565c0));
        send.setBackgroundDrawable(buttonBackground);
        send.setEnabled(false);
        LinearLayout.LayoutParams actionSize = new LinearLayout.LayoutParams(-2, -2);
        actionSize.leftMargin = dp(8);
        composer.addView(send, actionSize);
        root.addView(composer, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        send.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (worker != null) {
                    worker.interrupt(); status.setText("Stopping...");
                    send.setText("Stopping..."); send.setEnabled(false);
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
                            status.setText("Ready - 128-token context, 32-token replies. CPU only.");
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

    private GradientDrawable rounded(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(6));
        return drawable;
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
        status.setText("Thinking... Tap Stop to cancel.");
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
