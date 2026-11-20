package com.theuncoaffair.fishpipe;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.lifecycle.ViewModelProvider;

import com.theuncoaffair.fishpipe.data.repository.SettingsRepository;
import com.theuncoaffair.fishpipe.databinding.ActivityProBinding;
import com.theuncoaffair.fishpipe.pricing.AnnualPrice;
import com.theuncoaffair.fishpipe.ui.pro.ProUiState;
import com.theuncoaffair.fishpipe.ui.pro.ProViewModel;
import com.theuncoaffair.fishpipe.util.BaseActivity;
import com.theuncoaffair.fishpipe.util.Ui;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class ProActivity extends BaseActivity {
    private ActivityProBinding binding;
    private ProViewModel viewModel;
    @Inject SettingsRepository settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());
        binding.getRoot().setFilterTouchesWhenObscured(true);
        viewModel = new ViewModelProvider(this).get(ProViewModel.class);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.btnBuyPro.setOnClickListener(v -> Ui.openUrl(this, viewModel.paymentUrl()));
        binding.btnCopyEmail.setOnClickListener(v ->
                Ui.copy(this, viewModel.paymentUrl(), R.string.pro_copied_email));
        binding.btnActivateCode.setOnClickListener(v -> {
            Intent intent = new Intent(this, PaywallActivity.class);
            intent.putExtra(PaywallActivity.EXTRA_VOLUNTARY, true);
            startActivity(intent);
        });
        viewModel.getState().observe(this, this::render);
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.refresh();
    }

    private void render(ProUiState state) {
        AnnualPrice price = AnnualPrice.forRegion(settings.getRegionCode());
        binding.proPrice.setText(price.display());
        binding.proFreeNote.setText(getString(R.string.pro_free_note, price.display(), price.currencyCode()));
        binding.proRegionNote.setText(getString(R.string.pro_region_note, price.display(), price.currencyCode()));
        binding.btnBuyPro.setText(getString(R.string.pro_email_buy, price.display()));
        int visibility = state.proActive ? View.GONE : View.VISIBLE;
        binding.btnBuyPro.setVisibility(visibility);
        binding.btnCopyEmail.setVisibility(visibility);
    }
}
