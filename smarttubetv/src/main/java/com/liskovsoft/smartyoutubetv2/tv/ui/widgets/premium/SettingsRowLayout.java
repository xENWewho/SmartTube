package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.premium;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * MOD: settings row that sets its own text colors when it's selected, so the text is
 * always readable on the white bar (YouTube scheme). Other schemes keep their colors.
 */
public class SettingsRowLayout extends LinearLayout {
    private static final int[] FOCUSED = {android.R.attr.state_focused};

    public SettingsRowLayout(Context context) {
        super(context);
    }

    public SettingsRowLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SettingsRowLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        applyTextColors(gainFocus);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyTextColors(hasFocus());
    }

    private void applyTextColors(boolean focused) {
        if (!isYouTubeStyle()) {
            return; // other color schemes keep SmartTube's own colors
        }
        apply(findViewById(android.R.id.title), R.attr.settingsRowTitleColor, focused);
        apply(findViewById(android.R.id.summary), R.attr.settingsRowSummaryColor, focused);
    }

    private void apply(@Nullable TextView textView, int attr, boolean focused) {
        if (textView == null) {
            return;
        }

        // Keep the original colors (incl. disabled state) to put back when not focused
        Object saved = textView.getTag(R.id.settings_row_original_colors);
        if (!(saved instanceof ColorStateList)) {
            saved = textView.getTextColors();
            textView.setTag(R.id.settings_row_original_colors, saved);
        }

        // Keep the original typeface too, so bold is only used while selected
        Object savedTypeface = textView.getTag(R.id.settings_row_original_typeface);
        if (savedTypeface == null) {
            savedTypeface = textView.getTypeface() != null ? textView.getTypeface() : Typeface.DEFAULT;
            textView.setTag(R.id.settings_row_original_typeface, savedTypeface);
        }

        if (!focused || !textView.isEnabled()) {
            textView.setTextColor((ColorStateList) saved);
            textView.setTypeface((Typeface) savedTypeface);
            return;
        }

        if (textView.getId() == android.R.id.title) {
            textView.setTypeface(Typeface.create((Typeface) savedTypeface, Typeface.BOLD));
        }

        ColorStateList colors = resolveColors(attr);
        if (colors != null) {
            textView.setTextColor(colors.getColorForState(FOCUSED, colors.getDefaultColor()));
        }
    }

    private boolean isYouTubeStyle() {
        TypedValue value = new TypedValue();
        return getContext().getTheme().resolveAttribute(R.attr.settingsRowBackground, value, true)
                && value.resourceId == R.drawable.settings_row_background_youtube;
    }

    @Nullable
    private ColorStateList resolveColors(int attr) {
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, value, true) && value.resourceId != 0) {
            return ContextCompat.getColorStateList(getContext(), value.resourceId);
        }
        return null;
    }
}
