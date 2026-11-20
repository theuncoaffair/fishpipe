package com.theuncoaffair.fishpipe;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.util.Diagnostics;
import com.theuncoaffair.fishpipe.util.Notifications;
import com.theuncoaffair.fishpipe.util.ThemeManager;

import dagger.hilt.android.HiltAndroidApp;
import timber.log.Timber;

@HiltAndroidApp
public final class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Diagnostics.install(this);
        Diagnostics.plant();
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
        Settings settings = new Settings(this);
        ThemeManager.applyTheme(settings.getTheme());
        Notifications.ensureChannel(this);
    }
}
