package com.linkforge.app;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** 原生 HTTP：节点探测 + 文件上传（不受 WebView CORS 限制） */
final class Net {
    static final String UA = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";

    static final class ProbeResult {
        final Node node; final boolean ok; final long latency;
        ProbeResult(Node n, boolean ok, long lat) { this.node = n; this.ok = ok; this.latency = lat; }
    }

    /** 纯延迟探测：HEAD 优先，失败退回 GET；不做任何 1KB 上传下载 */
    static ProbeResult probe(Node node) {
        long t0 = System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(node.probe).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3500);
            conn.setReadTimeout(3500);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", UA);
            int code = conn.getResponseCode();
            // 读取少量响应确保连接真正可用，避免某些服务器握手成功但不返回数据
            InputStream is = code < 400 ? conn.getInputStream() : conn.getErrorStream();
            if (is != null) { is.read(new byte[64]); is.close(); }
            boolean ok = code < 500;
            return new ProbeResult(node, ok, ok ? (System.currentTimeMillis() - t0) : Long.MAX_VALUE);
        } catch (Exception e) {
            return new ProbeResult(node, false, Long.MAX_VALUE);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    static final class UploadResult {
        final String url; final String error;
        UploadResult(String url, String error) { this.url = url; this.error = error; }
        static UploadResult ok(String u) { return new UploadResult(u, null); }
        static UploadResult fail(String e) { return new UploadResult(null, e); }
    }

    interface Progress { void on(int percent); }

    /** 流式上传：直接从 Uri 打开输入流边读边写，永不把整个文件读进内存，多大文件都不会 OOM */
    static UploadResult upload(Context ctx, Node node, Uri uri, String fileName, long fileSize, Progress progress) {
        HttpURLConnection conn = null;
        InputStream fis = null;
        try {
            String boundary = "----LinkForgeBoundary" + System.currentTimeMillis();
            boolean put = Nodes.isPut(node);
            conn = (HttpURLConnection) new URL(node.endpoint).openConnection();
            conn.setRequestMethod(put ? "PUT" : "POST");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "*/*");

            byte[] head, tail;
            long total;
            if (put) {
                head = new byte[0];
                tail = new byte[0];
                conn.setRequestProperty("Content-Type", "application/octet-stream");
                total = fileSize > 0 ? fileSize : -1;
            } else {
                java.io.ByteArrayOutputStream h = new java.io.ByteArrayOutputStream();
                String[][] extras = Nodes.extraFields(node);
                if (extras != null) {
                    for (String[] kv : extras) {
                        h.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                        h.write(("Content-Disposition: form-data; name=\"" + kv[0] + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                        h.write((kv[1] + "\r\n").getBytes(StandardCharsets.UTF_8));
                    }
                }
                String field = Nodes.fileField(node);
                h.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                h.write(("Content-Disposition: form-data; name=\"" + field + "\"; filename=\"" + safeName(fileName) + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                h.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                head = h.toByteArray();
                h.reset();
                h.write("\r\n".getBytes(StandardCharsets.UTF_8));
                h.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                tail = h.toByteArray();
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                total = (fileSize > 0 ? fileSize : 0) + head.length + tail.length;
            }

            fis = ctx.getContentResolver().openInputStream(uri);
            if (fis == null) return UploadResult.fail("无法打开文件");

            if (total > 0) conn.setFixedLengthStreamingMode(total);
            else conn.setChunkedStreamingMode(64 * 1024);

            DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
            long written = 0;
            if (head.length > 0) { dos.write(head); written += head.length; }
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = fis.read(buf)) != -1) {
                dos.write(buf, 0, n);
                written += n;
                if (progress != null && total > 0)
                    progress.on((int) Math.min(99, written * 100L / total));
            }
            fis.close();
            fis = null;
            if (tail.length > 0) { dos.write(tail); written += tail.length; }
            dos.flush();
            dos.close();

            int status = conn.getResponseCode();
            if (status < 200 || status >= 400) return UploadResult.fail("HTTP " + status);
            InputStream is = conn.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            String url = Nodes.parseUrl(node, sb.toString());
            if (url == null) return UploadResult.fail("返回格式无法识别");
            if (progress != null) progress.on(100);
            return UploadResult.ok(url);
        } catch (Exception e) {
            String msg = e.getMessage();
            return UploadResult.fail(msg == null || msg.isEmpty() ? e.getClass().getSimpleName() : msg);
        } finally {
            if (fis != null) { try { fis.close(); } catch (Exception ignored) {} }
            if (conn != null) conn.disconnect();
        }
    }

    /** 查询文件大小（字节）；未知返回 -1 */
    static long fileSize(Context ctx, Uri uri) {
        android.database.Cursor c = null;
        try {
            c = ctx.getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (i >= 0) return c.getLong(i);
            }
        } catch (Exception ignored) {
        } finally { if (c != null) c.close(); }
        return -1;
    }

    private static String safeName(String n) {
        return n == null ? "file.bin" : n.replace("\"", "").replace("\r", "").replace("\n", "");
    }

    /** 从 content:// Uri 读取全部字节 */
    static byte[] readBytes(Context ctx, Uri uri) throws Exception {
        InputStream is = ctx.getContentResolver().openInputStream(uri);
        if (is == null) throw new Exception("无法打开文件");
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        is.close();
        return bos.toByteArray();
    }

    static String displayName(Context ctx, Uri uri) {
        String name = null;
        android.database.Cursor c = null;
        try {
            c = ctx.getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignored) {
        } finally { if (c != null) c.close(); }
        if (name == null) {
            String p = uri.getLastPathSegment();
            name = p != null ? p : "file";
        }
        return name;
    }
}
