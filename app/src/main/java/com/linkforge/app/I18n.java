package com.linkforge.app;

import android.content.Context;

/** 极简双语：默认中文，可切换英文。所有文案集中在此，Activity 与前台服务共用。 */
final class I18n {
    static boolean english = false;

    static void load(Context c) {
        english = "en".equals(Prefs.language(c));
    }

    /** 取文案：中文在前，英文在后 */
    static String t(String cn, String en) {
        return english ? en : cn;
    }
}
