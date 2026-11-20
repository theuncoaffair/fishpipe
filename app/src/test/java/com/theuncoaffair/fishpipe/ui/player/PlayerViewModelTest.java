package com.theuncoaffair.fishpipe.ui.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.data.MediaItem;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PlayerViewModelTest {
    @Rule
    public final InstantTaskExecutorRule rule = new InstantTaskExecutorRule();

    private final ExecutorService direct = Executors.newSingleThreadExecutor();
    private PlayerViewModel viewModel;
    private MediaItem item;

    @Before
    public void setUp() {
        Context context = mock(Context.class);
        when(context.getString(R.string.app_name)).thenReturn("FishPipe");
        item = new MediaItem();
        item.title = "Test title";
        item.author = "Test author";
        item.pageUrl = "https://example.com/video/1";
        item.createTime = 1700000000L;
        viewModel = new PlayerViewModel(context, direct);
        viewModel.initialize(new PlayerArgs(
                "https://cdn.example.com/video.mp4",
                item.title,
                "",
                item,
                false,
                PlayerViewModel.REPEAT_OFF));
    }

    @After
    public void tearDown() {
        direct.shutdownNow();
    }

    @Test
    public void initializePublishesMetadata() {
        PlayerUiState state = viewModel.getState().getValue();
        assertEquals("FishPipe", state.title);
        assertEquals("Test author", state.author);
        assertEquals(item.pageUrl, state.shareUrl);
        assertEquals(PlayerViewModel.REPEAT_OFF, state.repeatMode);
        assertEquals(1f, state.speed, 0.001f);
        assertEquals("1x", state.speedLabel);
        assertNull(viewModel.getLocalEntry());
        assertSame(item, viewModel.downloadItem());
    }

    @Test
    public void speedRepeatMuteAndCaptionsPublishState() {
        viewModel.cycleSpeed();
        PlayerUiState speed = viewModel.getState().getValue();
        assertEquals(1.25f, speed.speed, 0.001f);
        assertEquals("1.25x", speed.speedLabel);

        viewModel.cycleRepeat();
        PlayerUiState repeatAll = viewModel.getState().getValue();
        assertEquals(PlayerViewModel.REPEAT_ALL, repeatAll.repeatMode);
        assertEquals(R.string.repeat_all, repeatAll.toastRes);
        assertEquals(1, repeatAll.toastNonce);

        viewModel.toggleMute();
        assertTrue(viewModel.isMuted());
        assertTrue(viewModel.getState().getValue().muted);

        viewModel.toggleCaptions();
        assertFalse(viewModel.isCaptionsVisible());
        assertFalse(viewModel.getState().getValue().captionsVisible);
        assertEquals("", viewModel.captionFor(0));
    }

    @Test
    public void navigationWithoutQueueEmitsToast() {
        assertFalse(viewModel.openNext(false));
        viewModel.next();
        PlayerUiState next = viewModel.getState().getValue();
        assertEquals(R.string.no_next_media, next.toastRes);
        assertEquals(1, next.toastNonce);

        viewModel.previous();
        PlayerUiState previous = viewModel.getState().getValue();
        assertEquals(R.string.no_previous_media, previous.toastRes);
        assertEquals(2, previous.toastNonce);
    }
}
