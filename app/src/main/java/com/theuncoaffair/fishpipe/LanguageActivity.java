package com.theuncoaffair.fishpipe;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.theuncoaffair.fishpipe.databinding.ActivityLanguageBinding;
import com.theuncoaffair.fishpipe.ui.language.LanguageAdapter;
import com.theuncoaffair.fishpipe.ui.language.LanguageOption;
import com.theuncoaffair.fishpipe.ui.language.LanguageUiState;
import com.theuncoaffair.fishpipe.ui.language.LanguageViewModel;
import com.theuncoaffair.fishpipe.util.BaseActivity;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class LanguageActivity extends BaseActivity {
    private ActivityLanguageBinding binding;
    private LanguageViewModel viewModel;
    private LanguageAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLanguageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(LanguageViewModel.class);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        adapter = new LanguageAdapter(this::select);
        binding.list.setLayoutManager(new LinearLayoutManager(this));
        binding.list.setAdapter(adapter);
        binding.list.setHasFixedSize(true);
        binding.search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setQuery(s == null ? "" : s.toString());
            }
        });
        viewModel.getState().observe(this, this::render);
    }

    private void render(LanguageUiState state) {
        adapter.setItems(state.options);
        binding.empty.setVisibility(state.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void select(LanguageOption option) {
        viewModel.select(option.code);
        recreate();
        finish();
    }
}
