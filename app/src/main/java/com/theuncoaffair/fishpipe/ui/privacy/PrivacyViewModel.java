package com.theuncoaffair.fishpipe.ui.privacy;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.util.ImageLoader;

import java.io.File;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class PrivacyViewModel extends ViewModel {
    private final Context context;
    private final ExecutorService io;
    private final MutableLiveData<PrivacyUiState> state =
            new MutableLiveData<>(new PrivacyUiState(false, 0));
    private int clearedNonce;

    @Inject
    public PrivacyViewModel(@ApplicationContext Context context, @Named("io") ExecutorService io) {
        this.context = context.getApplicationContext();
        this.io = io;
    }

    public LiveData<PrivacyUiState> getState() {
        return state;
    }

    public void clearCache() {
        PrivacyUiState current = state.getValue();
        if (current != null && current.clearing) return;
        state.setValue(new PrivacyUiState(true, clearedNonce));
        io.execute(() -> {
            ImageLoader.clearCache();
            delete(new File(context.getCacheDir(), "image_cache"));
            delete(context.getCacheDir());
            delete(context.getExternalCacheDir());
            delete(new File(context.getFilesDir(), "download_parts"));
            clearedNonce++;
            state.postValue(new PrivacyUiState(false, clearedNonce));
        });
    }

    private void delete(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                delete(child);
            }
        }
        file.delete();
    }
}
