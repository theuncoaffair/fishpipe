package com.theuncoaffair.fishpipe.analytics;

import com.theuncoaffair.fishpipe.data.MediaItem;

import java.util.ArrayList;
import java.util.List;

public final class InsightsResult {
    public int mode;
    public MediaItem video;
    public Profile creator;
    public SoundStat sound;
    public final List<TagStat> hashtags = new ArrayList<>();
    public final List<SampleVideo> samples = new ArrayList<>();
    public String warning = "";
    public long views;
    public long likes;
    public long comments;
    public long shares;
    public long saves;
    public double engagementRate;
    public double likeRate;
    public double saveRate;
    public double shareRate;
    public double commentRate;
    public double viewRate = -1;
    public double compareAvgViews = Double.NaN;
    public int sampleVideos;
    public long avgViews;
    public long avgLikes;
    public long avgComments;
    public long avgShares;
    public double avgEngagement;
    public double postsPerWeek;

    public static final class Profile {
        public String uniqueId = "";
        public String nickname = "";
        public String avatar = "";
        public String signature = "";
        public String bioLink = "";
        public boolean verified;
        public long followers;
        public long following;
        public long hearts;
        public long videos;
        public String pageUrl = "";
    }

    public static final class SampleVideo {
        public String id = "";
        public String description = "";
        public String author = "";
        public String cover = "";
        public String pageUrl = "";
        public long createTime;
        public long views;
        public long likes;
        public long comments;
        public long shares;
        public long saves;
        public String musicTitle = "";
        public String musicAuthor = "";
        public String musicId = "";
        public String region = "";
    }

    public static final class TagStat {
        public String tag = "";
        public int uses;
        public long views;
    }

    public static final class SoundStat {
        public String id = "";
        public String title = "";
        public String author = "";
        public String pageUrl = "";
        public int sampleUses;
        public long totalViews;
        public double avgEngagement;
    }
}
