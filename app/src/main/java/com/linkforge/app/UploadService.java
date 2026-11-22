package com.linkforge.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 前台服务：上传任务在此执行，与 Activity 生命周期完全解耦。
 * 切换主题 recreate()、退到桌面、锁屏都不会中断上传。
 * 通知栏常驻显示「正在上传 · 文件名」（无进度），双击通知回到 App。
 */
public class UploadService extends Service {
    private static final String TAG = "LinkForgeSvc";
    public static final String ACTION_EVENT = "com.linkforge.app.UPLOAD_EVENT";
    private static final String CHANNEL_ID = "linkforge_upload";
    private static final int NOTIFY_ID = 1001;

    /** 进程内共享状态：服务写入，Activity 读取；Activity 重建后仍可恢复 */
    public static final class State {
        public static volatile boolean active = false;
        public static volatile boolean done = false;
        public static volatile boolean bannerVisible = false;
        public static volatile String name = "";
        public static volatile String stage = "";
        public static volatile int pct = 0;
        public static volatile int total = 0;
        public static volatile int success = 0;
        public static volatile boolean failedAll = false;
        public static volatile String node = null;
        public static volatile long stamp = 0;
        public static final List<String> urls = Collections.synchronizedList(new ArrayList<String>());
        public static final List<String> failed = Collections.synchronizedList(new ArrayList<String>());
    }

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        String[] uris = intent.getStringArrayExtra("uris");
        String[] names = intent.getStringArrayExtra("names");
        long[] sizes = intent.getLongArrayExtra("sizes");
        String[] order = intent.getStringArrayExtra("order");
        final long stamp = intent.getLongExtra("stamp", 0);
        if (uris == null || uris.length == 0) { stopSelf(); return START_NOT_STICKY; }

        createChannel();
        final List<String> fUris = arr(uris);
        final List<String> fNames = names != null ? arr(names) : new ArrayList<String>();
        final List<Long> fSizes = new ArrayList<Long>();
        if (sizes != null) for (long s : sizes) fSizes.add(s);
        final List<String> fOrder = order != null ? arr(order) : new ArrayList<String>();

        State.active = true;
        State.done = false;
        State.stamp = stamp;
        State.pct = 0;
        State.total = fUris.size();
        State.success = 0;
        State.failedAll = false;
        State.urls.clear();
        State.failed.clear();
        State.name = fNames.isEmpty() ? "文件" : fNames.get(0);

        // 立即进入前台：上传中通知（无进度、常驻、不可滑动关闭）
        startFg(buildNotify(State.name, true));

        new Thread(() -> run(fUris, fNames, fSizes, fOrder, stamp), "lf-upload").start();
        return START_NOT_STICKY;
    }

    private void run(List<String> uris, List<String> names, List<Long> sizes, List<String> orderIds, long stamp) {
        try {
            List<Node> order = new ArrayList<Node>();
            for (String id : orderIds) {
                for (Node n : Nodes.PUBLIC) if (n.id.equals(id)) { order.add(n); break; }
            }
            for (Node n : Nodes.PUBLIC) if (!order.contains(n)) order.add(n);

            int success = 0;
            List<String> okUrls = new ArrayList<String>(), failedNames = new ArrayList<String>();
            String usedNode = null;

            for (int i = 0; i < uris.size(); i++) {
                if (State.stamp != stamp) return;
                final int fi = i;
                Uri uri = Uri.parse(uris.get(i));
                final String name = i < names.size() ? names.get(i) : ("文件 " + (i + 1));
                long size = i < sizes.size() ? sizes.get(i) : -1L;
                final String label = (uris.size() > 1 ? "[" + (fi + 1) + "/" + uris.size() + "] " : "") + name;

                State.name = label;
                State.stage = label;
                State.pct = 0;
                main.post(() -> notifyText(label));
                emit("progress");

                boolean done = false;
                for (Node node : order) {
                    if (State.stamp != stamp) return;
                    Net.UploadResult r = Net.upload(this, node, uri, name, size, pct -> {
                        if (State.stamp != stamp) return;
                        State.pct = pct;
                        emit("progress");
                    });
                    if (r.url != null) { okUrls.add(r.url); usedNode = node.name; done = true; break; }
                }
                if (State.stamp != stamp) return;
                if (done) success++; else failedNames.add(name);
            }

            if (State.stamp != stamp) return;
            State.success = success;
            State.done = true;
            State.failedAll = success == 0;
            State.node = usedNode;
            State.pct = 100;
            State.urls.clear(); State.urls.addAll(okUrls);
            State.failed.clear(); State.failed.addAll(failedNames);

            final String doneText = success > 0
                    ? "上传完成 · 点击查看（" + success + "/" + uris.size() + "）"
                    : "上传失败 · 点击查看";
            emit("done");
            finishForeground(doneText);
        } catch (Throwable t) {
            Log.e(TAG, "upload error", t);
            State.done = true;
            State.failedAll = true;
            State.pct = 0;
            emit("done");
            finishForeground("上传出错 · 点击查看");
        }
    }

    private List<String> arr(String[] a) {
        List<String> l = new ArrayList<String>();
        if (a != null) Collections.addAll(l, a);
        return l;
    }

    // ---------- 通知 ----------
    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "上传通知", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("文件上传状态");
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private void startFg(Notification n) {
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFY_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(NOTIFY_ID, n);
        } catch (Throwable t) {
            try { startForeground(NOTIFY_ID, n); } catch (Throwable ignored) {}
        }
    }

    private Notification buildNotify(String text, boolean ongoing) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) fl |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, fl);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(this, CHANNEL_ID);
        else b = new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("LinkForge")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
                .setOnlyAlertOnce(true);
        return b.build();
    }

    private void notifyText(String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFY_ID, buildNotify(text, true));
        } catch (Throwable ignored) {}
    }

    /** 完成后：退出前台状态、更新为可点击的普通通知、结束服务；状态留在 static 供 App 查看 */
    private void finishForeground(String text) {
        main.post(() -> {
            try { stopForeground(false); } catch (Throwable ignored) {}
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(NOTIFY_ID, buildNotify(text, false));
            } catch (Throwable ignored) {}
            try { stopSelf(); } catch (Throwable ignored) {}
        });
    }

    private void emit(String type) {
        Intent i = new Intent(ACTION_EVENT);
        i.setPackage(getPackageName());
        i.putExtra("type", type);
        try { sendBroadcast(i); } catch (Throwable ignored) {}
    }

    @Override public void onDestroy() { super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
