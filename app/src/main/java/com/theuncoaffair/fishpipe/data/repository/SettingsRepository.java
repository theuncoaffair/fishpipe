package com.theuncoaffair.fishpipe.data.repository;

import com.theuncoaffair.fishpipe.data.Settings;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class SettingsRepository {
    private final Settings settings;

    @Inject
    public SettingsRepository(Settings settings) {
        this.settings = settings;
    }

    public boolean isAutoDetect() {
        return settings.isAutoDetect();
    }

    public void setAutoDetect(boolean value) {
        settings.setAutoDetect(value);
    }

    public boolean isStayInApp() {
        return settings.isStayInApp();
    }

    public void setStayInApp(boolean value) {
        settings.setStayInApp(value);
    }

    public String getLastClipboard() {
        return settings.getLastClipboard();
    }

    public void setLastClipboard(String value) {
        settings.setLastClipboard(value);
    }

    public String getLanguage() {
        return settings.getLanguage();
    }

    public void setLanguage(String value) {
        settings.setLanguage(value);
    }

    public String getDownloadPath() {
        return settings.getDownloadPath();
    }

    public void setDownloadPath(String value) {
        settings.setDownloadPath(value);
    }

    public String getFolderMode() {
        return settings.getFolderMode();
    }

    public void setFolderMode(String mode) {
        settings.setFolderMode(mode);
    }

    public String getDefaultVideo() {
        return settings.getDefaultVideo();
    }

    public void setDefaultVideo(String value) {
        settings.setDefaultVideo(value);
    }

    public String getDefaultPhotos() {
        return settings.getDefaultPhotos();
    }

    public void setDefaultPhotos(String value) {
        settings.setDefaultPhotos(value);
    }

    public int getMaxTasks() {
        return settings.getMaxTasks();
    }

    public void setMaxTasks(int value) {
        settings.setMaxTasks(value);
    }

    public int getSpeedLimitKbps() {
        return settings.getSpeedLimitKbps();
    }

    public void setSpeedLimitKbps(int value) {
        settings.setSpeedLimitKbps(value);
    }

    public String getNetworkPolicy() {
        return settings.getNetworkPolicy();
    }

    public void setNetworkPolicy(String policy) {
        settings.setNetworkPolicy(policy);
    }

    public boolean isShowDownloadOptions() {
        return settings.isShowDownloadOptions();
    }

    public void setShowDownloadOptions(boolean value) {
        settings.setShowDownloadOptions(value);
    }

    public boolean isToastDownloadStart() {
        return settings.isToastDownloadStart();
    }

    public void setToastDownloadStart(boolean value) {
        settings.setToastDownloadStart(value);
    }

    public boolean isToastDownloadEnd() {
        return settings.isToastDownloadEnd();
    }

    public void setToastDownloadEnd(boolean value) {
        settings.setToastDownloadEnd(value);
    }

    public boolean isToastEmbedStart() {
        return settings.isToastEmbedStart();
    }

    public void setToastEmbedStart(boolean value) {
        settings.setToastEmbedStart(value);
    }

    public boolean isToastEmbedFinished() {
        return settings.isToastEmbedFinished();
    }

    public void setToastEmbedFinished(boolean value) {
        settings.setToastEmbedFinished(value);
    }

    public boolean isCaptionsEnabled() {
        return settings.isCaptionsEnabled();
    }

    public void setCaptionsEnabled(boolean value) {
        settings.setCaptionsEnabled(value);
    }

    public boolean isNotifyProgress() {
        return settings.isNotifyProgress();
    }

    public boolean isNotifyComplete() {
        return settings.isNotifyComplete();
    }

    public String getRegionCode() {
        return settings.getRegionCode();
    }

    public String getRegionName() {
        return settings.getRegionName();
    }

    public void setRegion(String code, String name) {
        settings.setRegion(code, name);
    }

    public String getTheme() {
        return settings.getTheme();
    }

    public void setTheme(String value) {
        settings.setTheme(value);
    }

    public String getCustomResolver() {
        return settings.getCustomResolver();
    }

    public boolean isWhatsNewDismissed() {
        return settings.isWhatsNewDismissed();
    }

    public void setWhatsNewDismissed(boolean value) {
        settings.setWhatsNewDismissed(value);
    }

    public boolean isCreatorFolders() {
        return settings.isCreatorFolders();
    }

    public String getProEmail() {
        return settings.getProEmail();
    }

    public int getDownloadUses() {
        return settings.getDownloadUses();
    }

    public boolean isPaywallDismissed() {
        return settings.isPaywallDismissed();
    }

    public void setPaywallDismissed(boolean dismissed) {
        settings.setPaywallDismissed(dismissed);
    }

    public void activatePro(String email, String sessionToken, long expiresAt,
                            long licenseExpiresAt) {
        settings.setProUnlocked(true, email);
        settings.setSession(sessionToken, expiresAt);
        settings.setLicenseExpiry(licenseExpiresAt);
        settings.setPaywallDismissed(false);
    }

    public String getDefaultAudio() {
        return settings.getDefaultAudio();
    }
}
