package com.userempowermentlab.kidsrecorder;

import android.content.Context;
import android.content.pm.ApplicationInfo;

/**
 * Created by mingrui on 7/17/2018.
 */

public class Helper {
    public static String getApplicationName(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
    }
}
