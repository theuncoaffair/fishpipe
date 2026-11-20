package com.theuncoaffair.fishpipe.core;

public interface AppLogger {
    void debug(String tag, String message);

    void info(String tag, String message);

    void warn(String tag, String message);

    void error(String tag, String message, Throwable throwable);
}
