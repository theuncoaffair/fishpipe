package com.theuncoaffair.fishpipe.region;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.theuncoaffair.fishpipe.MainActivity;
import com.theuncoaffair.fishpipe.databinding.ActivityRegionBinding;
import com.theuncoaffair.fishpipe.util.BaseActivity;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class RegionActivity extends BaseActivity {
    public static final String EXTRA_CHANGE = "change_region";

    private ActivityRegionBinding binding;
    private RegionViewModel viewModel;
    private RegionAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(RegionViewModel.class);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.retry.setOnClickListener(v -> viewModel.load());
        adapter = new RegionAdapter(this::choose);
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

    private void render(RegionUiState state) {
        adapter.setItems(state.regions, state.selectedCode);
        binding.loading.setVisibility(state.loading ? View.VISIBLE : View.GONE);
        binding.error.setVisibility(state.error ? View.VISIBLE : View.GONE);
        binding.empty.setVisibility(state.isEmpty() ? View.VISIBLE : View.GONE);
        binding.list.setVisibility(state.loading || state.error ? View.GONE : View.VISIBLE);
    }

    private void choose(Region region) {
        viewModel.select(region);
        if (getIntent().getBooleanExtra(EXTRA_CHANGE, false)) {
            setResult(RESULT_OK);
            finish();
            return;
        }
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
