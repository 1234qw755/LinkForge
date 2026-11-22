package com.linkforge.app;

import java.util.ArrayList;
import java.util.List;

/** 节点定义与上传协议适配器（与服务端 API 对应） */
final class Node {
    final String id, name, proto, endpoint, probe, tag, time;

    Node(String id, String name, String proto, String endpoint, String probe, String tag, String time) {
        this.id = id; this.name = name; this.proto = proto;
        this.endpoint = endpoint; this.probe = probe; this.tag = tag; this.time = time;
    }
    Node(String id, String name, String proto, String endpoint, String probe, String tag) {
        this(id, name, proto, endpoint, probe, tag, null);
    }
}

final class Nodes {
    static final List<Node> PUBLIC = new ArrayList<>();
    static final List<Provider> PROVIDERS = new ArrayList<>();

    static {
        // —— Catbox 系（永久）——
        PUBLIC.add(new Node("catbox", "Catbox", "catbox", "https://catbox.moe/user/api.php", "https://catbox.moe/", "永久"));
        PUBLIC.add(new Node("catbox-de", "Catbox DE", "catbox", "https://de.catbox.moe/user/api.php", "https://de.catbox.moe/", "永久"));
        PUBLIC.add(new Node("catbox-sg", "Catbox SG", "catbox", "https://sg.catbox.moe/user/api.php", "https://sg.catbox.moe/", "永久"));
        // —— Litterbox ——
        PUBLIC.add(new Node("lit-1h", "Litterbox 1h", "litterbox", "https://litterbox.catbox.moe/resources/internals/api.php", "https://litterbox.catbox.moe/", "1小时", "1h"));
        PUBLIC.add(new Node("lit-12h", "Litterbox 12h", "litterbox", "https://litterbox.catbox.moe/resources/internals/api.php", "https://litterbox.catbox.moe/", "12小时", "12h"));
        PUBLIC.add(new Node("lit-24h", "Litterbox 24h", "litterbox", "https://litterbox.catbox.moe/resources/internals/api.php", "https://litterbox.catbox.moe/", "24小时", "24h"));
        PUBLIC.add(new Node("lit-72h", "Litterbox 72h", "litterbox", "https://litterbox.catbox.moe/resources/internals/api.php", "https://litterbox.catbox.moe/", "72小时", "72h"));
        // —— 文本/临时床 ——
        PUBLIC.add(new Node("0x0", "0x0.st", "text", "https://0x0.st", "https://0x0.st/", "临时"));
        PUBLIC.add(new Node("qurl", "qurl.sh", "qurl", "https://qurl.sh/", "https://qurl.sh/", "7天"));
        PUBLIC.add(new Node("uguu", "Uguu", "uguu", "https://uguu.se/upload.php?output=text", "https://uguu.se/", "3小时"));
        PUBLIC.add(new Node("tmpfiles", "TmpFiles", "tmpfiles", "https://tmpfiles.org/api/v1/upload", "https://tmpfiles.org/", "临时"));
        PUBLIC.add(new Node("fileio", "file.io", "fileio", "https://file.io", "https://file.io/", "1次"));
        PUBLIC.add(new Node("freeimage", "FreeImage.host", "freeimage", "https://freeimage.host/api/1/upload?key=6d207e02198a847aa98d0a2a901485a5", "https://freeimage.host/", "永久"));
        // —— Pomf 系 ——
        PUBLIC.add(new Node("pomf-lain", "Pomf.lain", "pomf", "https://pomf.lain.la/upload.php", "https://pomf.lain.la/", "Pomf"));
        PUBLIC.add(new Node("pomf2-lain", "Pomf2.lain", "pomf", "https://pomf2.lain.la/upload.php", "https://pomf2.lain.la/", "Pomf"));
        PUBLIC.add(new Node("fileditch", "FileDitch", "pomf", "https://fileditch.com/upload.php", "https://fileditch.com/", "Pomf"));
        PUBLIC.add(new Node("quax", "Qu.ax", "pomf", "https://qu.ax/upload.php", "https://qu.ax/", "Pomf"));
        PUBLIC.add(new Node("midi", "UWU (midi.moe)", "pomf", "https://midi.moe/upload.php", "https://midi.moe/", "Pomf"));
        PUBLIC.add(new Node("pomf-cute", "Pomf CuteLolis", "pomf", "https://pomf.cutelolis.moe/upload.php", "https://pomf.cutelolis.moe/", "Pomf"));
        PUBLIC.add(new Node("junko", "Junko.moe", "pomf", "https://host.junko.cafe/upload.php", "https://host.junko.cafe/", "Pomf"));
        PUBLIC.add(new Node("ohayou", "Ohayou (sicp)", "pomf", "https://sicp.me/upload.php", "https://sicp.me/", "Pomf"));
        PUBLIC.add(new Node("marcweiss", "Pomf MarcWeiss", "pomf", "https://img.marcweiss.eu/upload.php", "https://img.marcweiss.eu/", "Pomf"));
        PUBLIC.add(new Node("akari", "Akari", "pomf", "https://akari.genshin.su/upload.php", "https://akari.genshin.su/", "Pomf"));
        PUBLIC.add(new Node("adie", "Adie", "pomf", "https://adie.space/upload.php", "https://adie.space/", "Pomf"));
        PUBLIC.add(new Node("crepe", "Crepe.moe", "pomf", "https://crepe.moe/upload.php", "https://crepe.moe/", "Pomf"));
        PUBLIC.add(new Node("kawai", "Kawai Files", "pomf", "https://f.sed.lol/upload.php", "https://f.sed.lol/", "Pomf"));
        PUBLIC.add(new Node("nagato", "Pomf Nagato", "pomf", "https://pomf.nagato.cz/upload.php", "https://pomf.nagato.cz/", "Pomf"));
        PUBLIC.add(new Node("patchouli", "Patchybox", "pomf", "https://patchouli.moe/upload.php", "https://patchouli.moe/", "Pomf"));
        PUBLIC.add(new Node("ayaya", "Ayaya", "pomf", "https://ayaya.beauty/upload.php", "https://ayaya.beauty/", "Pomf"));
        PUBLIC.add(new Node("picsur", "Picsur", "pomf", "https://picsur.host/upload.php", "https://picsur.host/", "Pomf"));
        PUBLIC.add(new Node("sicp2", "SICP Mirror", "pomf", "https://sicp.me/upload.php", "https://sicp.me/", "Pomf"));
        PUBLIC.add(new Node("neobox", "Neobox", "pomf", "https://neobox.neocities.org/upload.php", "https://neobox.neocities.org/", "Pomf"));
        PUBLIC.add(new Node("rxy", "RXY Upload", "text", "https://0x0.sh", "https://0x0.sh/", "临时"));
        PUBLIC.add(new Node("bashupload", "BashUpload", "text", "https://bashupload.com/", "https://bashupload.com/", "临时"));
        PUBLIC.add(new Node("transfer", "Transfer.sh", "text", "https://transfer.sh/", "https://transfer.sh/", "14天"));
        PUBLIC.add(new Node("osh.ax", "Osh.ax", "pomf", "https://osh.ax/upload.php", "https://osh.ax/", "Pomf"));
        PUBLIC.add(new Node("pomf-se", "Pomf.se Clone", "pomf", "https://pomf.se/upload.php", "https://pomf.se/", "Pomf"));
        PUBLIC.add(new Node("litterbox-2", "Litterbox Alt", "litterbox", "https://litterbox.catbox.moe/resources/internals/api.php", "https://litterbox.catbox.moe/", "临时", "24h"));
        PUBLIC.add(new Node("tmpfiles-2", "TmpFiles Alt", "tmpfiles", "https://tmpfiles.org/api/v1/upload", "https://tmpfiles.org/", "临时"));
        PUBLIC.add(new Node("uguu-2", "Uguu Alt", "uguu", "https://uguu.se/upload.php?output=text", "https://uguu.se/", "临时"));
        PUBLIC.add(new Node("catbox-alt", "Catbox Mirror", "catbox", "https://catbox.moe/user/api.php", "https://catbox.moe/", "永久"));
        PUBLIC.add(new Node("0x0-alt", "0x0 Mirror", "text", "https://0x0.st", "https://0x0.st/", "临时"));
        PUBLIC.add(new Node("qurl-alt", "qurl Mirror", "qurl", "https://qurl.sh/", "https://qurl.sh/", "7天"));
        PUBLIC.add(new Node("midi-2", "UWU Alt", "uguu", "https://midi.moe/upload.php?output=text", "https://midi.moe/", "临时"));
        PUBLIC.add(new Node("quax-2", "Qu.ax Alt", "uguu", "https://qu.ax/upload.php?output=text", "https://qu.ax/", "临时"));
        PUBLIC.add(new Node("fileditch-2", "FileDitch Alt", "uguu", "https://fileditch.com/upload.php?output=text", "https://fileditch.com/", "临时"));
        PUBLIC.add(new Node("cute-2", "CuteLolis Alt", "uguu", "https://pomf.cutelolis.moe/upload.php?output=text", "https://pomf.cutelolis.moe/", "临时"));
        PUBLIC.add(new Node("junko-2", "Junko Alt", "uguu", "https://host.junko.cafe/upload.php?output=text", "https://host.junko.cafe/", "临时"));
        PUBLIC.add(new Node("akari-2", "Akari Alt", "uguu", "https://akari.genshin.su/upload.php?output=text", "https://akari.genshin.su/", "临时"));
        PUBLIC.add(new Node("adie-2", "Adie Alt", "uguu", "https://adie.space/upload.php?output=text", "https://adie.space/", "临时"));
        PUBLIC.add(new Node("crepe-2", "Crepe Alt", "uguu", "https://crepe.moe/upload.php?output=text", "https://crepe.moe/", "临时"));
        PUBLIC.add(new Node("ayaya-2", "Ayaya Alt", "uguu", "https://ayaya.beauty/upload.php?output=text", "https://ayaya.beauty/", "临时"));
        PUBLIC.add(new Node("nagato-2", "Nagato Alt", "uguu", "https://pomf.nagato.cz/upload.php?output=text", "https://pomf.nagato.cz/", "临时"));
        PUBLIC.add(new Node("patchouli-2", "Patchybox Alt", "uguu", "https://patchouli.moe/upload.php?output=text", "https://patchouli.moe/", "临时"));
        PUBLIC.add(new Node("kawai-2", "Kawai Alt", "uguu", "https://f.sed.lol/upload.php?output=text", "https://f.sed.lol/", "临时"));
        PUBLIC.add(new Node("marcweiss-2", "MarcWeiss Alt", "uguu", "https://img.marcweiss.eu/upload.php?output=text", "https://img.marcweiss.eu/", "临时"));

        PROVIDERS.add(new Provider("aliyun", "阿里云盘", "阿", "#FF6A00", "OAuth 2.0 授权连接个人云盘", new String[]{"读取文件列表", "上传文件到你的云盘", "获取文件分享链接"}));
        PROVIDERS.add(new Provider("webdav", "WebDAV", "W", "#0082C8", "连接自建 / 第三方 WebDAV 服务", new String[]{"读写你的 WebDAV 目录", "生成公开访问链接"}));
        PROVIDERS.add(new Provider("github", "GitHub", "GH", "#24292F", "通过 GitHub OAuth 授权仓库访问", new String[]{"读写指定仓库", "创建 Gist 直链"}));
        PROVIDERS.add(new Provider("custom", "自建图床", "自", "#6750A4", "接入你的自建图床 / 对象存储", new String[]{"上传文件", "管理分享链接"}));
    }

    static final class Provider {
        final String id, name, shortName, color, desc;
        final String[] perms;
        Provider(String id, String name, String shortName, String color, String desc, String[] perms) {
            this.id = id; this.name = name; this.shortName = shortName;
            this.color = color; this.desc = desc; this.perms = perms;
        }
    }

    /** 文件字段名 */
    static String fileField(Node n) {
        switch (n.proto) {
            case "catbox": case "litterbox": return "fileToUpload";
            case "pomf": case "uguu": return "files[]";
            case "freeimage": return "source";
            case "qurl": return null; // PUT 裸数据
            default: return "file";
        }
    }

    /** 附加文本字段（返回 null 表示无） */
    static String[][] extraFields(Node n) {
        switch (n.proto) {
            case "catbox": return new String[][]{{"reqtype", "fileupload"}};
            case "litterbox": return new String[][]{{"reqtype", "fileupload"}, {"time", n.time != null ? n.time : "1h"}};
            case "freeimage": return new String[][]{{"key", "6d207e02198a847aa98d0a2a901485a5"}};
            default: return null;
        }
    }

    static boolean isPut(Node n) { return "qurl".equals(n.proto); }

    /** 从响应文本解析直链 */
    static String parseUrl(Node n, String text) {
        if (text == null) return null;
        String s = text.trim();
        switch (n.proto) {
            case "pomf":
                try {
                    org.json.JSONObject j = new org.json.JSONObject(s);
                    if (j.has("files")) {
                        org.json.JSONArray fa = j.getJSONArray("files");
                        if (fa.length() > 0 && fa.getJSONObject(0).has("url")) return fa.getJSONObject(0).getString("url");
                    }
                    if (j.has("url")) return j.getString("url");
                } catch (Exception ignored) {}
                return firstUrl(s);
            case "tmpfiles":
                try {
                    org.json.JSONObject j = new org.json.JSONObject(s);
                    org.json.JSONObject d = j.optJSONObject("data");
                    if (d != null && d.has("url")) return d.getString("url").replace("https://tmpfiles.org/", "https://tmpfiles.org/dl/");
                } catch (Exception ignored) {}
                return null;
            case "fileio":
                try {
                    org.json.JSONObject j = new org.json.JSONObject(s);
                    String u = j.optString("link", j.optString("url", null));
                    return u;
                } catch (Exception ignored) {}
                return null;
            case "freeimage":
                try {
                    org.json.JSONObject j = new org.json.JSONObject(s);
                    org.json.JSONObject img = j.optJSONObject("image");
                    return img != null ? img.optString("url", null) : null;
                } catch (Exception ignored) {}
                return null;
            default:
                return firstUrl(s);
        }
    }

    private static String firstUrl(String s) {
        for (String line : s.split("\\n")) {
            String t = line.trim();
            if (t.matches("(?i)^https?://.+")) return t;
        }
        // 有的服务返回 JSON 里含 url 字段
        if (s.contains("\"url\"")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"url\"\\s*:\\s*\"(https?://[^\"]+)\"").matcher(s);
            if (m.find()) return m.group(1).replace("\\/", "/");
        }
        return null;
    }
}
