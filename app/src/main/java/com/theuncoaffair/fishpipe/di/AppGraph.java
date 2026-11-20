package com.theuncoaffair.fishpipe.di;

import android.content.Context;

import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.data.repository.ResolveRepository;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;

@EntryPoint
@InstallIn(SingletonComponent.class)
public interface AppGraph {
    Settings settings();

    ResolveRepository resolveRepository();

    static AppGraph get(Context context) {
        return EntryPointAccessors.fromApplication(
                context.getApplicationContext(), AppGraph.class);
    }
}
