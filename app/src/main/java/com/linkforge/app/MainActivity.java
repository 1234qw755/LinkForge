package com.linkforge.app;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "LinkForge";
    private final ExecutorService pool = Executors.newFixedThreadPool(24);
    private final Handler main = new Handler(Looper.getMainLooper());

    // 颜色硬编码，深浅两套，绝不依赖主题属性解析（避免透明/空白）
    private int PRIMARY, ON_PRIMARY, PRIMARY_CONTAINER, ON_PRIMARY_CONTAINER;
    private int SECONDARY_CONTAINER, ON_SECONDARY_CONTAINER, TERTIARY_CONTAINER, ON_TERTIARY_CONTAINER;
    private int SURFACE, SURFACE_LOW, SURFACE_HIGH, ON_SURFACE, ON_SURFACE_VARIANT, OUTLINE, ERROR;

    private LinearLayout root;
    private View homeView;

    private Dialog uploadDialog;
    private final List<Uri> pickedUris = new ArrayList<>();
    private final List<String> pickedNames = new ArrayList<>();
    private final List<Long> pickedSizes = new ArrayList<>();
    private TextView fileInfoText;
    private View fileInfoRow, dropZone;
    private Spinner nodeSpinner;
    private ArrayAdapter<String> spinnerAdapter;
    private final List<Node> spinnerNodes = new ArrayList<>();
    private final Map<String, Net.ProbeResult> resultMap = new HashMap<>();
    private ProgressBar progressBar;
    private TextView progressStage, progressPct;
    private View resultBox;
    private TextView resultTitle, resultLink, resultNodeInfo;
    private LinearLayout multiList;
    private View singleResult, multiResult;
    private Button startUploadBtn, bgContinueBtn, pauseBtn, cancelUploadBtn;
    private int selectedPos = 0;
    private boolean uploading = false;
    // 后台继续模式：工作台收起，进度常驻显示在顶部
    private boolean backgroundMode = false;
    private View topBanner;
    private TextView topBannerName, topBannerPct;
    private ProgressBar topBannerBar;
    private final List<String> lastUrls = new ArrayList<>();
    private boolean lastFailedAll = false;
    // 任务代号：打开窗口/清空/重选时递增，用于作废在途的后台任务回调，避免旧结果污染新状态
    private int uploadSeq = 0;
    private static final int REQ_PICK = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
        I18n.load(this);
        initColors();
        setupStatusBar();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SURFACE);
        setContentView(root);
        safe(() -> {
            buildTopBanner();
            homeView = buildHome();
            root.addView(homeView, new LinearLayout.LayoutParams(-1, 0, 1f));
        });
        maybeShowPermIntro();
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            unregisterReceiver(uploadReceiver);
        } catch (Throwable ignored) {}
        try {
            registerReceiver(uploadReceiver, new IntentFilter(UploadService.ACTION_EVENT));
        } catch (Throwable ignored) {}
        // 切主题/退桌面重建后，从服务的静态状态恢复顶部条与上传中标志
        restoreFromState();
    }

    @Override
    protected void onStop() {
        try { unregisterReceiver(uploadReceiver); } catch (Throwable ignored) {}
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (uploadDialog != null) { try { uploadDialog.dismiss(); } catch (Throwable ignored) {} }
        super.onDestroy();
    }

    // 服务上传事件 → 刷新工作台进度 + 顶部条
    private final BroadcastReceiver uploadReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            if (intent == null || !UploadService.ACTION_EVENT.equals(intent.getAction())) return;
            String type = intent.getStringExtra("type");
            if ("progress".equals(type)) applyProgress();
            else if ("done".equals(type)) applyDone();
            else if ("control".equals(type)) post(() -> refreshPauseButton());
            else if ("cancel".equals(type)) post(() -> {
                // 服务已取消任务并移除通知，这里只做兜底复位
                uploading = false;
                backgroundMode = false;
                if (topBanner != null) topBanner.setVisibility(View.GONE);
                refreshStartButton();
            });
        }
    };

    private void applyProgress() {
        final String name = UploadService.State.name, stage = UploadService.State.stage;
        final int pct = UploadService.State.pct;
        post(() -> {
            if (uploadDialog != null && uploadDialog.isShowing()) {
                progressStage.setText(stage);
                progressBar.setProgress(pct);
                progressPct.setText(pct + "%");
            }
            if (UploadService.State.bannerVisible) showTopBanner(name, pct);
        });
    }

    private void applyDone() {
        final int success = UploadService.State.success, total = UploadService.State.total;
        final boolean ok = success > 0;
        post(() -> {
            uploading = false;
            if (bgContinueBtn != null) bgContinueBtn.setVisibility(View.GONE);
            if (pauseBtn != null) pauseBtn.setVisibility(View.GONE);
            if (cancelUploadBtn != null) cancelUploadBtn.setVisibility(View.GONE);
            refreshStartButton();
            if (uploadDialog != null && uploadDialog.isShowing()) {
                progressBar.setProgress(100);
                progressPct.setText("");
                if (ok) {
                    progressStage.setText(L("完成 ", "Done ") + success + "/" + total + (success < total ? L("（部分失败）", " (partial)") : ""));
                    showResults(success, total, new ArrayList<>(UploadService.State.urls), new ArrayList<>(UploadService.State.failed), UploadService.State.node);
                } else {
                    progressStage.setText(L("失败 · 已尝试多个节点", "Failed · multiple nodes tried"));
                    showAllFailed();
                }
            }
            if (UploadService.State.bannerVisible) {
                topBannerBar.setProgress(100);
                if (ok) {
                    topBannerName.setText(L("完成 ", "Done ") + success + "/" + total + L(" · 点击查看", " · Tap to view"));
                    topBannerPct.setText("100%");
                } else {
                    topBannerName.setText(L("上传失败 · 点击查看", "Upload failed · Tap to view"));
                    topBannerPct.setText("");
                }
            }
            lastUrls.clear(); lastUrls.addAll(UploadService.State.urls);
            lastFailedAll = !ok;
        });
    }

    // Activity 重建后：只要服务里还有未完成任务/完成结果，就恢复顶部条（主题切换不中断上传）
    private void restoreFromState() {
        // 用户主动关闭过顶部条：不再自动恢复显示（但服务里的上传照常进行）
        if (UploadService.State.bannerDismissed) return;
        if (UploadService.State.active && !UploadService.State.done) {
            uploading = true;
            refreshStartButton();
            UploadService.State.bannerVisible = true; // 工作台弹窗随重建消失，统一用顶部条继续展示
            showTopBanner(UploadService.State.name, UploadService.State.pct);
        } else if (UploadService.State.done) {
            lastUrls.clear(); lastUrls.addAll(UploadService.State.urls);
            lastFailedAll = UploadService.State.failedAll;
            if (UploadService.State.bannerVisible) {
                if (UploadService.State.success > 0)
                    showTopBanner(L("完成 ", "Done ") + UploadService.State.success + "/" + UploadService.State.total + L(" · 点击查看", " · Tap to view"), 100);
                else
                    showTopBanner(L("上传失败 · 点击查看", "Upload failed · Tap to view"), 0);
            }
        }
    }

    // ============ 顶部常驻进度条（后台继续时显示）============
    private void buildTopBanner() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(14);
        b.setPadding(pad, dp(12), pad, dp(12));
        GradientDrawable gd = bg(PRIMARY_CONTAINER, 18);
        gd.setStroke(1, withAlpha(PRIMARY, 40));
        b.setBackground(gd);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        topBannerName = new TextView(this);
        topBannerName.setTextSize(13);
        topBannerName.setTypeface(topBannerName.getTypeface(), Typeface.BOLD);
        topBannerName.setTextColor(ON_PRIMARY_CONTAINER);
        topBannerName.setSingleLine(true);
        topBannerName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(topBannerName, lp(0, -2, 1f));
        topBannerPct = new TextView(this);
        topBannerPct.setTextSize(13);
        topBannerPct.setTypeface(topBannerPct.getTypeface(), Typeface.BOLD);
        topBannerPct.setTextColor(ON_PRIMARY_CONTAINER);
        row.addView(topBannerPct, lp(-2, -2));
        TextView close = new TextView(this);
        close.setText("✕"); close.setTextSize(14); close.setTextColor(ON_PRIMARY_CONTAINER);
        close.setPadding(dp(12), 0, 0, 0);
        close.setOnClickListener(click(this::dismissTopBanner));
        row.addView(close, lp(-2, -2));
        b.addView(row, lp(-1, -2));

        topBannerBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        topBannerBar.setMax(100);
        b.addView(topBannerBar, lp(-1, dp(8), 0, dp(10), 0, 0));

        b.setOnClickListener(click(this::onTopBannerClick));
        b.setVisibility(View.GONE);
        topBanner = b;
        root.addView(topBanner, lp(-1, -2));
    }

    private void showTopBanner(String name, int pct) {
        if (topBanner == null) return;
        topBanner.setVisibility(View.VISIBLE);
        topBannerName.setText(name);
        topBannerPct.setText(pct + "%");
        topBannerBar.setProgress(pct);
    }

    private void dismissTopBanner() {
        // 顶部条叉叉 = 取消上传：通知服务终止任务并移除通知，同时复位本地 UI
        cancelUpload();
    }

    /** 取消当前上传：发指令给服务（终止任务 + 移除通知），并复位界面状态 */
    private void cancelUpload() {
        if (UploadService.State.active && !UploadService.State.done) {
            Intent svc = new Intent(this, UploadService.class);
            svc.setAction(UploadService.ACTION_CANCEL);
            try { startService(svc); } catch (Throwable ignored) {}
        }
        backgroundMode = false;
        uploading = false;
        UploadService.State.active = false;
        UploadService.State.done = true;
        UploadService.State.pause = false;
        UploadService.State.cancel = true;
        UploadService.State.bannerDismissed = true;
        UploadService.State.bannerVisible = false;
        lastUrls.clear();
        lastFailedAll = false;
        if (topBanner != null) topBanner.setVisibility(View.GONE);
        // 弹窗若在显示，复位到可重新选择的状态
        if (uploadDialog != null && uploadDialog.isShowing()) {
            if (pauseBtn != null) pauseBtn.setVisibility(View.GONE);
            if (cancelUploadBtn != null) cancelUploadBtn.setVisibility(View.GONE);
            if (bgContinueBtn != null) bgContinueBtn.setVisibility(View.GONE);
            if (progressBar != null) progressBar.setProgress(0);
            if (progressPct != null) progressPct.setText("");
            if (progressStage != null) progressStage.setText("");
            if (resultBox != null) resultBox.setVisibility(View.GONE);
        }
        refreshStartButton();
        toast(L("已取消上传", "Upload cancelled"));
    }

    private void onTopBannerClick() {
        if (uploading) { toast(L("正在上传，请稍候", "Uploading, please wait")); return; }
        if (lastFailedAll) { toast(L("上次上传失败，请重新选择文件", "Last upload failed, please select files again")); return; }
        if (lastUrls.isEmpty()) { toast(L("暂无可查看的直链", "No links to view")); return; }
        showLastResultSheet();
    }

    // ---------------- 颜色 / 主题 ----------------
    private boolean isDark() {
        int m = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return m == Configuration.UI_MODE_NIGHT_YES;
    }

    private void initColors() {
        if (isDark()) {
            PRIMARY = 0xFFD0BCFF; ON_PRIMARY = 0xFF381E72;
            PRIMARY_CONTAINER = 0xFF4F378B; ON_PRIMARY_CONTAINER = 0xFFEADDFF;
            SECONDARY_CONTAINER = 0xFF4A4458; ON_SECONDARY_CONTAINER = 0xFFE8DEF8;
            TERTIARY_CONTAINER = 0xFF633B48; ON_TERTIARY_CONTAINER = 0xFFFFD8E4;
            SURFACE = 0xFF141218; SURFACE_LOW = 0xFF1E1B23; SURFACE_HIGH = 0xFF2B2930;
            ON_SURFACE = 0xFFE6E1E5; ON_SURFACE_VARIANT = 0xFFCAC4D0;
            OUTLINE = 0xFF938F99; ERROR = 0xFFF2B8B5;
        } else {
            PRIMARY = 0xFF6750A4; ON_PRIMARY = 0xFFFFFFFF;
            PRIMARY_CONTAINER = 0xFFEADDFF; ON_PRIMARY_CONTAINER = 0xFF21005D;
            SECONDARY_CONTAINER = 0xFFE8DEF8; ON_SECONDARY_CONTAINER = 0xFF1D192B;
            TERTIARY_CONTAINER = 0xFFFFD8E4; ON_TERTIARY_CONTAINER = 0xFF31111D;
            SURFACE = 0xFFFFFFFF; SURFACE_LOW = 0xFFF6F3FA; SURFACE_HIGH = 0xFFECE6F0;
            ON_SURFACE = 0xFF1D1B20; ON_SURFACE_VARIANT = 0xFF49454F;
            OUTLINE = 0xFF79747E; ERROR = 0xFFB3261E;
        }
    }

    private void setupStatusBar() {
        try {
            Window w = getWindow();
            w.setStatusBarColor(SURFACE);
            w.setNavigationBarColor(SURFACE);
            View decor = w.getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (isDark()) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            decor.setSystemUiVisibility(flags);
        } catch (Throwable ignored) {}
    }

    private void applyTheme() {
        String t = Prefs.theme(this);
        if ("light".equals(t)) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        else if ("dark".equals(t)) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    // ---------- 异步/点击统一兜底：任何异常都不闪退 ----------
    private void safe(Runnable r) {
        try { r.run(); }
        catch (Throwable t) { Log.e(TAG, "safe catch", t); toast(L("操作未成功，请重试", "Action failed, please retry")); }
    }

    private void post(Runnable r) { main.post(() -> safe(r)); }

    private void safeBg(Runnable r) {
        try { r.run(); }
        catch (Throwable t) { Log.e(TAG, "bg catch", t); post(() -> toast(L("处理出错，请重试", "Error, please retry"))); }
    }

    private View.OnClickListener click(Runnable r) { return v -> safe(r); }

    // 双语文案：中文在前，英文在后
    private static String L(String cn, String en) { return I18n.t(cn, en); }
    private static String zhBtn() { return "中文"; }
    private static String enBtn() { return "English"; }

    private void toast(String s) {
        // 不显示任何提示
    }

    // ==================== 首页 ====================
    private View buildHome() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(SURFACE);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(SURFACE);

        // 顶栏：闪电品牌 + LinkForge + 右上纯色圆主题按钮
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(24), dp(20), dp(24), dp(8));
        ImageView logo = new ImageView(this);
        logo.setImageDrawable(icon("flash", PRIMARY));
        bar.addView(logo, lp(dp(24), dp(24), Gravity.CENTER_VERTICAL));
        TextView brand = new TextView(this);
        brand.setText("LinkForge"); brand.setTextSize(21);
        brand.setTypeface(brand.getTypeface(), Typeface.BOLD); brand.setTextColor(ON_SURFACE);
        brand.setPadding(dp(9), 0, 0, 0);
        bar.addView(brand, lp(0, -2, 1f));
        View themeBtn = new View(this);
        themeBtn.setBackground(bg(PRIMARY, 999));
        bar.addView(themeBtn, lp(dp(26), dp(26)));
        themeBtn.setOnClickListener(click(() -> {
            Prefs.setTheme(this, isDark() ? "light" : "dark");
            applyTheme();
            recreate();
        }));
        page.addView(bar, lp(-1, -2));

        // 胶囊标签（矢量星标，无 emoji）
        LinearLayout kicker = new LinearLayout(this);
        kicker.setOrientation(LinearLayout.HORIZONTAL);
        kicker.setGravity(Gravity.CENTER_VERTICAL);
        kicker.setBackground(bg(PRIMARY_CONTAINER, 999));
        kicker.setPadding(dp(14), dp(7), dp(16), dp(7));
        ImageView kickerIco = new ImageView(this);
        kickerIco.setImageDrawable(icon("star", PRIMARY));
        kicker.addView(kickerIco, lp(dp(14), dp(14), Gravity.CENTER_VERTICAL));
        TextView kickerTxt = new TextView(this);
        kickerTxt.setText(L("全网 · 免登 · 极速", "All Networks · No Login · Fast"));
        kickerTxt.setTextSize(12); kickerTxt.setTextColor(PRIMARY);
        kickerTxt.setTypeface(kickerTxt.getTypeface(), Typeface.BOLD);
        kickerTxt.setLetterSpacing(0.05f);
        kickerTxt.setPadding(dp(7), 0, 0, 0);
        kicker.addView(kickerTxt, lp(-2, -2, Gravity.CENTER_VERTICAL));
        page.addView(kicker, lp(-2, -2, Gravity.CENTER_HORIZONTAL, 0, 22, 0, 0));

        // 大标题 + 副标题
        TextView title = new TextView(this);
        title.setText(L("一键生成永久直链", "Generate Permanent Links in One Tap"));
        title.setTextSize(26); title.setGravity(Gravity.CENTER); title.setTextColor(ON_SURFACE);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        page.addView(title, lp(-2, -2, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 4));

        TextView sub = new TextView(this);
        sub.setText(L("并发探测 50+ 全球免费分发节点，毫秒级锁定最快链路，让文件分享从未如此简单。",
                "Probes 50+ free nodes concurrently, locks onto the fastest route in milliseconds — file sharing made simple."));
        sub.setTextSize(13); sub.setGravity(Gravity.CENTER); sub.setTextColor(ON_SURFACE_VARIANT);
        sub.setLineSpacing(0, 1.42f);
        page.addView(sub, lp(-2, -2, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 22));

        // 主卡片：淡紫渐变 + 深紫实心圆火箭，整卡可点
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL); hero.setGravity(Gravity.CENTER);
        int[] grad = isDark()
                ? new int[]{0xFF3A2E52, 0xFF2C2340}
                : new int[]{0xFFF2EBFD, 0xFFE3D3F7};
        GradientDrawable heroBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, grad);
        heroBg.setCornerRadius(dp(28));
        hero.setBackground(heroBg);
        hero.setPadding(dp(20), dp(36), dp(20), dp(34));
        hero.setClickable(true);
        hero.setOnClickListener(click(this::openUpload));

        ImageView heroIco = new ImageView(this);
        heroIco.setImageDrawable(icon("rocket", ON_PRIMARY));
        heroIco.setBackground(bg(PRIMARY, 999));
        heroIco.setPadding(dp(22), dp(22), dp(22), dp(22));
        hero.addView(heroIco, lp(dp(88), dp(88), Gravity.CENTER));

        TextView heroTitle = new TextView(this);
        heroTitle.setText(L("生成直链", "Generate Link"));
        heroTitle.setTextSize(19); heroTitle.setGravity(Gravity.CENTER);
        heroTitle.setTypeface(heroTitle.getTypeface(), Typeface.BOLD);
        heroTitle.setTextColor(ON_SURFACE);
        hero.addView(heroTitle, lp(-2, -2, Gravity.CENTER, 0, 20, 0, 0));

        TextView heroHint = new TextView(this);
        heroHint.setText(L("点击选择文件 · 自动优选节点 · 即刻出链",
                "Select files · Auto best node · Instant links"));
        heroHint.setTextSize(12); heroHint.setGravity(Gravity.CENTER);
        heroHint.setTextColor(ON_SURFACE_VARIANT);
        hero.addView(heroHint, lp(-2, -2, Gravity.CENTER, 0, 7, 0, 0));

        page.addView(hero, lp(-1, -2, dp(20), dp(22), dp(20), dp(16)));

        // ===== 语言选择分栏 =====
        LinearLayout langSec = new LinearLayout(this);
        langSec.setOrientation(LinearLayout.VERTICAL);
        langSec.setBackground(bg(blockCardColor(), 20));
        langSec.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout langHead = new LinearLayout(this);
        langHead.setOrientation(LinearLayout.HORIZONTAL);
        langHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView langTitle = new TextView(this);
        langTitle.setText(L("语言", "Language"));
        langTitle.setTextSize(16);
        langTitle.setTypeface(langTitle.getTypeface(), Typeface.BOLD);
        langTitle.setTextColor(ON_SURFACE);
        langHead.addView(langTitle, lp(0, -2, 1f));
        TextView langEn = new TextView(this);
        langEn.setText("LANGUAGE"); langEn.setTextSize(10);
        langEn.setLetterSpacing(0.1f); langEn.setTextColor(OUTLINE);
        langHead.addView(langEn, lp(-2, -2));
        langSec.addView(langHead, lp(-1, -2));
        LinearLayout langBtns = new LinearLayout(this);
        langBtns.setOrientation(LinearLayout.HORIZONTAL);
        langBtns.setGravity(Gravity.CENTER_VERTICAL);
        langBtns.setPadding(0, dp(14), 0, 0); // 上边距放到容器，按钮自身不再留 margin
        langBtns.setClipChildren(false);      // 允许按钮阴影/圆角完整绘制
        boolean isZh = !I18n.english;
        Button btnZh = langButton(zhBtn(), isZh);
        btnZh.setOnClickListener(click(() -> switchLanguage("zh")));
        Button btnEn = langButton(enBtn(), !isZh);
        btnEn.setOnClickListener(click(() -> switchLanguage("en")));
        langBtns.addView(btnZh, lp(0, dp(46), 1f, 0, 0, dp(6), 0));
        langBtns.addView(btnEn, lp(0, dp(46), 1f, dp(6), 0, 0, 0));
        langSec.addView(langBtns, lp(-1, dp(60))); // 固定高度 60 = 14 上边距 + 46 按钮
        page.addView(langSec, lp(-1, -2, dp(20), dp(6), dp(20), dp(14)));

        // 核心能力标题行
        LinearLayout secRow = new LinearLayout(this);
        secRow.setOrientation(LinearLayout.HORIZONTAL);
        secRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView secTitle = new TextView(this);
        secTitle.setText(L("核心能力", "Core Features"));
        secTitle.setTextSize(17);
        secTitle.setTypeface(secTitle.getTypeface(), Typeface.BOLD); secTitle.setTextColor(ON_SURFACE);
        secRow.addView(secTitle, lp(0, -2, 1f));
        TextView secEn = new TextView(this);
        secEn.setText("WHY LINKFORGE"); secEn.setTextSize(10);
        secEn.setLetterSpacing(0.1f); secEn.setTextColor(OUTLINE);
        secRow.addView(secEn, lp(-2, -2));
        page.addView(secRow, lp(-1, -2, dp(20), dp(6), dp(20), dp(12)));

        page.addView(featureCard("cloud", L("大容量分发", "Large-Scale Distribution"),
                L("内置 50+ 公有分发节点，单文件与批量分发游刃有余，突破单一平台的容量与限速瓶颈。",
                        "50+ built-in public nodes handle single files and batch distribution with ease, breaking past platform size and speed limits."),
                SECONDARY_CONTAINER, PRIMARY), lp(-1, -2, dp(20), 0, dp(20), dp(10)));
        page.addView(featureCard("trend", L("高速多并发", "High-Speed Concurrency"),
                L("后台静默并发探测节点延迟，自动锁定当前网络下的最优链路，上传下载双倍加速。",
                        "Silently probes node latency in parallel and locks onto the best route for your network, doubling upload and download speed."),
                TERTIARY_CONTAINER, ON_TERTIARY_CONTAINER), lp(-1, -2, dp(20), 0, dp(20), dp(10)));
        page.addView(featureCard("shield", L("链路防失效", "Fail-Safe Routes"),
                L("智能兜底回退机制，节点异常自动切换备用源；合规授权凭证本地透明存储，随时可撤销。",
                        "Smart fallback switches to backup sources when a node fails; authorized credentials are stored locally and can be revoked anytime."),
                PRIMARY_CONTAINER, PRIMARY), lp(-1, -2, dp(20), 0, dp(20), dp(10)));

        TextView foot = new TextView(this);
        foot.setText(L("LinkForge · 直链由第三方节点提供", "LinkForge · Links provided by third-party nodes"));
        foot.setTextSize(11); foot.setGravity(Gravity.CENTER); foot.setTextColor(OUTLINE);
        foot.setPadding(0, dp(18), 0, dp(28));
        page.addView(foot, lp(-1, -2));

        sv.addView(page, lp(-1, -1));
        return sv;
    }

    private void switchLanguage(String lang) {
        Prefs.setLanguage(this, lang);
        I18n.load(this);
        // 语言切换重建 Activity；上传在服务中不受影响，切回后由 restoreFromState 恢复展示
        recreate();
    }

    private int blockCardColor() { return isDark() ? SURFACE_HIGH : 0xFFF1EAF6; }

    private Button langButton(String text, boolean selected) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14f);
        b.setAllCaps(false);
        // 关键修复：固定高度 + 显式居中，避免 wrap_content 下按钮被测量过矮、下半部分被裁切
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(0, 0, 0, 0);
        b.setGravity(Gravity.CENTER);
        b.setStateListAnimator(null); // 去掉默认抬升阴影，防止绘制超出 clip 边界
        if (selected) {
            b.setTextColor(ON_PRIMARY);
            b.setBackground(bg(PRIMARY, 24)); // 24 = 高度46的一半，标准胶囊圆角
            b.setAlpha(1f);
        } else {
            b.setTextColor(ON_SURFACE_VARIANT);
            b.setBackground(bg(SURFACE, 24));
            b.setAlpha(0.9f);
        }
        return b;
    }

    private TextView chip(String text) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(11);
        t.setTextColor(PRIMARY); t.setBackground(bg(PRIMARY_CONTAINER, 999));
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        return t;
    }

    private View featureCard(String iconKind, String title, String desc, int bgColor, int fgColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL); // 图标与文字整体垂直居中，不再上下错位
        // 能力卡：与背景相近的浅灰圆角块，文字直接刻在块上（无描边）
        int block = isDark() ? SURFACE_HIGH : 0xFFF1EAF6;
        card.setBackground(bg(block, 20));
        card.setPadding(dp(14), dp(15), dp(14), dp(15));
        ImageView ico = new ImageView(this);
        ico.setImageDrawable(icon(iconKind, fgColor));
        ico.setBackground(bg(bgColor, 15));
        ico.setPadding(dp(11), dp(11), dp(11), dp(11));
        card.addView(ico, lp(dp(46), dp(46), Gravity.CENTER_VERTICAL));
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setGravity(Gravity.CENTER_VERTICAL); // 文字块自身也垂直居中
        TextView t = new TextView(this); t.setText(title); t.setTextSize(14);
        t.setTypeface(t.getTypeface(), Typeface.BOLD); t.setTextColor(ON_SURFACE);
        TextView d = new TextView(this); d.setText(desc); d.setTextSize(12);
        d.setTextColor(ON_SURFACE_VARIANT); d.setLineSpacing(0, 1.3f);
        txt.addView(t); txt.addView(d, lp(-2, -2, 0, 4, 0, 0));
        // 图标右侧留一点距离；card 已设 CENTER_VERTICAL，文字块随之垂直居中，去掉原先的 top margin
        card.addView(txt, lp(0, -2, 1f, dp(12), 0, 0, 0));
        return card;
    }

    // ==================== 上传工作台 ====================
    private void openUpload() {
        // 单实例限制：弹窗已在显示时直接忽略，避免手速快连点叠加出两个窗口
        if (uploadDialog != null && uploadDialog.isShowing()) return;
        uploadSeq++; // 作废上一次窗口遗留的所有后台回调
        backgroundMode = false;
        if (topBanner != null) topBanner.setVisibility(View.GONE);
        pickedUris.clear(); pickedNames.clear(); pickedSizes.clear();
        resultMap.clear(); spinnerNodes.clear(); uploading = false; selectedPos = 0;

        View v = getLayoutInflater().inflate(R.layout.upload_sheet, null);
        dropZone = v.findViewById(R.id.dropZone);
        fileInfoRow = v.findViewById(R.id.fileInfoRow);
        fileInfoText = (TextView) v.findViewById(R.id.fileInfoText);
        View clearBtn = v.findViewById(R.id.clearFile);
        nodeSpinner = (Spinner) v.findViewById(R.id.nodeSpinner);
        progressBar = (ProgressBar) v.findViewById(R.id.progressBar);
        progressStage = (TextView) v.findViewById(R.id.progressStage);
        progressPct = (TextView) v.findViewById(R.id.progressPct);
        resultBox = v.findViewById(R.id.resultBox);
        resultTitle = (TextView) v.findViewById(R.id.resultTitle);
        resultLink = (TextView) v.findViewById(R.id.resultLink);
        resultNodeInfo = (TextView) v.findViewById(R.id.resultNodeInfo);
        singleResult = v.findViewById(R.id.singleResult);
        multiResult = v.findViewById(R.id.multiResult);
        multiList = (LinearLayout) v.findViewById(R.id.multiList);
        startUploadBtn = (Button) v.findViewById(R.id.startUpload);
        bgContinueBtn = (Button) v.findViewById(R.id.bgContinue);
        pauseBtn = (Button) v.findViewById(R.id.pauseBtn);
        cancelUploadBtn = (Button) v.findViewById(R.id.cancelUploadBtn);
        View cancelBtn = v.findViewById(R.id.cancelBtn);
        View copyBtn = v.findViewById(R.id.copyBtn);
        View copyAllBtn = v.findViewById(R.id.copyAllBtn);
        TextView nodeCount = (TextView) v.findViewById(R.id.nodeCount);
        TextView sheetTitle = (TextView) v.findViewById(R.id.sheetTitle);
        TextView dropTitle = (TextView) v.findViewById(R.id.dropTitle);
        TextView dropSub = (TextView) v.findViewById(R.id.dropSub);
        TextView nodeLabel = (TextView) v.findViewById(R.id.nodeLabel);
        ImageView dropIcon = (ImageView) v.findViewById(R.id.dropIcon);
        ImageView fileIcon = (ImageView) v.findViewById(R.id.fileIcon);

        v.setBackgroundColor(SURFACE);
        nodeCount.setTextColor(OUTLINE);
        if (copyBtn instanceof TextView) { ((TextView) copyBtn).setTextColor(ON_PRIMARY); ((TextView) copyBtn).setBackground(bg(PRIMARY_CONTAINER, 999)); }
        if (copyAllBtn instanceof TextView) { ((TextView) copyAllBtn).setTextColor(ON_PRIMARY_CONTAINER); ((TextView) copyAllBtn).setBackground(bg(PRIMARY_CONTAINER, 999)); }
        progressStage.setTextColor(ON_SURFACE_VARIANT);
        progressPct.setTextColor(ON_SURFACE_VARIANT);
        resultTitle.setTextColor(PRIMARY);
        resultLink.setTextColor(ON_SURFACE);
        resultNodeInfo.setTextColor(OUTLINE);
        fileInfoText.setTextColor(ON_SURFACE);
        startUploadBtn.setTextColor(ON_PRIMARY);
        startUploadBtn.setBackground(bg(PRIMARY, 999));
        startUploadBtn.setAllCaps(false);
        bgContinueBtn.setText(L("后台继续", "Continue in Background"));
        bgContinueBtn.setTextSize(14f);
        bgContinueBtn.setTextColor(ON_PRIMARY_CONTAINER);
        bgContinueBtn.setBackground(bg(PRIMARY_CONTAINER, 999));
        bgContinueBtn.setAllCaps(false);
        bgContinueBtn.setPadding(dp(20), dp(10), dp(20), dp(10));
        bgContinueBtn.setVisibility(View.GONE);
        // 暂停 / 取消按钮：默认隐藏，开始上传后显示
        pauseBtn.setVisibility(View.GONE);
        cancelUploadBtn.setVisibility(View.GONE);
        pauseBtn.setTextSize(14f);
        cancelUploadBtn.setTextSize(14f);
        pauseBtn.setAllCaps(false);
        cancelUploadBtn.setAllCaps(false);
        cancelUploadBtn.setTextColor(ERROR);
        cancelUploadBtn.setBackground(bg(withAlpha(ERROR, 24), 999));

        // 双语文案（XML 默认中文，此处按当前语言覆盖）
        sheetTitle.setText(L("生成直链", "Generate Link"));
        dropTitle.setText(L("点击选择文件，或选择多个文件", "Tap to select files, or select multiple"));
        dropSub.setText(L("支持任意格式 · 可多选 · 无大小限制", "Any format · Multi-select · No size limit"));
        nodeLabel.setText(L("上传节点（测速后显示延迟 / 不可达）", "Upload node (latency / unreachable after test)"));
        startUploadBtn.setText(L("开始上传", "Start Upload"));
        refreshPauseButton();
        if (copyBtn instanceof TextView) ((TextView) copyBtn).setText(L("复制", "Copy"));
        if (copyAllBtn instanceof TextView) ((TextView) copyAllBtn).setText(L("复制全部直链", "Copy All Links"));
        // 矢量图标替代 emoji
        dropIcon.setImageDrawable(icon("folder", PRIMARY));
        fileIcon.setImageDrawable(icon("file", PRIMARY));

        nodeCount.setText(Nodes.PUBLIC.size() + L(" 个节点 · 打开即测速", " nodes · testing on open"));

        uploadDialog = new Dialog(this, R.style.SheetDialog);
        uploadDialog.setContentView(v);
        if (uploadDialog.getWindow() != null) {
            Window w = uploadDialog.getWindow();
            // 居中卡片：左右各留 20dp，四周都不贴屏幕边缘，四角圆润
            int margin = dp(20);
            int wPx = getResources().getDisplayMetrics().widthPixels - margin * 2;
            w.setLayout(wPx, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            w.setBackgroundDrawable(bg(SURFACE, 28));
        }
        uploadDialog.setOnDismissListener(d -> {
            // 后台继续模式下弹窗收起但上传任务继续，不能复位 uploading
            if (!backgroundMode) uploading = false;
        });

        dropZone.setOnClickListener(click(this::pickFiles));
        clearBtn.setOnClickListener(click(this::clearPickedFiles));
        startUploadBtn.setOnClickListener(click(this::doUpload));
        bgContinueBtn.setOnClickListener(click(this::backgroundContinue));
        pauseBtn.setOnClickListener(click(this::togglePause));
        cancelUploadBtn.setOnClickListener(click(this::cancelUpload));
        cancelBtn.setOnClickListener(click(() -> uploadDialog.dismiss()));
        copyBtn.setOnClickListener(click(() -> copyText(resultLink.getText().toString())));
        copyAllBtn.setOnClickListener(click(() -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < multiList.getChildCount(); i++) {
                TextView tv = multiList.getChildAt(i).findViewWithTag("url");
                if (tv != null) sb.append(tv.getText()).append('\n');
            }
            copyText(sb.toString().trim());
        }));

        setupSpinner();
        refreshStartButton();
        startSilentProbe();
        uploadDialog.show();
    }

    private void pickFiles() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, L("选择文件", "Select files")), REQ_PICK);
        } catch (Exception e) {
            toast(L("无法打开文件选择器", "Cannot open file picker"));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK) return;
        if (uploadDialog == null || !uploadDialog.isShowing()) return;
        if (resultCode != RESULT_OK || data == null) { post(this::refreshStartButton); return; }
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++)
                uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) { post(this::refreshStartButton); return; }

        final int mySeq = uploadSeq;
        final List<String> names = new ArrayList<>();
        final List<Long> sizes = new ArrayList<>();
        for (Uri u : uris) {
            names.add(Net.displayName(this, u));
            sizes.add(Net.fileSize(this, u));
        }
        post(() -> {
            // 期间用户若清空/重开窗口，代号变化，直接丢弃这份旧结果
            if (mySeq != uploadSeq) return;
            if (uploadDialog == null || !uploadDialog.isShowing()) return;
            pickedUris.clear(); pickedNames.clear(); pickedSizes.clear();
            pickedUris.addAll(uris); pickedNames.addAll(names); pickedSizes.addAll(sizes);
            uploading = false;
            fileInfoRow.setVisibility(View.VISIBLE);
            dropZone.setVisibility(View.GONE);
            long totalSize = 0;
            for (Long s : sizes) if (s != null && s > 0) totalSize += s;
            if (names.size() == 1) {
                String sz = (sizes.get(0) != null && sizes.get(0) > 0) ? "  ·  " + fmtSize(sizes.get(0)) : "";
                fileInfoText.setText(names.get(0) + sz);
            } else {
                fileInfoText.setText(names.get(0) + L(" 等 ", " + ") + names.size() + L(" 个文件", " files")
                        + (totalSize > 0 ? "  ·  " + fmtSize(totalSize) : ""));
            }
            resultBox.setVisibility(View.GONE);
            progressBar.setProgress(0);
            progressStage.setText("");
            progressPct.setText("");
            refreshStartButton();
        });
    }
    private String fmtSize(long b) {
        if (b < 1024) return b + " B";
        if (b < 1048576) return String.format(Locale.ROOT, "%.1f KB", b / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", b / 1048576.0);
    }

    private void clearPickedFiles() {
        uploadSeq++; // 作废所有在途任务（测速/上传），它们的回调会因代号不符被丢弃
        uploading = false;
        pickedUris.clear(); pickedNames.clear(); pickedSizes.clear();
        fileInfoRow.setVisibility(View.GONE);
        dropZone.setVisibility(View.VISIBLE);
        resultBox.setVisibility(View.GONE);
        if (multiList != null) multiList.removeAllViews();
        progressBar.setProgress(0);
        progressStage.setText("");
        progressPct.setText("");
        refreshStartButton();
    }

    private void refreshStartButton() {
        boolean can = !pickedUris.isEmpty() && !uploading;
        if (startUploadBtn != null) {
            startUploadBtn.setEnabled(can);
            startUploadBtn.setAlpha(can ? 1f : 0.5f);
        }
    }

    /** 暂停 / 继续 切换：向服务发控制指令 */
    private void togglePause() {
        if (!UploadService.State.active || UploadService.State.done) return;
        boolean toPause = !UploadService.State.pause;
        Intent svc = new Intent(this, UploadService.class);
        svc.setAction(toPause ? UploadService.ACTION_PAUSE : UploadService.ACTION_RESUME);
        try { startService(svc); } catch (Throwable ignored) {}
        UploadService.State.pause = toPause;
        refreshPauseButton();
        toast(toPause ? L("已暂停上传", "Upload paused") : L("已继续上传", "Upload resumed"));
    }

    /** 按当前暂停状态刷新按钮文案与配色 */
    private void refreshPauseButton() {
        if (pauseBtn == null) return;
        boolean paused = UploadService.State.pause;
        pauseBtn.setText(paused ? L("继续", "Resume") : L("暂停", "Pause"));
        pauseBtn.setTextColor(paused ? ON_PRIMARY : ON_PRIMARY_CONTAINER);
        pauseBtn.setBackground(bg(paused ? PRIMARY : PRIMARY_CONTAINER, 999));
    }

    private void setupSpinner() {
        spinnerNodes.clear();
        spinnerNodes.add(null);
        for (Node n : Nodes.PUBLIC) spinnerNodes.add(n);
        List<String> labels = new ArrayList<>();
        labels.add(L("自动优选（测速中…）", "Auto Select (testing...)"));
        for (Node n : Nodes.PUBLIC) labels.add(n.name + " · " + n.tag);

        spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            @Override public android.view.View getView(int pos, android.view.View cv, ViewGroup parent) {
                TextView tv = (TextView) super.getView(pos, cv, parent);
                tv.setTextColor(ON_SURFACE); return tv;
            }
            @Override public android.view.View getDropDownView(int pos, android.view.View cv, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(pos, cv, parent);
                tv.setTextColor(ON_SURFACE); return tv;
            }
        };
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        nodeSpinner.setAdapter(spinnerAdapter);
        nodeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, android.view.View v, int pos, long id) { selectedPos = pos; }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void startSilentProbe() {
        final int mySeq = uploadSeq;
        pool.execute(() -> safeBg(() -> {
            final List<Net.ProbeResult> results = Collections.synchronizedList(new ArrayList<>());
            List<Thread> ts = new ArrayList<>();
            for (Node n : Nodes.PUBLIC) {
                Thread t = new Thread(() -> results.add(Net.probe(n)));
                ts.add(t); t.start();
            }
            for (Thread t : ts) { try { t.join(); } catch (InterruptedException e) { break; } }
            post(() -> {
                if (mySeq != uploadSeq) return;
                if (uploadDialog == null || !uploadDialog.isShowing()) return;
                resultMap.clear();
                for (Net.ProbeResult r : results) if (r.node != null) resultMap.put(r.node.id, r);
                Node keep = null;
                if (selectedPos > 0 && selectedPos < spinnerNodes.size()) keep = spinnerNodes.get(selectedPos);
                List<Node> sorted = new ArrayList<>(Nodes.PUBLIC);
                Collections.sort(sorted, (a, b) -> {
                    Net.ProbeResult ra = resultMap.get(a.id), rb = resultMap.get(b.id);
                    boolean oka = ra != null && ra.ok, okb = rb != null && rb.ok;
                    if (oka != okb) return oka ? -1 : 1;
                    if (oka) return Long.compare(ra.latency, rb.latency);
                    return 0;
                });
                spinnerNodes.clear(); spinnerNodes.add(null); spinnerNodes.addAll(sorted);
                List<String> labels = new ArrayList<>();
                Net.ProbeResult best = null;
                for (Node n : sorted) { Net.ProbeResult r = resultMap.get(n.id); if (r != null && r.ok) { best = r; break; } }
                labels.add(best != null ? L("自动优选（已锁定 ", "Auto Select (locked on ") + best.node.name + " · " + best.latency + "ms" + L("）", ")") : L("自动优选", "Auto Select"));
                for (Node n : sorted) {
                    Net.ProbeResult r = resultMap.get(n.id);
                    labels.add(n.name + " · " + ((r != null && r.ok) ? r.latency + "ms" : L("不可达", "Unreachable")));
                }
                spinnerAdapter.clear();
                spinnerAdapter.addAll(labels);
                spinnerAdapter.notifyDataSetChanged();
                int newPos = 0;
                if (keep != null) { int idx = spinnerNodes.indexOf(keep); if (idx > 0) newPos = idx; }
                nodeSpinner.setSelection(newPos);
            });
        }));
    }

    private void doUpload() {
        if (pickedUris.isEmpty() || uploading) return;
        uploading = true;
        refreshStartButton();
        resultBox.setVisibility(View.GONE);
        progressBar.setProgress(0);
        progressPct.setText("");
        if (bgContinueBtn != null) bgContinueBtn.setVisibility(View.VISIBLE);
        if (pauseBtn != null) { pauseBtn.setVisibility(View.VISIBLE); refreshPauseButton(); }
        if (cancelUploadBtn != null) cancelUploadBtn.setVisibility(View.VISIBLE);
        UploadService.State.pause = false;
        UploadService.State.cancel = false;
        lastUrls.clear();
        lastFailedAll = false;

        final List<Node> order = new ArrayList<>();
        if (selectedPos == 0) {
            for (Node n : spinnerNodes) {
                if (n == null) continue;
                Net.ProbeResult r = resultMap.get(n.id);
                if (r != null && r.ok) order.add(n);
            }
        } else if (selectedPos > 0 && selectedPos < spinnerNodes.size()) {
            Node chosen = spinnerNodes.get(selectedPos);
            if (chosen != null) order.add(chosen);
        }
        for (Node n : Nodes.PUBLIC) if (!order.contains(n)) order.add(n);

        // 打包为字符串数组传给前台服务（服务内以 Uri 流式读取，不占内存）
        final int n = pickedUris.size();
        String[] uris = new String[n];
        String[] names = new String[n];
        long[] sizes = new long[n];
        for (int i = 0; i < n; i++) {
            uris[i] = pickedUris.get(i).toString();
            names[i] = pickedNames.get(i);
            sizes[i] = (i < pickedSizes.size() && pickedSizes.get(i) != null) ? pickedSizes.get(i) : -1L;
        }
        String[] orderIds = new String[order.size()];
        for (int i = 0; i < order.size(); i++) orderIds[i] = order.get(i).id;
        final long stamp = System.nanoTime();

        // 先在本地置位，服务启动后广播事件会驱动 UI
        UploadService.State.active = true;
        UploadService.State.done = false;
        UploadService.State.bannerVisible = false;
        UploadService.State.bannerDismissed = false;
        UploadService.State.stamp = stamp;
        UploadService.State.pct = 0;
        UploadService.State.success = 0;
        UploadService.State.failedAll = false;
        UploadService.State.name = n > 0 ? names[0] : "文件";
        UploadService.State.urls.clear();
        UploadService.State.failed.clear();
        progressStage.setText(names[0]);

        Intent svc = new Intent(this, UploadService.class);
        svc.putExtra("uris", uris);
        svc.putExtra("names", names);
        svc.putExtra("sizes", sizes);
        svc.putExtra("order", orderIds);
        svc.putExtra("stamp", stamp);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
            else startService(svc);
        } catch (Throwable t) {
            Log.e(TAG, "start service failed", t);
            uploading = false;
            UploadService.State.active = false;
            refreshStartButton();
        }
    }

    // 点「后台继续」：收起工作台，进度常驻到顶部
    private void backgroundContinue() {
        if (!uploading) return;
        backgroundMode = true;
        UploadService.State.bannerVisible = true;
        String cur = progressStage.getText().toString();
        if (cur.isEmpty()) cur = UploadService.State.name;
        if (cur.isEmpty()) cur = "准备上传…";
        showTopBanner(cur, progressBar.getProgress());
        if (uploadDialog != null && uploadDialog.isShowing()) uploadDialog.dismiss();
    }

    // ==================== 首次权限引导 ====================
    private void maybeShowPermIntro() {
        if (Prefs.permIntroDone(this)) return;
        View v = getLayoutInflater().inflate(R.layout.perm_sheet, null);
        TextView permTitle = (TextView) v.findViewById(R.id.permTitle);
        TextView msg = (TextView) v.findViewById(R.id.permMsg);
        Button ok = (Button) v.findViewById(R.id.permOk);
        permTitle.setText(L("权限说明", "Permissions"));
        permTitle.setTextColor(ON_SURFACE);
        msg.setText(L("为保证软件能够正常使用，请先同意通知权限并关闭电池优化。\n\n通知权限用于在上传时于通知栏显示上传状态；关闭电池优化可避免系统在退到后台后中断上传。",
                "For the app to work properly, please grant notification permission and disable battery optimization.\n\nNotification permission is used to show upload status in the notification bar; disabling battery optimization prevents the system from stopping uploads in the background."));
        ok.setText(L("同意并继续", "Agree & Continue"));
        msg.setTextColor(ON_SURFACE_VARIANT);
        ok.setTextColor(ON_PRIMARY);
        ok.setBackground(bg(PRIMARY, 999));
        ok.setAllCaps(false);
        Dialog d = new Dialog(this, R.style.SheetDialog);
        d.setContentView(v);
        if (d.getWindow() != null) {
            Window w = d.getWindow();
            int margin = dp(24);
            w.setLayout(getResources().getDisplayMetrics().widthPixels - margin * 2,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            w.setBackgroundDrawable(bg(SURFACE, 28));
        }
        d.setCancelable(false);
        ok.setOnClickListener(v1 -> {
            Prefs.setPermIntroDone(this);
            d.dismiss();
            requestRuntimePerms();
        });
        try { d.show(); } catch (Throwable ignored) {}
    }

    private void requestRuntimePerms() {
        // 通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 2001);
                }
            } catch (Throwable ignored) {}
        }
        // 关闭电池优化（忽略电池优化白名单）
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        } catch (Throwable ignored) {}
    }

    // 点顶部条：展示最近一次的上传结果
    private void showLastResultSheet() {
        View v = getLayoutInflater().inflate(R.layout.result_sheet, null);
        TextView title = v.findViewById(R.id.rsTitle);
        LinearLayout list = (LinearLayout) v.findViewById(R.id.rsList);
        Button copyAll = (Button) v.findViewById(R.id.rsCopyAll);
        title.setText(L("上传完成 · 直链列表", "Upload Complete · Links"));
        copyAll.setText(L("复制全部直链", "Copy All Links"));
        title.setTextColor(PRIMARY);
        copyAll.setTextColor(ON_PRIMARY);
        copyAll.setBackground(bg(PRIMARY, 999));
        copyAll.setAllCaps(false);
        list.removeAllViews();
        for (String url : lastUrls) list.addView(resultRow(url));
        Dialog d = new Dialog(this, R.style.SheetDialog);
        d.setContentView(v);
        if (d.getWindow() != null) {
            Window w = d.getWindow();
            int margin = dp(20);
            w.setLayout(getResources().getDisplayMetrics().widthPixels - margin * 2,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            w.setBackgroundDrawable(bg(SURFACE, 28));
        }
        copyAll.setOnClickListener(click(() -> {
            StringBuilder sb = new StringBuilder();
            for (String url : lastUrls) sb.append(url).append('\n');
            copyText(sb.toString().trim());
        }));
        d.show();
    }

    private void showResults(int success, int total, List<String> urls, List<String> failed, String node) {
        resultBox.setVisibility(View.VISIBLE);
        singleResult.setVisibility(success == 1 && failed.isEmpty() ? View.VISIBLE : View.GONE);
        multiResult.setVisibility(!(success == 1 && failed.isEmpty()) ? View.VISIBLE : View.GONE);
        resultTitle.setText(success == 1 && failed.isEmpty() ? L("直链生成成功", "Link Generated") : L("批量完成（", "Batch Complete (") + success + "/" + total + "）");
        resultNodeInfo.setText(L("节点：", "Node: ") + (node == null ? L("多节点", "Multiple") : node) + L(" · 直链由第三方服务提供", " · Links provided by third-party services"));
        if (success == 1 && failed.isEmpty()) {
            resultLink.setText(urls.get(0));
        } else {
            multiList.removeAllViews();
            for (String u : urls) multiList.addView(resultRow(u));
            for (String f : failed) multiList.addView(resultRowFail(f));
        }
        toast(L("直链已生成，可以复制分享啦", "Link generated, ready to copy and share"));
    }

    private View resultRow(String url) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8)); row.setBackground(bg(SURFACE_LOW, 8));
        TextView tv = new TextView(this); tv.setText(url); tv.setTextSize(11);
        tv.setTextColor(ON_SURFACE); tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setTag("url"); tv.setLayoutParams(lp(0, -2, 1f));
        row.addView(tv);
        row.setOnClickListener(click(() -> copyText(url)));
        return row;
    }

    private View resultRowFail(String name) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8)); row.setBackground(bg(SURFACE_LOW, 8));
        TextView tv = new TextView(this);
        tv.setText(name + "  ·  " + L("上传失败", "Upload failed")); tv.setTextSize(11); tv.setTextColor(ERROR);
        row.addView(tv);
        return row;
    }

    private void showAllFailed() {
        resultBox.setVisibility(View.VISIBLE);
        singleResult.setVisibility(View.VISIBLE);
        multiResult.setVisibility(View.GONE);
        resultTitle.setText(L("上传失败", "Upload Failed"));
        resultLink.setText("");
        resultNodeInfo.setText(L("已尝试多个节点均失败。常见原因：当前网络无法访问这些海外免费节点、节点临时维护或被限速。建议切换网络后重试。", "All tried nodes failed. Common causes: this network cannot reach these overseas free nodes, nodes under maintenance or throttled. Try switching networks."));
        toast(L("上传失败：节点均不可达，请检查网络后重试", "Upload failed: no nodes reachable, check network and retry"));
    }

    private void copyText(String s) {
        if (s == null || s.isEmpty()) { toast(L("没有可复制的内容", "Nothing to copy")); return; }
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("link", s));
            toast(L("链接已复制到剪贴板", "Link copied to clipboard"));
        } catch (Throwable t) { toast(L("复制失败", "Copy failed")); }
    }

    // ==================== 矢量图标（纯代码绘制，告别 emoji） ====================
    private Drawable icon(final String kind, final int color) {
        return new Drawable() {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            {
                p.setColor(color);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2f);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
            }
            @Override public void draw(Canvas c) {
                android.graphics.Rect b = getBounds();
                c.save();
                c.translate(b.left, b.top);
                c.scale(b.width() / 24f, b.height() / 24f);
                Path path = new Path();
                switch (kind) {
                    case "bolt":
                        path.moveTo(13, 2); path.lineTo(5, 13); path.lineTo(11, 13);
                        path.lineTo(10, 22); path.lineTo(19, 10); path.lineTo(13, 10);
                        path.lineTo(15, 3); path.close(); c.drawPath(path, p);
                        break;
                    case "cloud":
                        path.moveTo(6.5f, 18); path.lineTo(17.5f, 18);
                        path.quadTo(21, 18, 21, 14.5f); path.quadTo(21, 11, 17.5f, 11);
                        path.quadTo(16, 6, 11.5f, 6.5f); path.quadTo(7, 7, 6.5f, 11);
                        path.quadTo(3, 11.5f, 3.5f, 15); path.quadTo(4, 18, 6.5f, 18);
                        c.drawPath(path, p);
                        break;
                    case "shield":
                        path.moveTo(12, 2.5f); path.lineTo(20, 5.5f); path.lineTo(20, 11);
                        path.quadTo(20, 17, 12, 21.5f); path.quadTo(4, 17, 4, 11);
                        path.lineTo(4, 5.5f); path.close(); c.drawPath(path, p);
                        break;
                    case "upload":
                        path.moveTo(12, 16); path.lineTo(12, 4);
                        path.moveTo(7, 9); path.lineTo(12, 4); path.lineTo(17, 9);
                        path.moveTo(4, 20); path.lineTo(20, 20);
                        c.drawPath(path, p);
                        break;
                    case "sun":
                        c.drawCircle(12, 12, 4, p);
                        for (int i = 0; i < 8; i++) {
                            double a = i * Math.PI / 4;
                            c.drawLine((float) (12 + 6.2 * Math.cos(a)), (float) (12 + 6.2 * Math.sin(a)),
                                    (float) (12 + 9 * Math.cos(a)), (float) (12 + 9 * Math.sin(a)), p);
                        }
                        break;
                    case "moon":
                        path.moveTo(20, 14.5f);
                        path.quadTo(14, 21, 8, 16.5f);
                        path.quadTo(3.5f, 12, 9, 6);
                        path.quadTo(10, 12, 17, 10.5f);
                        path.quadTo(20, 10.5f, 20, 14.5f); path.close();
                        c.drawPath(path, p);
                        break;
                    case "flash": { // 实心闪电（顶栏品牌图标）
                        p.setStyle(Paint.Style.FILL);
                        path.moveTo(13.5f, 2); path.lineTo(5, 13.5f); path.lineTo(11, 13.5f);
                        path.lineTo(10, 22); path.lineTo(19.5f, 10); path.lineTo(13, 10);
                        path.lineTo(15.5f, 2); path.close(); c.drawPath(path, p);
                        break;
                    }
                    case "rocket": { // 标准 Material 风格火箭：线条等粗、左右对称、重心居中、圆角统一
                        // 机身（对称流线型，顶部圆弧收尖，底部平齐）
                        path.moveTo(12, 2);
                        path.cubicTo(15.6f, 5f, 16.2f, 9.6f, 15.2f, 13.5f);
                        path.lineTo(8.8f, 13.5f);
                        path.cubicTo(7.8f, 9.6f, 8.4f, 5f, 12, 2);
                        path.close();
                        c.drawPath(path, p);
                        // 舷窗（正圆，居中偏上，与机身留白协调）
                        c.drawCircle(12, 8.6f, 2.2f, p);
                        // 左尾翼（对称小三角，收在机身下段）
                        path.reset();
                        path.moveTo(8.8f, 10.8f);
                        path.lineTo(5.6f, 15f);
                        path.lineTo(8.8f, 14.2f);
                        path.close();
                        c.drawPath(path, p);
                        // 右尾翼
                        path.reset();
                        path.moveTo(15.2f, 10.8f);
                        path.lineTo(18.4f, 15f);
                        path.lineTo(15.2f, 14.2f);
                        path.close();
                        c.drawPath(path, p);
                        // 底部喷口（短横线，稳住重心不过重）
                        path.reset();
                        path.moveTo(10.4f, 16.2f);
                        path.lineTo(13.6f, 16.2f);
                        c.drawPath(path, p);
                        // 中心尾焰（一段短直线，圆头）
                        path.reset();
                        path.moveTo(12, 16.2f);
                        path.lineTo(12, 19.5f);
                        c.drawPath(path, p);
                        break;
                    }
                    case "star": { // 四角星（胶囊标签，替代 ✦）
                        path.moveTo(12, 3);
                        path.quadTo(12.6f, 10.5f, 20, 12);
                        path.quadTo(12.6f, 13.5f, 12, 21);
                        path.quadTo(11.4f, 13.5f, 4, 12);
                        path.quadTo(11.4f, 10.5f, 12, 3);
                        path.close();
                        c.drawPath(path, p);
                        break;
                    }
                    case "folder": { // 平面文件夹图形（替代 📁）
                        path.moveTo(3, 6.5f);
                        path.lineTo(3, 17.5f);
                        path.quadTo(3, 19.5f, 5, 19.5f);
                        path.lineTo(19, 19.5f);
                        path.quadTo(21, 19.5f, 21, 17.5f);
                        path.lineTo(21, 8.5f);
                        path.quadTo(21, 6.5f, 19, 6.5f);
                        path.lineTo(11, 6.5f);
                        path.lineTo(9, 4.5f);
                        path.lineTo(5, 4.5f);
                        path.quadTo(3, 4.5f, 3, 6.5f);
                        path.close();
                        c.drawPath(path, p);
                        break;
                    }
                    case "file": { // 平面文档图形（替代 📄）
                        path.moveTo(6, 3);
                        path.lineTo(14, 3);
                        path.lineTo(18.5f, 7.5f);
                        path.lineTo(18.5f, 20.5f);
                        path.quadTo(18.5f, 21.5f, 17.5f, 21.5f);
                        path.lineTo(6.5f, 21.5f);
                        path.quadTo(5.5f, 21.5f, 5.5f, 20.5f);
                        path.lineTo(5.5f, 4f);
                        path.quadTo(5.5f, 3, 6, 3);
                        path.close();
                        c.drawPath(path, p);
                        path.reset();
                        path.moveTo(14, 3);
                        path.lineTo(14, 7.5f);
                        path.lineTo(18.5f, 7.5f);
                        c.drawPath(path, p);
                        break;
                    }
                    case "trend": { // 上升趋势箭头（高速多并发）
                        path.moveTo(4, 16); path.cubicTo(9, 16, 10, 10, 14, 10);
                        path.cubicTo(17, 10, 18, 7, 20, 5); c.drawPath(path, p);
                        path.reset();
                        path.moveTo(15, 5); path.lineTo(20, 5); path.lineTo(20, 10);
                        c.drawPath(path, p);
                        break;
                    }
                    default: break;
                }
                c.restore();
            }
            @Override public void setAlpha(int a) { p.setAlpha(a); invalidateSelf(); }
            @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); invalidateSelf(); }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
    }

    // ==================== 工具 ====================
    private int dp(int v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(color); g.setCornerRadius(dp(radius));
        return g;
    }

    private GradientDrawable cardBg() {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(SURFACE_LOW);
        g.setCornerRadius(dp(20));
        g.setStroke(1, withAlpha(OUTLINE, 30));
        return g;
    }

    private int withAlpha(int color, int alpha) { return (alpha << 24) | (color & 0x00FFFFFF); }

    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    private LinearLayout.LayoutParams lp(int w, int h, int gravity) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h); p.gravity = gravity; return p;
    }
    private LinearLayout.LayoutParams lp(int w, int h, int gravity, int ml, int mt, int mr, int mb) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.gravity = gravity; p.setMargins(ml, mt, mr, mb); return p;
    }
    private LinearLayout.LayoutParams lp(int w, int h, int ml, int mt, int mr, int mb) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(ml, mt, mr, mb); return p;
    }
    private LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }
    private LinearLayout.LayoutParams lp(int w, int h, float weight, int ml, int mt, int mr, int mb) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight);
        p.setMargins(ml, mt, mr, mb); return p;
    }
}
