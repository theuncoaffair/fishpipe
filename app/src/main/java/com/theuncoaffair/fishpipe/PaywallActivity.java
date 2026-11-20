package com.theuncoaffair.fishpipe;

import android.os.Bundle;
import android.view.View;

import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.theuncoaffair.fishpipe.billing.LicenseManager;
import com.theuncoaffair.fishpipe.databinding.ActivityPaywallBinding;
import com.theuncoaffair.fishpipe.ui.paywall.PaywallUiState;
import com.theuncoaffair.fishpipe.ui.paywall.PaywallViewModel;
import com.theuncoaffair.fishpipe.util.BaseActivity;
import com.theuncoaffair.fishpipe.util.Ui;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class PaywallActivity extends BaseActivity {
    public static final String EXTRA_VOLUNTARY = "voluntary";
    public static final String EXTRA_EMAIL = "email";

    private ActivityPaywallBinding binding;
    private PaywallViewModel viewModel;
    private int lastEmailErrorNonce;
    private int lastCodeErrorNonce;
    private int lastSentNonce;
    private int lastToastNonce;
    private int lastFinishNonce;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPaywallBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());
        binding.getRoot().setFilterTouchesWhenObscured(true);
        viewModel = new ViewModelProvider(this).get(PaywallViewModel.class);

        boolean voluntary = getIntent().getBooleanExtra(EXTRA_VOLUNTARY, false);
        String price = viewModel.annualPrice();
        binding.paywallBody.setText(voluntary
                ? getString(R.string.paywall_body_voluntary)
                : getString(R.string.paywall_body, LicenseManager.FREE_LIMIT, price));
        binding.paywallPrice.setText(getString(R.string.paywall_price, price));
        binding.paywallBuy.setText(getString(R.string.paywall_buy, price));

        binding.paywallToolbar.setNavigationOnClickListener(v -> close());
        binding.paywallSendCode.setOnClickListener(v -> {
            Ui.hideKeyboard(this);
            viewModel.sendCode(text(binding.paywallEmail));
        });
        binding.paywallActivate.setOnClickListener(v -> {
            Ui.hideKeyboard(this);
            viewModel.verify(text(binding.paywallEmail), text(binding.paywallCode));
        });
        binding.paywallBuy.setOnClickListener(v ->
                Ui.openUrl(this, viewModel.paymentUrl()));

        PaywallUiState initial = viewModel.getState().getValue();
        if (initial != null) {
            lastEmailErrorNonce = initial.emailErrorNonce;
            lastCodeErrorNonce = initial.codeErrorNonce;
            lastSentNonce = initial.sentNonce;
            lastToastNonce = initial.toastNonce;
            lastFinishNonce = initial.finishNonce;
            binding.paywallDevDismiss.setChecked(initial.devDismiss);
            String linkEmail = getIntent().getStringExtra(EXTRA_EMAIL);
            String email = linkEmail == null ? "" : linkEmail.trim();
            if (email.isEmpty()) email = initial.initialEmail;
            if (!email.isEmpty()) binding.paywallEmail.setText(email);
        }
        viewModel.getState().observe(this, this::render);
    }

    private void render(PaywallUiState state) {
        binding.paywallUses.setText(state.usesText);
        binding.paywallDevice.setText(state.deviceText);
        binding.paywallStatus.setText(state.statusMessage);
        binding.paywallStatus.setVisibility(
                state.statusMessage.trim().isEmpty() ? View.GONE : View.VISIBLE);
        binding.paywallProgress.setVisibility(state.busy ? View.VISIBLE : View.GONE);
        binding.paywallSendCode.setEnabled(state.configured && !state.busy);
        binding.paywallActivate.setEnabled(state.configured && !state.busy);
        binding.paywallEmail.setEnabled(!state.busy);
        binding.paywallCode.setEnabled(!state.busy);
        binding.paywallDevDismiss.setVisibility(state.developer ? View.VISIBLE : View.GONE);

        binding.paywallEmailLayout.setError(state.emailError == 0
                ? null : getString(state.emailError));
        binding.paywallCodeLayout.setError(state.codeError == 0
                ? null : getString(state.codeError));
        if (state.emailError != 0 && state.emailErrorNonce > lastEmailErrorNonce) {
            binding.paywallEmail.requestFocus();
        }
        if (state.codeError != 0 && state.codeErrorNonce > lastCodeErrorNonce) {
            binding.paywallCode.requestFocus();
        }
        lastEmailErrorNonce = state.emailErrorNonce;
        lastCodeErrorNonce = state.codeErrorNonce;

        if (state.sentNonce > lastSentNonce) {
            lastSentNonce = state.sentNonce;
            binding.paywallCode.setText("");
            binding.paywallCode.requestFocus();
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

    private void close() {
        persistDismiss();
        finish();
    }

    private void persistDismiss() {
        PaywallUiState state = viewModel.getState().getValue();
        if (state != null && state.developer && binding.paywallDevDismiss.isChecked()) {
            viewModel.setDismiss(true);
        }
    }

    private static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    @Override
    public void onBackPressed() {
        persistDismiss();
        super.onBackPressed();
    }
}
