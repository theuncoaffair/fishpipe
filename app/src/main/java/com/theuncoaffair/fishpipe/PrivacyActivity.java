package com.theuncoaffair.fishpipe;

import android.content.Intent;
import android.os.Bundle;

import androidx.lifecycle.ViewModelProvider;

import com.theuncoaffair.fishpipe.databinding.ActivityPrivacyBinding;
import com.theuncoaffair.fishpipe.ui.privacy.PrivacyUiState;
import com.theuncoaffair.fishpipe.ui.privacy.PrivacyViewModel;
import com.theuncoaffair.fishpipe.util.BaseActivity;
import com.theuncoaffair.fishpipe.util.Ui;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class PrivacyActivity extends BaseActivity {
    private ActivityPrivacyBinding binding;
    private PrivacyViewModel viewModel;
    private int lastClearedNonce;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPrivacyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(PrivacyViewModel.class);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.btnTerms.setOnClickListener(v -> openLegal(LegalActivity.KIND_TERMS));
        binding.btnPolicy.setOnClickListener(v -> openLegal(LegalActivity.KIND_PRIVACY));
        binding.btnCookies.setOnClickListener(v -> openLegal(LegalActivity.KIND_COOKIES));
        binding.btnClearCache.setOnClickListener(v -> viewModel.clearCache());
        PrivacyUiState initial = viewModel.getState().getValue();
        if (initial != null) {
            lastClearedNonce = initial.clearedNonce;
        }
        viewModel.getState().observe(this, this::render);
    }

    private void render(PrivacyUiState state) {
        binding.btnClearCache.setEnabled(!state.clearing);
        if (state.clearedNonce > lastClearedNonce) {
            lastClearedNonce = state.clearedNonce;
            Ui.toast(this, R.string.privacy_cache_cleared);
        }
    }

    private void openLegal(String kind) {
        Intent intent = new Intent(this, LegalActivity.class);
        intent.putExtra(LegalActivity.EXTRA_KIND, kind);
        startActivity(intent);
    }
}
