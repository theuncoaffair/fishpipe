package com.theuncoaffair.fishpipe.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DownloadEntryTest {
    @Test
    public void progressPercentClampsToRange() {
        DownloadEntry entry = new DownloadEntry();
        entry.size = 200;
        entry.bytes = 100;
        assertEquals(50, entry.progressPercent());
        entry.bytes = 400;
        assertEquals(100, entry.progressPercent());
        entry.size = 0;
        entry.bytes = 50;
        assertEquals(0, entry.progressPercent());
    }

    @Test
    public void completedEntryReportsFullProgressWithoutSize() {
        DownloadEntry entry = new DownloadEntry();
        entry.status = DownloadEntry.STATUS_COMPLETED;
        assertEquals(100, entry.progressPercent());
    }

    @Test
    public void statusHelpersMatchDeclaredStates() {
        DownloadEntry entry = new DownloadEntry();
        entry.status = DownloadEntry.STATUS_QUEUED;
        assertTrue(entry.isRunning());
        assertFalse(entry.canRetry());
        entry.status = DownloadEntry.STATUS_PAUSED;
        assertTrue(entry.isPaused());
        assertTrue(entry.canRetry());
        entry.status = DownloadEntry.STATUS_FAILED;
        assertTrue(entry.canRetry());
        entry.status = DownloadEntry.STATUS_COMPLETED;
        assertTrue(entry.isComplete());
    }

    @Test
    public void copyPreservesMetadata() {
        DownloadEntry entry = new DownloadEntry();
        entry.mediaId = "123";
        entry.title = "title";
        entry.type = DownloadEntry.TYPE_IMAGE;
        entry.size = 10;
        entry.bytes = 5;
        DownloadEntry copy = entry.copy();
        assertEquals("123", copy.mediaId);
        assertEquals("title", copy.title);
        assertEquals(DownloadEntry.TYPE_IMAGE, copy.type);
        assertEquals(10, copy.size);
        assertEquals(5, copy.bytes);
    }
}
