package com.theuncoaffair.fishpipe.net;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class CustomResolverTest {
    @Test
    public void httpsEndpointIsSecure() {
        CustomResolver resolver = new CustomResolver("https://example.com/resolve", "");
        assertTrue(resolver.isConfigured());
        assertTrue(resolver.isSecure());
    }

    @Test
    public void cleartextEndpointIsRejectedBeforeNetwork() {
        CustomResolver resolver = new CustomResolver("http://example.com/resolve", "");
        assertTrue(resolver.isConfigured());
        assertFalse(resolver.isSecure());
        try {
            resolver.resolve("https://www.tiktok.com/@example/video/123456789012345");
            fail("Expected cleartext resolver rejection");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("HTTPS"));
        }
    }
}
