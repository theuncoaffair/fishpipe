package com.theuncoaffair.fishpipe.ui.pro;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.data.repository.LicenseRepository;
import com.theuncoaffair.fishpipe.data.repository.SettingsRepository;
import com.theuncoaffair.fishpipe.pricing.AnnualPrice;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public final class ProViewModel extends ViewModel {
    private final LicenseRepository license;
    private final SettingsRepository settings;
    private final MutableLiveData<ProUiState> state = new MutableLiveData<>();

    @Inject
    public ProViewModel(LicenseRepository license, SettingsRepository settings) {
        this.license = license;
        this.settings = settings;
        refresh();
    }

    public LiveData<ProUiState> getState() {
        return state;
    }

    public String paymentUrl() {
        return AnnualPrice.forRegion(settings.getRegionCode()).checkoutUrl();
    }

    public void refresh() {
        state.setValue(new ProUiState(license.isProActive()));
    }
}
