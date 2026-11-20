package com.theuncoaffair.fishpipe.pricing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class AnnualPrice {
    private static final String CHECKOUT_URL = "https://selar.com/sp4j7498fu";
    private static final String FALLBACK_CURRENCY = "USD";

    private static final Map<String, String> DISPLAYS;
    private static final Map<String, String> REGION_CURRENCIES;

    static {
        Map<String, String> displays = new HashMap<>();
        displays.put("NGN", "₦1,100.00");
        displays.put("USD", "$3.00");
        displays.put("GBP", "£3.00");
        displays.put("GHS", "GH¢10.32");
        displays.put("KES", "KSh116.32");
        displays.put("ZAR", "R14.41");
        displays.put("TZS", "TSh2,500.00");
        displays.put("UGX", "USh3,535");
        displays.put("XAF", "CFA562");
        displays.put("XOF", "CFA551");
        displays.put("RWF", "R₣1,287");
        displays.put("ZMW", "ZMW50.00");
        displays.put("SLE", "Le100.00");
        DISPLAYS = Collections.unmodifiableMap(displays);

        Map<String, String> regions = new HashMap<>();
        regions.put("NG", "NGN");
        regions.put("GB", "GBP");
        regions.put("GH", "GHS");
        regions.put("KE", "KES");
        regions.put("ZA", "ZAR");
        regions.put("TZ", "TZS");
        regions.put("UG", "UGX");
        regions.put("RW", "RWF");
        regions.put("ZM", "ZMW");
        regions.put("SL", "SLE");
        for (String code : new String[]{"CM", "CF", "CG", "TD", "GA", "GQ"}) {
            regions.put(code, "XAF");
        }
        for (String code : new String[]{"BJ", "BF", "CI", "GW", "ML", "NE", "SN", "TG"}) {
            regions.put(code, "XOF");
        }
        REGION_CURRENCIES = Collections.unmodifiableMap(regions);
    }

    private final String code;
    private final String currencyCode;
    private final String display;

    private AnnualPrice(@NonNull String code, @NonNull String currencyCode, @NonNull String display) {
        this.code = code;
        this.currencyCode = currencyCode;
        this.display = display;
    }

    @NonNull
    public static AnnualPrice forRegion(@Nullable String regionCode) {
        String code = regionCode == null ? "" : regionCode.trim().toUpperCase(Locale.US);
        String currencyCode = currencyForRegion(code);
        String display = DISPLAYS.get(currencyCode);
        if (display == null) {
            currencyCode = FALLBACK_CURRENCY;
            display = DISPLAYS.get(FALLBACK_CURRENCY);
        }
        return new AnnualPrice(code, currencyCode, display);
    }

    @NonNull
    private static String currencyForRegion(@NonNull String regionCode) {
        String mapped = REGION_CURRENCIES.get(regionCode);
        if (mapped != null) return mapped;
        if (regionCode.length() != 2) return FALLBACK_CURRENCY;
        try {
            Currency currency = Currency.getInstance(new Locale("", regionCode));
            if (currency != null && DISPLAYS.containsKey(currency.getCurrencyCode())) {
                return currency.getCurrencyCode();
            }
        } catch (IllegalArgumentException ignored) {
        }
        return FALLBACK_CURRENCY;
    }

    @NonNull
    public String code() {
        return code;
    }

    @NonNull
    public String currencyCode() {
        return currencyCode;
    }

    @NonNull
    public String display() {
        return display;
    }

    @NonNull
    public String checkoutUrl() {
        return CHECKOUT_URL + "?currency=" + currencyCode;
    }
}
