package com.theuncoaffair.fishpipe;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.theuncoaffair.fishpipe.billing.LicenseManager;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.databinding.ActivitySplashBinding;
import com.theuncoaffair.fishpipe.region.RegionActivity;
import com.theuncoaffair.fishpipe.util.BaseActivity;
import com.theuncoaffair.fishpipe.util.ThemeManager;
import com.theuncoaffair.fishpipe.util.Ui;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class SplashActivity extends BaseActivity {
    @Inject
    Settings settings;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySplashBinding binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Ui.statusBar(this, ThemeManager.isLight(this));
        LicenseManager.bootstrap(this);
        handler.postDelayed(this::next, 900);
    }

    private void next() {
        if (isFinishing() || isDestroyed()) return;
        Intent intent = new Intent(this,
                settings.getRegionCode().isEmpty() ? RegionActivity.class : MainActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
