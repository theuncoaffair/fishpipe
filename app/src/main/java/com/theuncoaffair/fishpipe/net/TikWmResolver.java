package com.theuncoaffair.fishpipe.net;

import com.theuncoaffair.fishpipe.data.MediaItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Public fallback resolver. TikTok's web CDN often returns 403 for video files
 * even when the page exposes a play URL. This resolver obtains fresh CDN URLs
 * that can be downloaded directly.
 */
public final class TikWmResolver {
    private static final String[] ENDPOINTS = {
            "https://www.tikwm.com/api/?url=",
            "https://tikwm.com/api/?url="
    };

    public MediaItem resolve(String input) throws Exception {
        String target = LinkUtils.normalize(input);
        if (target.isEmpty()) target = input == null ? "" : input.trim();
        if (target.isEmpty()) throw new ResolveException("Empty link");

        Exception last = null;
        for (String endpoint : ENDPOINTS) {
            try {
                MediaItem item = fetch(endpoint + Http.encode(target) + "&hd=1", target);
                if (item != null && item.hasContent()) return item;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new ResolveException("Public resolver failed", last);
    }

    private MediaItem fetch(String url, String target) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json,text/plain,*/*");
        headers.put("User-Agent", Http.MOBILE_UA);
        headers.put("Referer", "https://www.tiktok.com/");
        Http.Response response = Http.get(url, headers);
        if (!response.ok()) throw new ResolveException("Resolver returned HTTP " + response.code);

        JSONObject root = new JSONObject(response.body);
        int code = root.optInt("code", -1);
        if (code != 0) {
            String message = root.optString("msg", "Resolver error");
            throw new ResolveException(message);
        }
        JSONObject data = root.optJSONObject("data");
        if (data == null) throw new ResolveException("Resolver returned no data");

        return parse(data, target);
    }

    static MediaItem parse(JSONObject data, String target) throws Exception {
        MediaItem item = new MediaItem();
        item.source = "tiktok";
        item.id = JsonUtils.string(data, "id", "aweme_id", "video_id");
        if (item.id.isEmpty()) item.id = LinkUtils.extractId(target);
        item.title = JsonUtils.string(data, "title", "desc", "description");
        item.coverUrl = JsonUtils.string(data, "origin_cover", "cover", "ai_dynamic_cover");
        item.pageUrl = target;
        item.createTime = MediaItem.seconds(data.optLong("create_time",
                data.optLong("createTime", data.optLong("timestamp", 0))));
        item.durationSeconds = data.optLong("duration", 0);
        item.region = JsonUtils.string(data, "region");
        item.playCount = data.optLong("play_count", data.optLong("playCount", 0));
        item.diggCount = data.optLong("digg_count", data.optLong("diggCount", 0));
        item.commentCount = data.optLong("comment_count", data.optLong("commentCount", 0));
        item.shareCount = data.optLong("share_count", data.optLong("shareCount", 0));
        item.collectCount = data.optLong("collect_count", data.optLong("collectCount", 0));

        JSONObject author = data.optJSONObject("author");
        item.author = JsonUtils.string(author, "unique_id", "uniqueId", "nickname", "name");
        if (item.author.isEmpty()) item.author = JsonUtils.string(data, "author", "author_name", "username");
        item.authorId = JsonUtils.string(author, "id", "uid", "user_id");
        item.authorAvatar = JsonUtils.string(author, "avatar", "avatar_thumb", "avatar_medium");

        String play = JsonUtils.string(data, "play", "video", "video_url");
        String hd = JsonUtils.string(data, "hdplay", "hd", "hd_url");
        String wm = JsonUtils.string(data, "wmplay", "wm", "watermark");
        item.videoUrl = play.isEmpty() ? hd : play;
        item.videoHdUrl = hd.isEmpty() || hd.equals(item.videoUrl) ? "" : hd;
        item.videoWmUrl = wm.isEmpty() || wm.equals(item.videoUrl) ? "" : wm;

        JSONObject music = data.optJSONObject("music_info");
        if (music == null) music = data.optJSONObject("music");
        if (music != null) {
            item.musicUrl = JsonUtils.string(music, "play", "url", "play_url", "playUrl");
            item.musicId = JsonUtils.string(music, "id", "music_id");
            item.musicTitle = JsonUtils.string(music, "title", "name");
            item.musicAuthor = JsonUtils.string(music, "author", "artist", "author_name");
            item.musicCoverUrl = JsonUtils.string(music, "cover", "cover_url", "cover_large");
        } else {
            item.musicUrl = JsonUtils.string(data, "music", "music_url");
        }

        JSONArray images = data.optJSONArray("images");
        if (images != null) {
            for (int i = 0; i < images.length(); i++) {
                Object raw = images.opt(i);
                String imageUrl = "";
                if (raw instanceof JSONObject) {
                    imageUrl = JsonUtils.string((JSONObject) raw, "url", "url_list", "image", "src", "download_url");
                } else if (raw instanceof String) {
                    imageUrl = JsonUtils.clean((String) raw);
                }
                if (imageUrl.startsWith("http") && !item.images.contains(imageUrl)) {
                    item.images.add(imageUrl);
                }
            }
        }

        if (item.coverUrl.isEmpty() && item.hasImages()) item.coverUrl = item.images.get(0);
        if (!item.hasContent()) throw new ResolveException("Resolver returned no media");
        return item;
    }
}
