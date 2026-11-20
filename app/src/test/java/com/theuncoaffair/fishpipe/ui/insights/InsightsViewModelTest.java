package com.theuncoaffair.fishpipe.ui.insights;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.theuncoaffair.fishpipe.analytics.InsightsResult;

import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class InsightsViewModelTest {
    @Rule
    public final InstantTaskExecutorRule rule = new InstantTaskExecutorRule();

    private final ExecutorService direct = Executors.newSingleThreadExecutor();

    @Test
    public void analyzePublishesSuccess() throws Exception {
        InsightsResult result = new InsightsResult();
        InsightsViewModel viewModel = new InsightsViewModel(direct, (query, mode) -> result);
        viewModel.analyze("query", 0);
        long deadline = System.currentTimeMillis() + 2000;
        while (viewModel.getState().getValue() == null
                || viewModel.getState().getValue().isLoading()) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("timeout");
            Thread.sleep(10);
        }
        InsightsUiState state = viewModel.getState().getValue();
        assertFalse(state.isLoading());
        assertEquals(result, state.getResult());
        assertNull(state.getError());
        direct.shutdownNow();
    }

    @Test
    public void analyzePublishesError() throws Exception {
        InsightsViewModel viewModel = new InsightsViewModel(direct, (query, mode) -> {
            throw new IllegalStateException("boom");
        });
        viewModel.analyze("query", 0);
        long deadline = System.currentTimeMillis() + 2000;
        while (viewModel.getState().getValue() == null
                || viewModel.getState().getValue().isLoading()) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("timeout");
            Thread.sleep(10);
        }
        InsightsUiState state = viewModel.getState().getValue();
        assertFalse(state.isLoading());
        assertEquals("boom", state.getError());
        assertNull(state.getResult());
        direct.shutdownNow();
    }
}
