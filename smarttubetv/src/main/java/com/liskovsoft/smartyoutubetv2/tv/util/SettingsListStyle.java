package com.liskovsoft.smartyoutubetv2.tv.util;

import android.content.Context;
import android.util.TypedValue;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * MOD: YouTube scheme shows the settings page as a list (icon + name per row)
 * instead of a grid of tiles. Enabled by the premiumSettingsList theme attribute.
 */
public final class SettingsListStyle {
    private SettingsListStyle() {
    }

    public static boolean isEnabled(Context context) {
        if (context == null) {
            return false;
        }
        TypedValue value = new TypedValue();
        return context.getTheme().resolveAttribute(R.attr.premiumSettingsList, value, true)
                && value.type == TypedValue.TYPE_INT_BOOLEAN && value.data != 0;
    }
}
