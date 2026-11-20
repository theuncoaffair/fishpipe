package com.theuncoaffair.fishpipe.di;

import android.content.Context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.theuncoaffair.fishpipe.core.AppClock;
import com.theuncoaffair.fishpipe.core.AppLogger;
import com.theuncoaffair.fishpipe.core.SystemAppClock;
import com.theuncoaffair.fishpipe.core.TimberAppLogger;
import com.theuncoaffair.fishpipe.data.Settings;

import javax.inject.Singleton;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import javax.inject.Named;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public abstract class AppModule {
    public static final String IO = "io";
    @Binds
    @Singleton
    abstract AppClock bindClock(SystemAppClock clock);

    @Binds
    @Singleton
    abstract AppLogger bindLogger(TimberAppLogger logger);

    @Provides
    @Singleton
    static Settings provideSettings(@ApplicationContext Context context) {
        return new Settings(context);
    }

    @Provides
    @Singleton
    @Named(IO)
    static ExecutorService provideIoExecutor() {
        return Executors.newFixedThreadPool(3, runnable -> {
            Thread thread = new Thread(runnable, "fishpipe-io");
            thread.setDaemon(true);
            return thread;
        });
    }
}
