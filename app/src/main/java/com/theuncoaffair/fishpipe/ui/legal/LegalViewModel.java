package com.theuncoaffair.fishpipe.ui.legal;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.LegalActivity;
import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.util.Markdown;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class LegalViewModel extends ViewModel {
    private final Context context;
    private final ExecutorService io;
    private final MutableLiveData<LegalUiState> state = new MutableLiveData<>();

    @Inject
    public LegalViewModel(@ApplicationContext Context context, @Named("io") ExecutorService io) {
        this.context = context.getApplicationContext();
        this.io = io;
    }

    public LiveData<LegalUiState> getState() {
        return state;
    }

    public void load(String kind) {
        String resolved = kind == null ? LegalActivity.KIND_TERMS : kind;
        int title = titleFor(resolved);
        state.setValue(new LegalUiState(title, "", false, false));
        io.execute(() -> {
            try {
                String markdown = read("legal/" + resolved + ".md");
                state.postValue(new LegalUiState(title, Markdown.render(markdown), true, false));
            } catch (IOException e) {
                state.postValue(new LegalUiState(title, "", true, true));
            }
        });
    }

    private int titleFor(String kind) {
        if (LegalActivity.KIND_PRIVACY.equals(kind)) return R.string.privacy_policy_title;
        if (LegalActivity.KIND_COOKIES.equals(kind)) return R.string.privacy_cookies_section;
        if (LegalActivity.KIND_DISCLAIMER.equals(kind)) return R.string.disclaimer;
        return R.string.privacy_terms_section;
    }

    private String read(String path) throws IOException {
        InputStream in = context.getAssets().open(path);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            in.close();
        }
    }
}
