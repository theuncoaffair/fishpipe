package com.theuncoaffair.fishpipe.core;

import javax.inject.Inject;

public final class SystemAppClock implements AppClock {
    @Inject
    public SystemAppClock() {
    }

    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }
}
