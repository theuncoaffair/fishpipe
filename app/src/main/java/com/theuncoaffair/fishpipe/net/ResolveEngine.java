package com.theuncoaffair.fishpipe.net;

import android.content.Context;

import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.di.AppGraph;

import timber.log.Timber;

public final class ResolveEngine {
    private final TikTokResolver tikTok = new TikTokResolver();
    private final DouyinResolver douyin = new DouyinResolver();
    private final TikWmResolver tikWm = new TikWmResolver();
    private final CustomResolver custom;
    private final boolean captionsEnabled;

    public ResolveEngine(Context context) {
        Settings settings = AppGraph.get(context).settings();
        custom = new CustomResolver(settings.getCustomResolver(), settings.getResolverKey());
        captionsEnabled = settings.isCaptionsEnabled();
    }

    public MediaItem resolve(String input) throws Exception {
        String raw = input == null ? "" : input.trim();
        if (raw.isEmpty()) throw new ResolveException("Empty input");
        if (raw.matches("\\d{15,}")) raw = "https://m.tiktok.com/v/" + raw + ".html";
        String url = LinkUtils.normalize(raw);
        if (url.isEmpty()) url = LinkUtils.findUrl(raw);
        if (url.isEmpty()) throw new ResolveException("No supported link found");
        if (LinkUtils.isShort(url)) {
            String expanded = LinkUtils.expanded(url);
            if (!expanded.isEmpty()) url = expanded;
        }

        boolean tikTokHost = LinkUtils.isTikTok(url);
        boolean douyinHost = LinkUtils.isDouyin(url);
        Exception last = null;

        if (custom.isConfigured()) {
            try {
                return finish(custom.resolve(url), url);
            } catch (Exception e) {
                last = e;
            }
        }
        if (douyinHost) {
            try {
                return finish(douyin.resolve(url), url);
            } catch (Exception e) {
                last = e;
            }
        }
        // TikTok's page CDN URLs are often rejected with HTTP 403. The public
        // fallback returns fresh CDN links and is therefore tried first.
        try {
            MediaItem item = tikWm.resolve(url);
            if (item != null && item.hasContent() && tikTokHost && captionsEnabled
                    && !item.hasCaptions()) {
                try {
                    MediaItem page = tikTok.resolve(url);
                    if (page != null && page.hasCaptions()) item.captions = page.captions;
                } catch (Exception ignored) {
                    // The public resolver result is still usable without captions.
                }
            }
            return finish(item, url);
        } catch (Exception e) {
            last = e;
        }
        if (tikTokHost) {
            try {
                return finish(tikTok.resolve(url), url);
            } catch (Exception e) {
                last = e;
            }
        }
        if (douyinHost) {
            try {
                return finish(douyin.resolve(url), url);
            } catch (Exception e) {
                last = e;
            }
        }
        if (!tikTokHost && !douyinHost) {
            try {
                return finish(tikTok.resolve(url), url);
            } catch (Exception e) {
                last = e;
            }
            try {
                return finish(douyin.resolve(url), url);
            } catch (Exception e) {
                last = e;
            }
        } else {
            // The same numeric id often exists on both services.
            try {
                return finish(tikTokHost ? douyin.resolve(url) : tikTok.resolve(url), url);
            } catch (Exception e) {
                last = e;
            }
        }
        Timber.tag("Resolve").w(last, "Resolve failed host=%s", LinkUtils.host(url));
        throw new ResolveException("Could not resolve this link", last);
    }

    private MediaItem finish(MediaItem item, String url) throws ResolveException {
        if (item == null || !item.hasContent()) throw new ResolveException("No media found");
        if (item.pageUrl == null || item.pageUrl.isEmpty()) item.pageUrl = url;
        if (item.id == null) item.id = "";
        item.story = LinkUtils.isStory(url) || LinkUtils.isStory(item.pageUrl);
        item.slideshow = item.hasImages();
        return item;
    }
}
