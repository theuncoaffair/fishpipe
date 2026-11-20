package com.theuncoaffair.fishpipe.ui.language;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.data.repository.SettingsRepository;
import com.theuncoaffair.fishpipe.util.LocaleHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class LanguageViewModel extends ViewModel {
    private static final String[] CODES = {
            Settings.LANG_SYSTEM, Settings.LANG_EN, Settings.LANG_VI
    };

    private final SettingsRepository settings;
    private final Context context;
    private final MutableLiveData<LanguageUiState> state = new MutableLiveData<>();
    private String query = "";

    @Inject
    public LanguageViewModel(SettingsRepository settings, @ApplicationContext Context context) {
        this.settings = settings;
        this.context = context;
        state.setValue(build());
    }

    public LiveData<LanguageUiState> getState() {
        return state;
    }

    public void setQuery(String value) {
        query = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        state.setValue(build());
    }

    public void select(String code) {
        settings.setLanguage(code);
        state.setValue(build());
    }


    private LanguageUiState build() {
        String selected = settings.getLanguage();
        List<LanguageOption> options = new ArrayList<>();
        for (String code : CODES) {
            String label = LocaleHelper.label(context, code);
            if (query.isEmpty()
                    || label.toLowerCase(Locale.ROOT).contains(query)
                    || code.toLowerCase(Locale.ROOT).contains(query)) {
                options.add(new LanguageOption(code, label, code.equals(selected)));
            }
        }
        return new LanguageUiState(options, query, selected);
    }
}
