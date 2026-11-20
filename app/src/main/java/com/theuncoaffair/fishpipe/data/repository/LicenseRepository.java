package com.theuncoaffair.fishpipe.data.repository;

import android.content.Context;

import dagger.hilt.android.qualifiers.ApplicationContext;

import com.theuncoaffair.fishpipe.billing.LicenseManager;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class LicenseRepository {
    private final Context context;

    @Inject
    public LicenseRepository(@ApplicationContext Context context) {
        this.context = context.getApplicationContext();
    }

    public int freeLimit() {
        return LicenseManager.FREE_LIMIT;
    }

    public boolean isProActive() {
        return LicenseManager.isProActive(context);
    }

    public int remaining() {
        return LicenseManager.remaining(context);
    }

    public boolean allowDownload() {
        return LicenseManager.allowDownload(context);
    }

    public void recordUse() {
        LicenseManager.recordUse(context);
    }

    public void resetUsage() {
        LicenseManager.resetUsage(context);
    }

    public void bootstrap() {
        LicenseManager.bootstrap(context);
    }

    public boolean isDeveloper() {
        return LicenseManager.isDeveloper(context);
    }

    public void showPaywall() {
        LicenseManager.showPaywall(context);
    }

    public String deviceHash() {
        return LicenseManager.deviceHash(context);
    }

    public String deviceLabel() {
        return LicenseManager.deviceLabel(context);
    }
}
