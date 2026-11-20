package com.theuncoaffair.fishpipe.region;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.data.repository.SettingsRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public final class RegionViewModel extends ViewModel {
    private final SettingsRepository settings;
    private final ExecutorService io;
    private final MutableLiveData<RegionUiState> state = new MutableLiveData<>();
    private final List<Region> allRegions = new ArrayList<>();
    private String query = "";
    private boolean loading = true;
    private boolean error;

    @Inject
    public RegionViewModel(SettingsRepository settings, @Named("io") ExecutorService io) {
        this.settings = settings;
        this.io = io;
        state.setValue(build());
        load();
    }

    public LiveData<RegionUiState> getState() {
        return state;
    }

    public void load() {
        loading = true;
        error = false;
        publish();
        io.execute(() -> {
            List<Region> loaded = RegionRepository.load();
            allRegions.clear();
            allRegions.addAll(loaded);
            loading = false;
            error = loaded.isEmpty();
            publish();
        });
    }

    public void setQuery(String value) {
        query = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        publish();
    }

    public void select(Region region) {
        settings.setRegion(region.code, region.name);
        publish();
    }

    private void publish() {
        state.postValue(build());
    }

    private RegionUiState build() {
        String selected = settings.getRegionCode();
        List<Region> filtered = new ArrayList<>();
        for (Region region : allRegions) {
            if (query.isEmpty()
                    || region.name.toLowerCase(Locale.ROOT).contains(query)
                    || region.code.toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(region);
            }
        }
        return new RegionUiState(filtered, query, selected == null ? "" : selected, loading, error);
    }
}
