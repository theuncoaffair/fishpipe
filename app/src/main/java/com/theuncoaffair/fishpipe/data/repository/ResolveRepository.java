package com.theuncoaffair.fishpipe.data.repository;

import android.content.Context;

import dagger.hilt.android.qualifiers.ApplicationContext;

import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.net.LinkUtils;
import com.theuncoaffair.fishpipe.net.ResolveEngine;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class ResolveRepository {
    private final Context context;

    @Inject
    public ResolveRepository(@ApplicationContext Context context) {
        this.context = context.getApplicationContext();
    }

    public MediaItem resolve(String query) throws Exception {
        return new ResolveEngine(context).resolve(query);
    }

    public String findUrl(String text) {
        return LinkUtils.findUrl(text);
    }

    public String normalize(String text) {
        return LinkUtils.normalize(text);
    }

    public boolean isSupported(String normalized) {
        return LinkUtils.isSupported(normalized);
    }
}
