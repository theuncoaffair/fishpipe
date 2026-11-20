package com.theuncoaffair.fishpipe.util;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import com.theuncoaffair.fishpipe.data.Settings;

import java.util.Locale;

public final class LocaleHelper {
    private LocaleHelper() {
    }

    public static Context wrap(Context base) {
        String language = new Settings(base).getLanguage();
        if (Settings.LANG_SYSTEM.equals(language) || language.isEmpty()) {
            return base;
        }
        Locale locale = new Locale(language);
        Locale.setDefault(locale);
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale);
            return base.createConfigurationContext(configuration);
        }
        configuration.locale = locale;
        Resources resources = base.getResources();
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
        return base;
    }

    public static String label(Context context, String code) {
        int id;
        if (Settings.LANG_SYSTEM.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_system;
        else if (Settings.LANG_VI.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_vi;
        else id = com.theuncoaffair.fishpipe.R.string.language_en;
        return context.getString(id);
    }
}
