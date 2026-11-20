// SPDX-License-Identifier: Apache-2.0
package org.brainrot.legacy;

/** Exercises sizing policy, not Android rendering or real widget measurements. */
public final class UiLayoutCheck {
    private static void expect(int width, int height, float density, float scaledDensity,
                               int lines, boolean status, boolean stacked, int padding) {
        UiLayout policy = UiLayout.forWindow(width, height, density, scaledDensity);
        if (policy.inputLines != lines || policy.showStatus != status ||
            policy.stackedComposer != stacked || policy.paddingDp != padding)
            throw new AssertionError("Unexpected layout policy at " + width + "x" + height);
    }

    public static void main(String[] args) {
        expect(240, 320, 1, 1, 2, true, true, 4);   // Small, tall phone: full-width editor.
        expect(240, 160, 1, 1, 1, false, false, 2); // Same phone with a short keyboard window.
        expect(320, 480, 1, 1, 3, true, false, 6);
        expect(320, 180, 1, 1, 2, true, false, 4);  // Exact very-compact boundary.
        expect(320, 280, 1, 1, 3, true, false, 6);  // Exact compact boundary.
        expect(259, 280, 1, 1, 2, true, true, 4);
        expect(260, 280, 1, 1, 2, true, false, 4);
        expect(1080, 1920, 3, 3, 3, true, false, 6);
        expect(1080, 600, 3, 3, 2, true, false, 4);
        expect(1080, 600, 3, 6, 1, false, false, 2); // Larger fonts need tighter layout.
        expect(480, 800, 2, 2, 2, true, true, 4);
        expect(480, 320, 2, 2, 1, false, false, 2);
        expect(1600, 1200, 2, 2, 3, true, false, 6);
        expect(0, 0, 1, 1, 1, false, false, 2);
        if (!UiLayout.forWindow(360, 640, 1, 1).sameAs(UiLayout.forWindow(1080, 1920, 3, 3)))
            throw new AssertionError("Density changed equivalent layout decisions");
        if (UiLayout.forWindow(240, 320, 1, 1).sameAs(null))
            throw new AssertionError("Null policy matched");
        boolean rejected = false;
        try { UiLayout.forWindow(240, 320, 0, 1); } catch (IllegalArgumentException expected) { rejected = true; }
        if (!rejected) throw new AssertionError("Invalid density accepted");
        int cases = 0;
        int[] widths = {160, 240, 260, 320, 360, 600};
        int[] heights = {100, 160, 180, 240, 280, 480};
        float[] densities = {1, 2, 3};
        float[] fonts = {1, 1.5f, 2};
        for (int w = 0; w < widths.length; w++) for (int h = 0; h < heights.length; h++)
            for (int d = 0; d < densities.length; d++) for (int f = 0; f < fonts.length; f++) {
                float density = densities[d], textScale = density * fonts[f];
                int width = (int) (widths[w] * density), height = (int) (heights[h] * density);
                UiLayout policy = UiLayout.forWindow(width, height, density, textScale);
                if (policy.inputLines < 1 || policy.inputLines > 3 || policy.paddingDp < 2 || policy.paddingDp > 6)
                    throw new AssertionError("Sizing bounds violated");
                if (policy.stackedComposer && (width / density >= 260 || height / textScale < 280))
                    throw new AssertionError("Composer stacked without sufficient room");
                if (height / textScale < 180 && (policy.showStatus || policy.inputLines != 1))
                    throw new AssertionError("Tiny window was not compacted");
                cases++;
            }
        System.out.println("PASS: native-UI sizing policy across " + cases + " screen/density/font cases and boundary checks");
    }
}
