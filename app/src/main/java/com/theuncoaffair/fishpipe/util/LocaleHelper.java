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
        else if (Settings.LANG_ES.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_es;
        else if (Settings.LANG_PT.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_pt;
        else if (Settings.LANG_FR.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_fr;
        else if (Settings.LANG_DE.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_de;
        else if (Settings.LANG_IT.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_it;
        else if (Settings.LANG_NL.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_nl;
        else if (Settings.LANG_PL.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_pl;
        else if (Settings.LANG_RU.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_ru;
        else if (Settings.LANG_TR.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_tr;
        else if (Settings.LANG_AR.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_ar;
        else if (Settings.LANG_HI.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_hi;
        else if (Settings.LANG_BN.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_bn;
        else if (Settings.LANG_UR.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_ur;
        else if (Settings.LANG_FA.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_fa;
        else if (Settings.LANG_ID.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_id;
        else if (Settings.LANG_MS.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_ms;
        else if (Settings.LANG_TH.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_th;
        else if (Settings.LANG_JA.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_ja;
        else if (Settings.LANG_KO.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_ko;
        else if (Settings.LANG_ZH.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_zh;
        else if (Settings.LANG_FIL.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_fil;
        else if (Settings.LANG_SW.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_sw;
        else if (Settings.LANG_AM.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_am;
        else if (Settings.LANG_HA.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_ha;
        else if (Settings.LANG_YO.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_yo;
        else if (Settings.LANG_IG.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_ig;
        else if (Settings.LANG_ZU.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_zu;
        else if (Settings.LANG_AF.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_af;
        else if (Settings.LANG_SO.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_so;
        else if (Settings.LANG_SN.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_sn;
        else if (Settings.LANG_XH.equals(code)) id = com.theuncoaffair.fishpipe.R.string.language_xh;
        else id = com.theuncoaffair.fishpipe.R.string.language_en;
        return context.getString(id);
    }
}
