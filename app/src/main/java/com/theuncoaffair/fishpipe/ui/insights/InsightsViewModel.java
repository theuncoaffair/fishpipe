package com.theuncoaffair.fishpipe.ui.insights;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.analytics.InsightsRepository;
import com.theuncoaffair.fishpipe.analytics.InsightsResult;
import com.theuncoaffair.fishpipe.di.AppModule;

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public final class InsightsViewModel extends ViewModel {
    private final MutableLiveData<InsightsUiState> state =
            new MutableLiveData<>(InsightsUiState.idle());
    private final ExecutorService io;
    private final InsightsLoader loader;
    private int requestId;

    @Inject
    public InsightsViewModel(@Named(AppModule.IO) ExecutorService io) {
        this(io, InsightsRepository::load);
    }

    @VisibleForTesting
    InsightsViewModel(@NonNull ExecutorService io, @NonNull InsightsLoader loader) {
        this.io = io;
        this.loader = loader;
    }

    @NonNull
    public LiveData<InsightsUiState> getState() {
        return state;
    }

    public void analyze(@NonNull String query, int mode) {
        final int id = ++requestId;
        state.setValue(InsightsUiState.loading());
        io.execute(() -> {
            try {
                InsightsResult result = loader.load(query, mode);
                if (id == requestId) state.postValue(InsightsUiState.success(result));
            } catch (Exception e) {
                if (id == requestId) state.postValue(InsightsUiState.error(e.getMessage()));
            }
        });
    }

    @Override
    protected void onCleared() {
        requestId++;
        super.onCleared();
    }
}
