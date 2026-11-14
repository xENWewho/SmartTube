package com.liskovsoft.smartyoutubetv2.tv.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Premium focus style (YouTube color scheme): a white rounded ring around the focused card
 * instead of a flat white box. Enabled only when the current theme defines cardFocusRing.
 */
public final class FocusRing {
    private FocusRing() {
    }

    public static boolean isEnabled(Context context) {
        return getRingResId(context) != 0;
    }

    /**
     * Draws the ring on top of the view. The ring shows when the view is selected or focused.
     */
    public static void apply(View view) {
        if (view == null || Build.VERSION.SDK_INT < 23) {
            return;
        }

        int resId = getRingResId(view.getContext());

        if (resId != 0) {
            Drawable ring = ContextCompat.getDrawable(view.getContext(), resId);
            view.setForeground(ring);
        }
    }

    /**
     * Readable text color for the given background (white on dark, grey on light).
     */
    public static int getTextColor(Context context, int backgroundColor) {
        boolean isDark = ColorUtils.calculateLuminance(backgroundColor) < 0.5;
        return ContextCompat.getColor(context, isDark ? R.color.card_default_text : R.color.card_selected_text_grey);
    }

    private static int getRingResId(Context context) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(R.attr.cardFocusRing, value, true)) {
            return value.resourceId;
        }
        return 0;
    }
}
