package com.theuncoaffair.fishpipe.download;

import android.content.Context;

import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.di.AppGraph;
import com.theuncoaffair.fishpipe.net.LinkUtils;
import com.theuncoaffair.fishpipe.util.Ui;

public final class ClipboardTracker {
    private ClipboardTracker() {
    }

    public static String refresh(Context context) {
        if (context == null) return "";
        Settings settings = AppGraph.get(context).settings();
        String text;
        long timestamp;
        try {
            text = Ui.clipboard(context);
            timestamp = Ui.clipboardTimestamp(context);
        } catch (Exception e) {
            return settings.getLastCopiedLink();
        }
        if (text.equals(settings.getLastClipboardSeen())
                && timestamp == settings.getLastClipboardTimestamp()) {
            return settings.getLastCopiedLink();
        }
        String url = LinkUtils.findUrl(text);
        if (!url.isEmpty() && LinkUtils.isSupported(url)) {
            settings.setLastClipboardSeen(text);
            settings.setLastClipboardTimestamp(timestamp);
            settings.setLastCopiedLink(url);
        }
        return settings.getLastCopiedLink();
    }
}
