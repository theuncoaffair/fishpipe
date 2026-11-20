package com.theuncoaffair.fishpipe.core;

import javax.inject.Inject;

import timber.log.Timber;

public final class TimberAppLogger implements AppLogger {
    @Inject
    public TimberAppLogger() {
    }

    @Override
    public void debug(String tag, String message) {
        Timber.tag(tag).d(message);
    }

    @Override
    public void info(String tag, String message) {
        Timber.tag(tag).i(message);
    }

    @Override
    public void warn(String tag, String message) {
        Timber.tag(tag).w(message);
    }

    @Override
    public void error(String tag, String message, Throwable throwable) {
        Timber.tag(tag).e(throwable, message);
    }
}
