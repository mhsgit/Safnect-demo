package com.populstay.wallet.dimen;

import android.content.res.Resources;
import android.util.DisplayMetrics;

import com.populstay.wallet.BaseApp;

/**
 * Created by Jerry
 */

public final class DimenUtil {

    public static int getScreenWidth() {
        final Resources resources = BaseApp.instance.getResources();
        final DisplayMetrics dm = resources.getDisplayMetrics();
        return dm.widthPixels;
    }

    public static int getScreenHeight() {
        final Resources resources = BaseApp.instance.getResources();
        final DisplayMetrics dm = resources.getDisplayMetrics();
        return dm.heightPixels;
    }
}
