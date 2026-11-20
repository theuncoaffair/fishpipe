package com.theuncoaffair.fishpipe.region;

public final class Region {
    public final String code;
    public final String name;

    public Region(String code, String name) {
        this.code = code == null ? "" : code;
        this.name = name == null || name.trim().isEmpty() ? code : name;
    }
}
