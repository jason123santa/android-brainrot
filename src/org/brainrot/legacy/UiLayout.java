// SPDX-License-Identifier: Apache-2.0
package org.brainrot.legacy;

/** Testable sizing rules only; colors, fonts and widget minimums remain native. */
final class UiLayout {
    final int paddingDp, chatPaddingDp, gapDp, inputLines, statusLines;
    final boolean showStatus, stackedComposer;

    private UiLayout(boolean compact, boolean veryCompact, boolean stacked) {
        paddingDp = veryCompact ? 2 : (compact ? 4 : 6);
        chatPaddingDp = compact ? 2 : 4;
        gapDp = compact ? 2 : 4;
        inputLines = veryCompact ? 1 : (compact ? 2 : 3);
        statusLines = compact ? 1 : 2;
        showStatus = !veryCompact;
        stackedComposer = stacked;
    }

    static UiLayout forWindow(int widthPx, int heightPx, float density, float scaledDensity) {
        if (widthPx < 0 || heightPx < 0 || !(density > 0) || !(scaledDensity > 0))
            throw new IllegalArgumentException("Invalid display metrics");
        float widthDp = widthPx / density;
        float heightSp = heightPx / scaledDensity;
        boolean compact = widthDp < 320 || heightSp < 280;
        boolean veryCompact = heightSp < 180;
        // Stack only when narrow AND tall enough; a short keyboard window needs
        // the inline row to leave vertical space for the conversation.
        return new UiLayout(compact, veryCompact, widthDp < 260 && heightSp >= 280);
    }

    boolean sameAs(UiLayout other) {
        return other != null && paddingDp == other.paddingDp && chatPaddingDp == other.chatPaddingDp &&
            gapDp == other.gapDp && inputLines == other.inputLines && statusLines == other.statusLines &&
            showStatus == other.showStatus && stackedComposer == other.stackedComposer;
    }
}
