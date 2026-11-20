package com.theuncoaffair.fishpipe.util;

import android.os.Build;
import android.view.Menu;

public final class MenuDividers {
    private MenuDividers() {
    }

    public static void enable(Menu menu) {
        if (menu != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            menu.setGroupDividerEnabled(true);
        }
    }
}
