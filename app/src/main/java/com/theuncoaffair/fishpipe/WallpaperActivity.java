package com.theuncoaffair.fishpipe;

import android.os.Bundle;

import androidx.lifecycle.ViewModelProvider;

import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.databinding.ActivityWallpaperBinding;
import com.theuncoaffair.fishpipe.ui.wallpaper.WallpaperUiState;
import com.theuncoaffair.fishpipe.ui.wallpaper.WallpaperViewModel;
import com.theuncoaffair.fishpipe.util.BaseActivity;
import com.theuncoaffair.fishpipe.util.Ui;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class WallpaperActivity extends BaseActivity {
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_TYPE = "type";

    private ActivityWallpaperBinding binding;
    private WallpaperViewModel viewModel;
    private int lastToastNonce;
    private int lastFinishNonce;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWallpaperBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(WallpaperViewModel.class);

        binding.wallpaperToolbar.setNavigationOnClickListener(v -> finish());
        binding.wallpaperSet.setOnClickListener(v -> viewModel.apply());
        binding.wallpaperSeek.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                binding.wallpaperFrameValue.setText(
                        getString(R.string.frame_percent, (int) value));
            }
        });
        binding.wallpaperSeek.addOnSliderTouchListener(
                new com.google.android.material.slider.Slider.OnSliderTouchListener() {
                    @Override
                    public void onStartTrackingTouch(
                            com.google.android.material.slider.Slider slider) {
                    }

                    @Override
                    public void onStopTrackingTouch(
                            com.google.android.material.slider.Slider slider) {
                        viewModel.loadFrame((int) slider.getValue());
                    }
                });

        WallpaperUiState initial = viewModel.getState().getValue();
        if (initial != null) {
            lastToastNonce = initial.toastNonce;
            lastFinishNonce = initial.finishNonce;
        }
        viewModel.getState().observe(this, this::render);

        String uri = getIntent().getStringExtra(EXTRA_URI);
        boolean video = DownloadEntry.TYPE_VIDEO.equals(
                getIntent().getStringExtra(EXTRA_TYPE));
        viewModel.initialize(uri, video);
    }

    private void render(WallpaperUiState state) {
        boolean video = state.video;
        binding.wallpaperSeek.setVisibility(video ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.wallpaperFrameLabel.setVisibility(video ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.wallpaperFrameValue.setVisibility(video ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.wallpaperProgress.setVisibility(state.busy ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.wallpaperSet.setEnabled(state.setEnabled);
        binding.wallpaperSeek.setEnabled(state.seekEnabled);
        binding.wallpaperFrameValue.setText(
                getString(R.string.frame_percent, state.framePercent));
        if (video && binding.wallpaperSeek.getValue() != state.framePercent) {
            binding.wallpaperSeek.setValue(state.framePercent);
        }
        if (state.preview != null) {
            binding.wallpaperPreview.setImageBitmap(state.preview);
        }

        if (state.toastNonce > lastToastNonce) {
            lastToastNonce = state.toastNonce;
            if (state.toastRes != 0) Ui.toast(this, state.toastRes);
        }
        if (state.finishNonce > lastFinishNonce) {
            lastFinishNonce = state.finishNonce;
            finish();
        }
    }
}
