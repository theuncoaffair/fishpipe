package com.theuncoaffair.fishpipe;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;

import androidx.lifecycle.ViewModelProvider;

import com.theuncoaffair.fishpipe.databinding.ActivityLegalBinding;
import com.theuncoaffair.fishpipe.ui.legal.LegalUiState;
import com.theuncoaffair.fishpipe.ui.legal.LegalViewModel;
import com.theuncoaffair.fishpipe.util.BaseActivity;
import com.theuncoaffair.fishpipe.util.Ui;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class LegalActivity extends BaseActivity {
    public static final String EXTRA_KIND = "kind";
    public static final String KIND_TERMS = "terms";
    public static final String KIND_PRIVACY = "privacy";
    public static final String KIND_COOKIES = "cookies";
    public static final String KIND_DISCLAIMER = "disclaimer";

    private ActivityLegalBinding binding;
    private LegalViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLegalBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(LegalViewModel.class);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.legalBody.setMovementMethod(LinkMovementMethod.getInstance());
        viewModel.getState().observe(this, this::render);
        viewModel.load(getIntent().getStringExtra(EXTRA_KIND));
    }

    private void render(LegalUiState state) {
        binding.toolbar.setTitle(state.titleRes);
        if (state.error) {
            Ui.toast(this, R.string.legal_load_failed);
            finish();
            return;
        }
        if (state.loaded) {
            binding.legalBody.setText(state.body);
        }
    }
}
