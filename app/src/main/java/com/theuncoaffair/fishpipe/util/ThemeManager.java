package com.theuncoaffair.fishpipe.util;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.util.TypedValue;

import androidx.appcompat.app.AppCompatDelegate;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.data.Settings;

public final class ThemeManager {
    private ThemeManager() {
    }

    public static void applyTheme(String mode) {
        AppCompatDelegate.setDefaultNightMode(nightMode(mode));
    }

    public static int nightMode(String mode) {
        if (Settings.THEME_LIGHT.equals(mode)) return AppCompatDelegate.MODE_NIGHT_NO;
        if (Settings.THEME_DARK.equals(mode) || Settings.THEME_AMOLED.equals(mode)) {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    public static int themeRes(Context context) {
        String mode = new Settings(context).getTheme();
        if (Settings.THEME_LIGHT.equals(mode)) return R.style.Theme_FishPipe_Light;
        if (Settings.THEME_AMOLED.equals(mode)) return R.style.Theme_FishPipe_Amoled;
        return R.style.Theme_FishPipe;
    }

    public static int transparentThemeRes(Context context) {
        return R.style.Theme_FishPipe_Transparent;
    }

    public static int playerThemeRes(Context context) {
        return R.style.Theme_FishPipe_Player;
    }

    public static boolean isLight(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
    }

    public static String themeLabel(Context context, String mode) {
        if (Settings.THEME_LIGHT.equals(mode)) return context.getString(R.string.theme_light);
        if (Settings.THEME_DARK.equals(mode)) return context.getString(R.string.theme_dark);
        if (Settings.THEME_AMOLED.equals(mode)) return context.getString(R.string.theme_amoled);
        return context.getString(R.string.theme_system);
    }

    public static int attrColor(Context context, int attr) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)) {
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
            if (value.resourceId != 0) {
                return context.getColor(value.resourceId);
            }
        }
        return Color.TRANSPARENT;
    }
}
