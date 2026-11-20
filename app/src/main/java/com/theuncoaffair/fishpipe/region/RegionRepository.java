package com.theuncoaffair.fishpipe.region;

import com.theuncoaffair.fishpipe.net.Http;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RegionRepository {
    private static final String URL = "https://www.tikwm.com/api/region";

    private RegionRepository() {
    }

    public static List<Region> load() {
        List<Region> list = fetch();
        if (list.isEmpty()) list = fallback();
        Collections.sort(list, new Comparator<Region>() {
            @Override
            public int compare(Region a, Region b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return list;
    }

    private static List<Region> fetch() {
        List<Region> out = new ArrayList<>();
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Accept", "application/json");
            headers.put("User-Agent", Http.MOBILE_UA);
            Http.Response response = Http.get(URL, headers);
            if (!response.ok()) return out;
            JSONObject root = new JSONObject(response.body);
            JSONObject data = root.optJSONObject("data");
            if (data == null) return out;
            Iterator<String> keys = data.keys();
            while (keys.hasNext()) {
                String code = keys.next();
                String name = data.optString(code, code);
                if (code.length() == 2) out.add(new Region(code.toUpperCase(Locale.US), name));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static String nameFor(String code) {
        if (code == null || code.isEmpty()) return "";
        for (Region region : fallback()) {
            if (region.code.equalsIgnoreCase(code)) return region.name;
        }
        return code;
    }

    private static List<Region> fallback() {
        List<Region> out = new ArrayList<>();
        add(out, "US", "United States");
        add(out, "GB", "United Kingdom");
        add(out, "CA", "Canada");
        add(out, "AU", "Australia");
        add(out, "NZ", "New Zealand");
        add(out, "IE", "Ireland");
        add(out, "DE", "Germany");
        add(out, "FR", "France");
        add(out, "ES", "Spain");
        add(out, "IT", "Italy");
        add(out, "PT", "Portugal");
        add(out, "NL", "Netherlands");
        add(out, "BE", "Belgium");
        add(out, "CH", "Switzerland");
        add(out, "AT", "Austria");
        add(out, "SE", "Sweden");
        add(out, "NO", "Norway");
        add(out, "DK", "Denmark");
        add(out, "FI", "Finland");
        add(out, "PL", "Poland");
        add(out, "RO", "Romania");
        add(out, "TR", "Türkiye");
        add(out, "RU", "Russia");
        add(out, "UA", "Ukraine");
        add(out, "IN", "India");
        add(out, "PK", "Pakistan");
        add(out, "BD", "Bangladesh");
        add(out, "ID", "Indonesia");
        add(out, "MY", "Malaysia");
        add(out, "SG", "Singapore");
        add(out, "PH", "Philippines");
        add(out, "TH", "Thailand");
        add(out, "VN", "Vietnam");
        add(out, "JP", "Japan");
        add(out, "KR", "South Korea");
        add(out, "CN", "China mainland");
        add(out, "HK", "Hong Kong");
        add(out, "TW", "Taiwan");
        add(out, "BR", "Brazil");
        add(out, "MX", "Mexico");
        add(out, "AR", "Argentina");
        add(out, "CL", "Chile");
        add(out, "CO", "Colombia");
        add(out, "PE", "Peru");
        add(out, "ZA", "South Africa");
        add(out, "NG", "Nigeria");
        add(out, "KE", "Kenya");
        add(out, "EG", "Egypt");
        add(out, "SA", "Saudi Arabia");
        add(out, "AE", "United Arab Emirates");
        add(out, "IL", "Israel");
        add(out, "IQ", "Iraq");
        return out;
    }

    private static void add(List<Region> list, String code, String name) {
        list.add(new Region(code, name));
    }
}
