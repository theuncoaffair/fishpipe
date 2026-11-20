package com.theuncoaffair.fishpipe.analytics;

import com.theuncoaffair.fishpipe.analytics.InsightsResult.SampleVideo;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.net.Http;
import com.theuncoaffair.fishpipe.net.JsonUtils;
import com.theuncoaffair.fishpipe.net.TikWmResolver;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InsightsRepository {
    public static final int MODE_AUTO = 0;
    public static final int MODE_VIDEO = 1;
    public static final int MODE_CREATOR = 2;
    public static final int MODE_SOUND = 3;
    public static final int MODE_HASHTAG = 4;

    private static final String SEARCH_API = "https://www.tikwm.com/api/feed/search";
    private static final String FEED_API = "https://www.tikwm.com/api/feed/list";
    private static final Pattern TAG = Pattern.compile("#([\\p{L}\\p{N}_]+)");
    private static final Pattern SOUND_ID = Pattern.compile("/music/[^?#]*-([0-9]{5,})");
    private static final Pattern UNIVERSAL = Pattern.compile(
            "(?is)<script[^>]*id=[\"']__UNIVERSAL_DATA_FOR_REHYDRATION__[\"'][^>]*>(.*?)</script>");

    private InsightsRepository() {
    }

    public static InsightsResult load(String input, int mode) throws Exception {
        String raw = input == null ? "" : input.trim();
        if (raw.isEmpty()) throw new Exception("Empty input");
        int resolvedMode = mode == MODE_AUTO ? detectMode(raw) : mode;
        InsightsResult result;
        if (resolvedMode == MODE_VIDEO) result = loadVideo(raw);
        else if (resolvedMode == MODE_CREATOR) result = loadCreator(raw);
        else if (resolvedMode == MODE_HASHTAG) result = loadHashtag(raw);
        else result = loadSound(raw);
        result.mode = resolvedMode;
        return result;
    }

    public static int detectMode(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("#")) return MODE_HASHTAG;
        if (value.startsWith("@")) return MODE_CREATOR;
        String lower = value.toLowerCase(Locale.US);
        if (lower.contains("tiktok.com")) {
            if (lower.contains("/video/") || lower.contains("/v/") || lower.contains("item_id")) {
                return MODE_VIDEO;
            }
            if (lower.contains("/music/")) return MODE_SOUND;
            if (lower.contains("/tag/")) return MODE_HASHTAG;
            if (lower.contains("/@")) return MODE_CREATOR;
            return MODE_VIDEO;
        }
        if (value.matches("\\d{15,}")) return MODE_VIDEO;
        return MODE_SOUND;
    }

    private static InsightsResult loadVideo(String raw) throws Exception {
        MediaItem video = new TikWmResolver().resolve(raw);
        InsightsResult result = new InsightsResult();
        result.video = video;
        populateVideoMetrics(result, video);

        String author = video.author == null ? "" : video.author.trim();
        if (!author.isEmpty()) {
            try {
                result.creator = fetchProfile(author);
            } catch (Exception e) {
                result.warning = e.getMessage() == null ? "" : e.getMessage();
            }
            List<SampleVideo> samples = new ArrayList<>();
            try {
                samples = creatorSamples(author);
            } catch (Exception e) {
                if (result.warning.isEmpty()) {
                    result.warning = e.getMessage() == null ? "" : e.getMessage();
                }
            }
            aggregate(result, samples, sampleFrom(video));
        }
        return result;
    }

    private static InsightsResult loadCreator(String raw) throws Exception {
        String username = extractUsername(raw);
        if (username.isEmpty()) throw new Exception("No creator username found");
        InsightsResult result = new InsightsResult();
        try {
            result.creator = fetchProfile(username);
        } catch (Exception profileError) {
            result.warning = profileError.getMessage() == null ? "" : profileError.getMessage();
        }
        List<SampleVideo> samples;
        try {
            samples = creatorSamples(username);
        } catch (Exception sampleError) {
            if (result.creator == null) throw sampleError;
            samples = new ArrayList<>();
        }
        if (result.creator == null) {
            if (samples.isEmpty()) throw new Exception("Could not load this creator");
            result.creator = profileFromSample(samples.get(0), username);
        }
        aggregate(result, samples, null);
        return result;
    }

    private static InsightsResult loadSound(String raw) throws Exception {
        String query = soundQuery(raw);
        if (query.isEmpty()) throw new Exception("No sound found");
        InsightsResult result = new InsightsResult();
        List<SampleVideo> samples = searchSamples(query, true);
        List<SampleVideo> matches = new ArrayList<>();
        String soundId = extractSoundId(raw);
        for (SampleVideo sample : samples) {
            boolean same = !soundId.isEmpty()
                    ? soundId.equals(sample.musicId)
                    : (!sample.musicTitle.isEmpty()
                    && sample.musicTitle.toLowerCase(Locale.US).contains(query.toLowerCase(Locale.US)));
            if (same) matches.add(sample);
        }
        if (matches.isEmpty()) throw new Exception("No public sample found for this sound");
        aggregate(result, matches, null);
        InsightsResult.SoundStat sound = new InsightsResult.SoundStat();
        sound.id = soundId;
        sound.title = query;
        sound.pageUrl = soundPage(soundId, query);
        sound.sampleUses = matches.size();
        sound.totalViews = totalViews(matches);
        sound.avgEngagement = averageEngagement(matches);
        for (SampleVideo sample : matches) {
            if (!sample.musicTitle.isEmpty()) {
                sound.title = sample.musicTitle;
                sound.author = sample.musicAuthor;
                if (sound.id.isEmpty()) sound.id = sample.musicId;
                break;
            }
        }
        result.sound = sound;
        return result;
    }

    private static InsightsResult loadHashtag(String raw) throws Exception {
        String tag = extractTag(raw);
        if (tag.isEmpty()) throw new Exception("No hashtag found");
        InsightsResult result = new InsightsResult();
        InsightsResult.TagStat official = fetchChallenge(tag);
        List<SampleVideo> samples = searchSamples(tag, true);
        List<SampleVideo> matches = new ArrayList<>();
        String needle = "#" + tag.toLowerCase(Locale.US);
        for (SampleVideo sample : samples) {
            if (sample.description.toLowerCase(Locale.US).contains(needle)) matches.add(sample);
        }
        if (matches.isEmpty()) matches = samples;
        if (matches.isEmpty() && official == null) {
            throw new Exception("No public sample found for this hashtag");
        }
        if (!matches.isEmpty()) aggregate(result, matches, null);
        InsightsResult.TagStat current = findTag(result.hashtags, tag);
        if (official == null) {
            official = new InsightsResult.TagStat();
            official.tag = tag;
            official.uses = matches.size();
            official.views = totalViews(matches);
        } else if (official.uses <= 0) {
            official.uses = matches.size();
        }
        if (current == null) {
            result.hashtags.add(0, official);
        } else {
            current.uses = official.uses;
            current.views = official.views;
        }
        if (matches.isEmpty()) {
            result.warning = "Hashtag totals loaded without sample videos.";
        }
        return result;
    }

    private static void populateVideoMetrics(InsightsResult result, MediaItem video) {
        result.views = video.playCount;
        result.likes = video.diggCount;
        result.comments = video.commentCount;
        result.shares = video.shareCount;
        result.saves = video.collectCount;
        result.engagementRate = percent(result.likes + result.comments + result.shares, result.views);
        result.likeRate = percent(result.likes, result.views);
        result.saveRate = percent(result.saves, result.views);
        result.shareRate = percent(result.shares, result.views);
        result.commentRate = percent(result.comments, result.views);
    }

    private static void aggregate(InsightsResult result, List<SampleVideo> samples,
                                  SampleVideo current) {
        List<SampleVideo> list = samples == null ? new ArrayList<>() : samples;
        result.sampleVideos = list.size();
        long sumViews = 0;
        long sumLikes = 0;
        long sumComments = 0;
        long sumShares = 0;
        double sumEngagement = 0;
        int engagementCount = 0;
        long minTime = Long.MAX_VALUE;
        long maxTime = 0;
        Map<String, InsightsResult.TagStat> tags = new LinkedHashMap<>();

        if (current != null) {
            addTags(tags, current.description, current.views);
        }
        for (SampleVideo sample : list) {
            sumViews += sample.views;
            sumLikes += sample.likes;
            sumComments += sample.comments;
            sumShares += sample.shares;
            double engagement = percent(sample.likes + sample.comments + sample.shares, sample.views);
            if (sample.views > 0) {
                sumEngagement += engagement;
                engagementCount++;
            }
            if (sample.createTime > 0) {
                minTime = Math.min(minTime, sample.createTime);
                maxTime = Math.max(maxTime, sample.createTime);
            }
            if (current == null || !sample.id.equals(current.id)) {
                addTags(tags, sample.description, sample.views);
            }
        }
        if (!list.isEmpty()) {
            result.avgViews = sumViews / list.size();
            result.avgLikes = sumLikes / list.size();
            result.avgComments = sumComments / list.size();
            result.avgShares = sumShares / list.size();
        }
        result.avgEngagement = engagementCount == 0 ? 0 : sumEngagement / engagementCount;
        if (maxTime > minTime) {
            long days = Math.max(1, (maxTime - minTime) / 86400L);
            result.postsPerWeek = list.size() * 7.0 / days;
        }

        List<InsightsResult.SampleVideo> sorted = new ArrayList<>(list);
        Collections.sort(sorted, new Comparator<InsightsResult.SampleVideo>() {
            @Override
            public int compare(InsightsResult.SampleVideo a, InsightsResult.SampleVideo b) {
                return Long.compare(b.views, a.views);
            }
        });
        for (int i = 0; i < sorted.size() && i < 5; i++) result.samples.add(sorted.get(i));

        List<InsightsResult.TagStat> values = new ArrayList<>(tags.values());
        Collections.sort(values, new Comparator<InsightsResult.TagStat>() {
            @Override
            public int compare(InsightsResult.TagStat a, InsightsResult.TagStat b) {
                return Integer.compare(b.uses, a.uses);
            }
        });
        for (int i = 0; i < values.size() && i < 8; i++) result.hashtags.add(values.get(i));

        if (result.views > 0 && result.avgViews > 0) {
            result.compareAvgViews = (result.views - result.avgViews) * 100.0 / result.avgViews;
        }
        if (result.creator != null && result.creator.followers > 0 && result.views > 0) {
            result.viewRate = result.views * 100.0 / result.creator.followers;
        }
        if (current != null) {
            populateSoundFromVideo(result, list);
        } else {
            populateTopSound(result, list);
        }
    }

    private static void populateSoundFromVideo(InsightsResult result, List<SampleVideo> samples) {
        MediaItem video = result.video;
        if (video == null) return;
        if (video.musicTitle == null || video.musicTitle.trim().isEmpty()) return;
        InsightsResult.SoundStat sound = new InsightsResult.SoundStat();
        sound.id = video.musicId == null ? "" : video.musicId;
        sound.title = video.musicTitle;
        sound.author = video.musicAuthor == null ? "" : video.musicAuthor;
        sound.pageUrl = soundPage(sound.id, sound.title);
        sound.sampleUses = 1;
        sound.totalViews = video.playCount;
        double engagement = percent(video.diggCount + video.commentCount + video.shareCount,
                video.playCount);
        double sum = engagement;
        int count = 1;
        for (InsightsResult.SampleVideo sample : samples) {
            if (sample.id.equals(video.id)) continue;
            boolean same = (!sound.id.isEmpty() && sound.id.equals(sample.musicId))
                    || (!sample.musicTitle.isEmpty()
                    && sample.musicTitle.equalsIgnoreCase(sound.title));
            if (same) {
                sound.sampleUses++;
                sound.totalViews += sample.views;
                sum += percent(sample.likes + sample.comments + sample.shares, sample.views);
                count++;
            }
        }
        sound.avgEngagement = count == 0 ? 0 : sum / count;
        result.sound = sound;
    }

    private static void populateTopSound(InsightsResult result, List<SampleVideo> samples) {
        if (samples.isEmpty()) return;
        InsightsResult.SampleVideo top = null;
        int uses = 0;
        for (InsightsResult.SampleVideo sample : samples) {
            if (sample.musicTitle.isEmpty()) continue;
            int currentUses = 0;
            for (InsightsResult.SampleVideo other : samples) {
                if (sample.musicId.isEmpty() ? other.musicTitle.equalsIgnoreCase(sample.musicTitle)
                        : sample.musicId.equals(other.musicId)) {
                    currentUses++;
                }
            }
            if (currentUses > uses) {
                uses = currentUses;
                top = sample;
            }
        }
        if (top == null) return;
        InsightsResult.SoundStat sound = new InsightsResult.SoundStat();
        sound.id = top.musicId;
        sound.title = top.musicTitle;
        sound.author = top.musicAuthor;
        sound.pageUrl = soundPage(sound.id, sound.title);
        sound.sampleUses = uses;
        sound.totalViews = 0;
        double sum = 0;
        int count = 0;
        for (InsightsResult.SampleVideo sample : samples) {
            boolean same = sound.id.isEmpty() ? sample.musicTitle.equalsIgnoreCase(sound.title)
                    : sound.id.equals(sample.musicId);
            if (same) {
                sound.totalViews += sample.views;
                sum += percent(sample.likes + sample.comments + sample.shares, sample.views);
                count++;
            }
        }
        sound.avgEngagement = count == 0 ? 0 : sum / count;
        result.sound = sound;
    }

    private static List<SampleVideo> creatorSamples(String username) throws Exception {
        List<SampleVideo> list = searchSamples(username, false);
        List<SampleVideo> matches = new ArrayList<>();
        for (SampleVideo sample : list) {
            if (username.equalsIgnoreCase(sample.author)) matches.add(sample);
        }
        if (matches.isEmpty()) {
            List<SampleVideo> feed = feedSamples();
            for (SampleVideo sample : feed) {
                if (username.equalsIgnoreCase(sample.author)) matches.add(sample);
            }
        }
        return matches;
    }

    private static List<SampleVideo> searchSamples(String query, boolean fallbackToFeed) {
        String value = query == null ? "" : query.trim();
        if (value.length() > 80) value = value.substring(0, 80);
        try {
            String url = SEARCH_API + "?keywords=" + Http.encode(value)
                    + "&count=50&cursor=0&publish_time=30";
            List<SampleVideo> list = requestSamples(url);
            if (!list.isEmpty() || !fallbackToFeed) return list;
        } catch (Exception ignored) {
        }
        if (!fallbackToFeed) return new ArrayList<>();
        try {
            List<SampleVideo> feed = feedSamples();
            List<SampleVideo> filtered = new ArrayList<>();
            String needle = value.toLowerCase(Locale.US);
            for (SampleVideo sample : feed) {
                if (sample.description.toLowerCase(Locale.US).contains(needle)
                        || sample.musicTitle.toLowerCase(Locale.US).contains(needle)
                        || sample.author.equalsIgnoreCase(value)) {
                    filtered.add(sample);
                }
            }
            return filtered;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static List<SampleVideo> feedSamples() throws Exception {
        return requestSamples(FEED_API + "?region=US&count=50");
    }

    private static List<SampleVideo> requestSamples(String url) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Http.MOBILE_UA);
        headers.put("Referer", "https://www.tiktok.com/");
        headers.put("Accept", "application/json");
        Http.Response response = Http.get(url, headers);
        if (response == null || !response.ok()) throw new Exception("Could not load public data");
        JSONObject root = new JSONObject(response.body);
        if (root.optInt("code", -1) != 0) {
            throw new Exception(JsonUtils.string(root, "msg", "message"));
        }
        Object data = root.opt("data");
        JSONArray array = null;
        if (data instanceof JSONArray) {
            array = (JSONArray) data;
        } else if (data instanceof JSONObject) {
            JSONObject object = (JSONObject) data;
            array = object.optJSONArray("videos");
            if (array == null) array = object.optJSONArray("aweme_list");
        }
        if (array == null) return new ArrayList<>();
        Map<String, SampleVideo> unique = new LinkedHashMap<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            SampleVideo sample = parseSample(item);
            if (!sample.id.isEmpty() && !unique.containsKey(sample.id)) {
                unique.put(sample.id, sample);
            }
        }
        List<SampleVideo> out = new ArrayList<>(unique.values());
        Collections.sort(out, new Comparator<SampleVideo>() {
            @Override
            public int compare(SampleVideo a, SampleVideo b) {
                return Long.compare(b.createTime, a.createTime);
            }
        });
        return out;
    }

    private static SampleVideo parseSample(JSONObject item) {
        SampleVideo sample = new SampleVideo();
        if (item == null) return sample;
        sample.id = JsonUtils.string(item, "id", "video_id", "aweme_id", "video_id");
        sample.description = JsonUtils.string(item, "title", "desc", "description");
        if (sample.description.isEmpty()) {
            JSONArray desc = item.optJSONArray("content_desc");
            if (desc != null && desc.length() > 0) sample.description = desc.optString(0, "");
        }
        JSONObject author = JsonUtils.object(item, "author", "author_info");
        sample.author = JsonUtils.string(author, "unique_id", "uniqueId", "nickname", "name");
        sample.cover = JsonUtils.string(item, "cover", "origin_cover", "ai_dynamic_cover", "dynamic_cover");
        sample.createTime = MediaItem.seconds(item.optLong("create_time", 0));
        sample.views = item.optLong("play_count", item.optLong("playCount", 0));
        sample.likes = item.optLong("digg_count", item.optLong("diggCount", 0));
        sample.comments = item.optLong("comment_count", item.optLong("commentCount", 0));
        sample.shares = item.optLong("share_count", item.optLong("shareCount", 0));
        sample.saves = item.optLong("collect_count", item.optLong("collectCount", 0));
        sample.region = JsonUtils.string(item, "region");
        JSONObject music = JsonUtils.object(item, "music_info", "music", "song_info");
        if (music != null) {
            sample.musicTitle = JsonUtils.string(music, "title", "name");
            sample.musicAuthor = JsonUtils.string(music, "author", "artist", "owner");
            sample.musicId = JsonUtils.string(music, "id", "music_id");
        }
        sample.pageUrl = tiktokVideoUrl(sample.author, sample.id);
        return sample;
    }

    private static InsightsResult.TagStat fetchChallenge(String tag) {
        try {
            String url = "https://www.tiktok.com/api/challenge/detail/?challengeName="
                    + Http.encode(tag) + "&aid=1988";
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", Http.MOBILE_UA);
            headers.put("Referer", "https://www.tiktok.com/tag/" + Http.encode(tag));
            headers.put("Accept", "application/json");
            Http.Response response = Http.get(url, headers);
            if (response == null || !response.ok()) return null;
            JSONObject root = new JSONObject(response.body);
            JSONObject info = root.optJSONObject("challengeInfo");
            if (info == null) return null;
            JSONObject stats = info.optJSONObject("statsV2");
            if (stats == null) stats = info.optJSONObject("stats");
            if (stats == null) return null;
            InsightsResult.TagStat out = new InsightsResult.TagStat();
            JSONObject challenge = info.optJSONObject("challenge");
            out.tag = JsonUtils.string(challenge, "title");
            if (out.tag.isEmpty()) out.tag = tag;
            out.uses = (int) Math.min(Integer.MAX_VALUE,
                    Math.max(0, parseLong(stats.optString("videoCount", "0"))));
            out.views = parseLong(stats.optString("viewCount", "0"));
            if (out.uses == 0 && out.views == 0) return null;
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long parseLong(String value) {
        if (value == null) return 0;
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static InsightsResult.Profile fetchProfile(String username) throws Exception {
        String url = "https://www.tiktok.com/@" + Http.encode(username) + "?lang=en";
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Http.MOBILE_UA);
        headers.put("Accept-Language", "en-US,en;q=0.9");
        headers.put("Referer", "https://www.tiktok.com/");
        Http.Response response = Http.get(url, headers);
        if (response == null || !response.ok()) throw new Exception("Creator profile unavailable");
        JSONObject root = findUniversal(response.body);
        if (root == null) throw new Exception("Creator profile unavailable");
        JSONObject scope = root.optJSONObject("__DEFAULT_SCOPE__");
        JSONObject userDetail = scope == null ? null : scope.optJSONObject("webapp.user-detail");
        JSONObject userInfo = userDetail == null ? null : userDetail.optJSONObject("userInfo");
        JSONObject user = userInfo == null ? null : userInfo.optJSONObject("user");
        if (user == null) throw new Exception("Creator profile unavailable");
        InsightsResult.Profile profile = new InsightsResult.Profile();
        profile.uniqueId = JsonUtils.string(user, "uniqueId", "unique_id");
        profile.nickname = JsonUtils.string(user, "nickname", "nickName");
        profile.avatar = JsonUtils.string(user, "avatarLarger", "avatarMedium", "avatarThumb");
        profile.signature = JsonUtils.string(user, "signature");
        JSONObject bioLink = user.optJSONObject("bioLink");
        if (bioLink == null) bioLink = user.optJSONObject("bio_link");
        profile.bioLink = bioLink == null ? "" : JsonUtils.string(bioLink, "link", "url");
        if (profile.bioLink.isEmpty()) profile.bioLink = JsonUtils.string(user, "bioLink", "bio_link");
        profile.verified = user.optBoolean("verified", false);
        profile.pageUrl = url;
        JSONObject stats = userInfo.optJSONObject("stats");
        if (stats != null) {
            profile.followers = stats.optLong("followerCount", 0);
            profile.following = stats.optLong("followingCount", 0);
            profile.hearts = stats.optLong("heartCount", stats.optLong("heart", 0));
            profile.videos = stats.optLong("videoCount", 0);
        }
        return profile;
    }

    private static JSONObject findUniversal(String html) {
        if (html == null) return null;
        Matcher matcher = UNIVERSAL.matcher(html);
        if (matcher.find()) {
            try {
                return new JSONObject(matcher.group(1).trim());
            } catch (Exception ignored) {
            }
        }
        for (String script : JsonUtils.scriptJson(html, "")) {
            try {
                JSONObject object = new JSONObject(script);
                if (object.has("__DEFAULT_SCOPE__")) return object;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static InsightsResult.Profile profileFromSample(SampleVideo sample, String username) {
        InsightsResult.Profile profile = new InsightsResult.Profile();
        profile.uniqueId = username;
        profile.nickname = username;
        profile.avatar = sample.cover;
        profile.pageUrl = "https://www.tiktok.com/@" + Http.encode(username);
        return profile;
    }

    private static SampleVideo sampleFrom(MediaItem item) {
        SampleVideo sample = new SampleVideo();
        if (item == null) return sample;
        sample.id = item.id;
        sample.description = item.title;
        sample.author = item.author;
        sample.cover = item.coverUrl;
        sample.pageUrl = item.pageUrl;
        sample.createTime = item.createTime;
        sample.views = item.playCount;
        sample.likes = item.diggCount;
        sample.comments = item.commentCount;
        sample.shares = item.shareCount;
        sample.saves = item.collectCount;
        sample.musicTitle = item.musicTitle;
        sample.musicAuthor = item.musicAuthor;
        sample.musicId = item.musicId;
        sample.region = item.region;
        return sample;
    }

    private static void addTags(Map<String, InsightsResult.TagStat> tags, String description,
                                long views) {
        if (description == null || description.isEmpty()) return;
        Matcher matcher = TAG.matcher(description);
        while (matcher.find()) {
            String label = matcher.group(1);
            String key = label.toLowerCase(Locale.US);
            InsightsResult.TagStat stat = tags.get(key);
            if (stat == null) {
                stat = new InsightsResult.TagStat();
                stat.tag = label;
                tags.put(key, stat);
            }
            stat.uses++;
            stat.views += views;
        }
    }

    private static InsightsResult.TagStat findTag(List<InsightsResult.TagStat> tags, String tag) {
        for (InsightsResult.TagStat stat : tags) {
            if (stat.tag.equalsIgnoreCase(tag)) return stat;
        }
        return null;
    }

    private static long totalViews(List<SampleVideo> samples) {
        long total = 0;
        for (SampleVideo sample : samples) total += sample.views;
        return total;
    }

    private static double averageEngagement(List<SampleVideo> samples) {
        double sum = 0;
        int count = 0;
        for (SampleVideo sample : samples) {
            if (sample.views <= 0) continue;
            sum += percent(sample.likes + sample.comments + sample.shares, sample.views);
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    private static double percent(long numerator, long denominator) {
        return denominator <= 0 ? 0 : numerator * 100.0 / denominator;
    }

    private static String extractUsername(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.contains("/@")) value = value.substring(value.indexOf("/@") + 2);
        else if (value.startsWith("@")) value = value.substring(1);
        int cut = value.indexOf('?');
        if (cut >= 0) value = value.substring(0, cut);
        cut = value.indexOf('#');
        if (cut >= 0) value = value.substring(0, cut);
        cut = value.indexOf('/');
        if (cut >= 0) value = value.substring(0, cut);
        try {
            value = URLDecoder.decode(value, "UTF-8");
        } catch (Exception ignored) {
        }
        return value.trim();
    }

    private static String extractTag(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.contains("/tag/")) value = value.substring(value.indexOf("/tag/") + 5);
        int cut = value.indexOf('?');
        if (cut >= 0) value = value.substring(0, cut);
        cut = value.indexOf('/');
        if (cut >= 0) value = value.substring(0, cut);
        while (value.startsWith("#")) value = value.substring(1);
        try {
            value = URLDecoder.decode(value, "UTF-8");
        } catch (Exception ignored) {
        }
        return value.trim();
    }

    private static String soundQuery(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.contains("/music/")) {
            value = value.substring(value.indexOf("/music/") + 7);
            int cut = value.indexOf('?');
            if (cut >= 0) value = value.substring(0, cut);
            cut = value.indexOf('#');
            if (cut >= 0) value = value.substring(0, cut);
            int dash = value.lastIndexOf('-');
            if (dash > 0 && value.substring(dash + 1).matches("[0-9]{5,}")) {
                value = value.substring(0, dash);
            }
            value = value.replace('-', ' ');
        }
        return value.trim();
    }

    private static String extractSoundId(String raw) {
        if (raw == null) return "";
        Matcher matcher = SOUND_ID.matcher(raw);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String tiktokVideoUrl(String author, String id) {
        if (id == null || id.isEmpty()) return "https://www.tiktok.com/";
        return "https://www.tiktok.com/@" + (author == null || author.isEmpty() ? "user" : author)
                + "/video/" + id;
    }

    private static String soundPage(String id, String title) {
        if (id != null && !id.isEmpty()) return "https://www.tiktok.com/music/sound-" + id;
        return "https://www.tiktok.com/search?q=" + Http.encode(title == null ? "" : title);
    }
}
