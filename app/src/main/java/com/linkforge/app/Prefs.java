package com.linkforge.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/** 本地凭证存储（合规：仅保存用户主动授权后的 token，可撤销） */
final class Prefs {
    private static final String KEY = "linkforge_creds";
    private static SharedPreferences sp(Context c) { return c.getSharedPreferences(KEY, Context.MODE_PRIVATE); }

    static JSONObject all(Context c) {
        String raw = sp(c).getString("data", "{}");
        try { return new JSONObject(raw); } catch (Exception e) { return new JSONObject(); }
    }

    static void grant(Context c, String id, String provider, String token, String scope) {
        try {
            JSONObject all = all(c);
            JSONObject one = new JSONObject();
            one.put("provider", provider);
            one.put("token", token);
            one.put("scope", scope);
            one.put("time", System.currentTimeMillis());
            all.put(id, one);
            sp(c).edit().putString("data", all.toString()).apply();
        } catch (Exception ignored) {}
    }

    static void revoke(Context c, String id) {
        JSONObject all = all(c);
        all.remove(id);
        sp(c).edit().putString("data", all.toString()).apply();
    }

    static int count(Context c) { return all(c).length(); }

    static String mask(String token) {
        if (token == null || token.length() < 10) return token == null ? "" : token;
        return token.substring(0, 6) + "••••••••" + token.substring(token.length() - 4);
    }

    static final String THEME_KEY = "theme";
    static String theme(Context c) { return sp(c).getString(THEME_KEY, "system"); }
    static void setTheme(Context c, String v) { sp(c).edit().putString(THEME_KEY, v).apply(); }

    // 首次启动权限引导是否已展示
    private static final String PERM_INTRO_KEY = "perm_intro_done";
    static boolean permIntroDone(Context c) { return sp(c).getBoolean(PERM_INTRO_KEY, false); }
    static void setPermIntroDone(Context c) { sp(c).edit().putBoolean(PERM_INTRO_KEY, true).apply(); }

    // 界面语言：zh / en
    private static final String LANG_KEY = "lang";
    static String language(Context c) { return sp(c).getString(LANG_KEY, "zh"); }
    static void setLanguage(Context c, String v) { sp(c).edit().putString(LANG_KEY, v).apply(); }
}
