package com.theuncoaffair.fishpipe.net;

import com.theuncoaffair.fishpipe.data.MediaItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TikTokResolver {
    public MediaItem resolve(String input) throws Exception {
        String url = LinkUtils.expanded(input);
        if (url.isEmpty()) url = LinkUtils.normalize(input);
        String id = LinkUtils.extractId(url);
        if (url.isEmpty()) throw new ResolveException("Empty link");

        String page = LinkUtils.tiktokPage(url, id);
        MediaItem item = fromPage(page, id);
        if (item != null && item.hasContent()) return item;

        if (!id.isEmpty()) {
            String mobile = "https://m.tiktok.com/v/" + id + ".html";
            if (!mobile.equals(page)) {
                item = fromPage(mobile, id);
                if (item != null && item.hasContent()) return item;
            }
        }

        // Try the oEmbed endpoint for metadata; it does not always expose a video
        // URL, but it makes the result useful when paired with a custom resolver.
        if (!id.isEmpty() || !url.isEmpty()) {
            MediaItem meta = fromOembed(url, id);
            if (meta != null) {
                if (!meta.hasVideo()) {
                    throw new ResolveException("No video URL found");
                }
                return meta;
            }
        }
        throw new ResolveException("No video found on the TikTok page");
    }

    private MediaItem fromPage(String pageUrl, String id) {
        if (pageUrl == null || pageUrl.isEmpty()) return null;
        try {
            Map<String, String> headers = new HashMap<>();
            boolean mobile = LinkUtils.host(pageUrl).startsWith("m.");
            headers.put("User-Agent", mobile ? Http.MOBILE_UA : Http.DESKTOP_UA);
            headers.put("Referer", "https://www.tiktok.com/");
            headers.put("Cookie", "tt_webid_v2=" + LinkUtils.randomWid());
            Http.Response response = Http.get(pageUrl, headers);
            if (!response.ok()) return null;
            MediaItem item = parseHtml(response.body, id);
            if (item != null) item.pageUrl = response.finalUrl.isEmpty() ? pageUrl : response.finalUrl;
            return item;
        } catch (Exception ignored) {
            return null;
        }
    }

    private MediaItem parseHtml(String html, String id) {
        List<String> scripts = JsonUtils.scriptJson(html, id);
        for (String script : scripts) {
            try {
                JSONObject root = new JSONObject(script);
                JSONObject object = JsonUtils.findVideoObject(root, id);
                if (object != null) {
                    MediaItem item = parseObject(object, id);
                    if (item.hasContent()) return item;
                }
                JSONObject photo = JsonUtils.findImageObject(root);
                if (photo != null) {
                    MediaItem item = parseObject(photo, id);
                    if (item.hasContent()) return item;
                }
            } catch (Exception ignored) {
            }
        }
        return parseRegex(html, id);
    }

    private MediaItem parseObject(JSONObject object, String preferredId) {
        MediaItem item = new MediaItem();
        item.source = "tiktok";
        item.id = JsonUtils.string(object, "id", "aweme_id", "awemeId");
        if (item.id.isEmpty()) item.id = preferredId;
        item.title = JsonUtils.string(object, "desc", "title", "text");
        item.createTime = MediaItem.seconds(object.optLong("createTime",
                object.optLong("create_time", object.optLong("ctime", 0))));
        JSONObject video = object.optJSONObject("video");
        if (video != null) {
            String play = JsonUtils.string(video, "playAddr", "play_addr", "playAddrH265", "play_addr_h265");
            String download = JsonUtils.string(video, "downloadAddr", "download_addr");
            item.videoUrl = play.isEmpty() ? download : play;
            item.videoWmUrl = download.isEmpty() || download.equals(item.videoUrl) ? "" : download;
            String hd = JsonUtils.bestBitrate(video);
            item.videoHdUrl = hd.isEmpty() || hd.equals(item.videoUrl) ? "" : hd;
            item.coverUrl = JsonUtils.string(video, "originCover", "origin_cover", "cover", "dynamicCover", "dynamic_cover");
            addCaptions(item, video);
            addCaptions(item, object);
        }
        JSONObject author = object.optJSONObject("author");
        if (author != null) {
            item.author = JsonUtils.string(author, "uniqueId", "unique_id", "nickname", "name");
        }
        if (item.author.isEmpty()) item.author = JsonUtils.string(object, "author", "nickname", "uniqueId");

        JSONObject music = object.optJSONObject("music");
        if (music != null) {
            item.musicUrl = JsonUtils.string(music, "playUrl", "play_url", "musicUrl", "music_url");
            item.musicTitle = JsonUtils.string(music, "title", "name");
            item.musicAuthor = JsonUtils.string(music, "authorName", "author_name", "author");
            item.musicCoverUrl = JsonUtils.string(music, "coverLarge", "cover_large", "coverMedium", "cover_medium", "coverThumb", "cover_thumb");
        }
        JSONObject post = object.optJSONObject("imagePost");
        if (post == null) post = object.optJSONObject("image_post");
        org.json.JSONArray images = post == null ? null : post.optJSONArray("images");
        if (images == null) images = object.optJSONArray("images");
        if (images != null) {
            for (int i = 0; i < images.length(); i++) {
                Object raw = images.opt(i);
                String url = "";
                if (raw instanceof JSONObject) {
                    JSONObject image = (JSONObject) raw;
                    url = JsonUtils.string(image, "displayImage", "display_image", "imageURL", "image_url", "url", "url_list");
                } else if (raw instanceof String) {
                    url = JsonUtils.clean((String) raw);
                }
                if (url.startsWith("http") && !item.images.contains(url)) item.images.add(url);
            }
        }
        if (!item.hasVideo() && item.hasImages()) {
            item.coverUrl = item.coverUrl.isEmpty() ? item.images.get(0) : item.coverUrl;
        }
        return item;
    }

    private static void addCaptions(MediaItem item, JSONObject source) {
        if (source == null) return;
        collectCaptionArray(item, source.optJSONArray("captionInfos"));
        collectCaptionArray(item, source.optJSONArray("caption_infos"));
        collectCaptionArray(item, source.optJSONArray("subtitleInfos"));
        collectCaptionArray(item, source.optJSONArray("subtitle_infos"));
        JSONObject cla = source.optJSONObject("claInfo");
        if (cla == null) cla = source.optJSONObject("cla_info");
        if (cla != null) {
            collectCaptionArray(item, cla.optJSONArray("captionInfos"));
            collectCaptionArray(item, cla.optJSONArray("caption_infos"));
            collectCaptionArray(item, cla.optJSONArray("subtitleInfos"));
            collectCaptionArray(item, cla.optJSONArray("subtitle_infos"));
        }
    }

    private static void collectCaptionArray(MediaItem item, JSONArray array) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) continue;
            String url = JsonUtils.string(object, "url", "Url", "urlList", "url_list",
                    "downloadUrl", "download_url", "captionUrl", "caption_url");
            if (url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) continue;
            boolean duplicate = false;
            for (MediaItem.CaptionTrack existing : item.captions) {
                if (url.equals(existing.url)) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) continue;
            MediaItem.CaptionTrack track = new MediaItem.CaptionTrack();
            track.url = url;
            track.format = JsonUtils.string(object, "format", "Format", "fileFormat",
                    "FileFormat", "file_format", "ext", "Ext");
            track.languageCode = JsonUtils.string(object, "lang", "Lang", "languageCode",
                    "LanguageCode", "language_code", "langCode", "lang_code");
            track.language = JsonUtils.string(object, "languageCodeName", "LanguageCodeName",
                    "languageName", "LanguageName", "langName", "LangName", "language",
                    "Language", "name", "Name");
            if (track.language.isEmpty()) track.language = track.languageCode;
            track.name = JsonUtils.string(object, "name", "Name", "captionName", "CaptionName",
                    "caption_name");
            track.autoGenerated = object.optBoolean("isAutoGen", object.optBoolean("IsAutoGen",
                    object.optBoolean("isAutoGenerated", object.optBoolean("IsAutoGenerated",
                            object.optBoolean("is_auto_generated", object.optBoolean("autoGenerated",
                                    object.optBoolean("AutoGenerated", object.optBoolean("isAuto",
                                            object.optBoolean("IsAuto", false)))))))));
            item.captions.add(track);
        }
    }

    private MediaItem parseRegex(String html, String id) {
        String play = JsonUtils.regexField(html, "playAddr");
        String download = JsonUtils.regexField(html, "downloadAddr");
        if (play.isEmpty() && download.isEmpty()) return null;
        MediaItem item = new MediaItem();
        item.source = "tiktok";
        item.id = id.isEmpty() ? JsonUtils.regexField(html, "id") : id;
        item.title = JsonUtils.regexField(html, "desc");
        if (item.title.isEmpty()) item.title = JsonUtils.regexField(html, "title");
        item.author = JsonUtils.regexField(html, "uniqueId");
        if (item.author.isEmpty()) item.author = JsonUtils.regexField(html, "nickname");
        String created = JsonUtils.regexField(html, "createTime");
        if (!created.isEmpty()) {
            try {
                item.createTime = MediaItem.seconds(Long.parseLong(created.replaceAll("[^0-9]", "")));
            } catch (Exception ignored) {
            }
        }
        item.videoUrl = play.isEmpty() ? download : play;
        item.videoWmUrl = download.isEmpty() || download.equals(item.videoUrl) ? "" : download;
        item.coverUrl = JsonUtils.regexField(html, "originCover");
        if (item.coverUrl.isEmpty()) item.coverUrl = JsonUtils.regexField(html, "cover");
        return item;
    }

    private MediaItem fromOembed(String url, String id) {
        try {
            String target = url;
            if (target.isEmpty() && !id.isEmpty()) target = "https://www.tiktok.com/@i/video/" + id;
            String endpoint = "https://www.tiktok.com/oembed?url=" + URLEncoder.encode(target, "UTF-8");
            Http.Response response = Http.get(endpoint, null);
            if (!response.ok()) return null;
            JSONObject json = new JSONObject(response.body);
            MediaItem item = new MediaItem();
            item.source = "tiktok";
            item.id = id;
            item.title = JsonUtils.string(json, "title");
            item.author = JsonUtils.string(json, "author_name", "author_unique_id");
            item.coverUrl = JsonUtils.string(json, "thumbnail_url");
            item.pageUrl = target;
            return item;
        } catch (Exception ignored) {
            return null;
        }
    }
}
