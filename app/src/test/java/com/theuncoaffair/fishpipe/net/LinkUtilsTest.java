package com.theuncoaffair.fishpipe.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LinkUtilsTest {
    @Test
    public void normalizeUpgradesPlainHttp() {
        assertEquals("https://www.tiktok.com/video/123456789012345",
                LinkUtils.normalize("http://www.tiktok.com/video/123456789012345"));
    }

    @Test
    public void findUrlTrimsTrailingPunctuation() {
        assertEquals("https://vt.tiktok.com/abc",
                LinkUtils.findUrl("see https://vt.tiktok.com/abc)."));
    }

    @Test
    public void identifiesSupportedHosts() {
        assertTrue(LinkUtils.isTikTok("https://www.tiktok.com/video/1"));
        assertTrue(LinkUtils.isDouyin("https://www.iesdouyin.com/share/video/1"));
        assertFalse(LinkUtils.isSupported("https://example.com/video/1"));
    }

    @Test
    public void extractsVideoId() {
        assertEquals("123456789012345",
                LinkUtils.extractId("https://www.tiktok.com/video/123456789012345"));
        assertEquals("123456789012345", LinkUtils.extractId("123456789012345"));
    }

    @Test
    public void detectsStoryAndShortLinks() {
        assertTrue(LinkUtils.isStory("https://www.tiktok.com/story/1"));
        assertTrue(LinkUtils.isShort("https://vt.tiktok.com/abc"));
        assertFalse(LinkUtils.isShort("https://www.tiktok.com/video/1"));
    }
}
