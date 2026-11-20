package com.theuncoaffair.fishpipe.download;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.theuncoaffair.fishpipe.data.Settings;

public final class NetworkPolicy {
    private NetworkPolicy() {
    }

    public static boolean isAllowed(Context context, Settings settings) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false;

        String policy = settings.getNetworkPolicy();
        if (Settings.NETWORK_ANY.equals(policy)) return true;
        boolean wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
        boolean mobile = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        if (Settings.NETWORK_WIFI.equals(policy)) return wifi;
        if (Settings.NETWORK_MOBILE.equals(policy)) return mobile;
        return true;
    }
}
