package com.theuncoaffair.fishpipe.util;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.theuncoaffair.fishpipe.core.AppClock;
import com.theuncoaffair.fishpipe.download.ClipboardWatchService;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public abstract class BaseActivity extends AppCompatActivity {
    @Inject AppClock clock;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(themeRes());
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
    }

    protected void applySystemBarInsets(View root) {
        applySystemBarInsets(root, true);
    }

    protected void applySystemBarInsets(View root, boolean includeBottom) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int bottom = includeBottom ? bars.bottom : view.getPaddingBottom();
            view.setPadding(bars.left, bars.top, bars.right, bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    protected void applyBottomSystemBarInset(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            target.setPadding(target.getPaddingLeft(), target.getPaddingTop(),
                    target.getPaddingRight(), bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ClipboardWatchService.setAppForeground(true);
    }

    @Override
    protected void onPause() {
        ClipboardWatchService.setAppForeground(false);
        super.onPause();
    }

    protected int themeRes() {
        return ThemeManager.themeRes(this);
    }

    protected long now() {
        return clock.nowMillis();
    }
}
