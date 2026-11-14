package com.liskovsoft.smartyoutubetv2.tv.util;

import android.content.Context;
import android.util.TypedValue;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.SearchOrbView;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * YouTube color scheme: all round top buttons (search, account, language, pip) share one
 * dark grey and turn red only when selected. Other schemes keep their own colors.
 */
public final class TopButtons {
    private TopButtons() {
    }

    @Nullable
    public static SearchOrbView.Colors getColors(Context context) {
        int color = resolveColor(context, R.attr.topButtonColor);
        int focusColor = resolveColor(context, R.attr.topButtonFocusColor);

        if (color == 0 || focusColor == 0) {
            return null;
        }

        return new SearchOrbView.Colors(color, focusColor, ContextCompat.getColor(context, R.color.orb_icon_color));
    }

    private static int resolveColor(Context context, int attr) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true) && value.resourceId != 0) {
            return ContextCompat.getColor(context, value.resourceId);
        }
        return 0;
    }
}
