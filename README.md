# 直链生成器 · LinkForge

> 一键把文件生成永久直链的安卓工具。纯原生实现，内置 56 个公共上传节点，开窗即静默测速、自动优选最快链路。

- 版本：**9.0**（versionCode 9）
- 平台：Android 8.0（API 26）及以上
- 语言：**简体中文 / English 双语界面**，可在应用内「语言」分栏一键切换（含通知栏文案）

---

## 目录

- [简体中文](#简体中文)
- [English](#english)

---

# 简体中文

## 简介

直链生成器（LinkForge）是一款运行在安卓设备上的文件上传工具。选择本地文件后，应用会在后台并发探测内置公共节点的可用性，自动锁定当前网络下最快的链路，将文件上传并返回一个可直接访问的公开链接（直链）。

应用为**纯原生 Android 实现**（Java + AndroidX + Material Components），不依赖 WebView，也没有任何 HTML / JavaScript 资源。

## 功能特性

- **一键出链**：选择文件即可生成可分享的直链，支持多文件批量上传。
- **56 个公共节点**：内置多家公共文件托管节点，单文件与批量分发都有覆盖。
- **静默测速 · 自动优选**：打开上传窗口即在后台并发探测各节点延迟，下拉列表可见每个节点的延迟 / 不可达状态，并按快慢排序。
- **大文件支持**：采用流式上传，边读边发、内存占用恒定，几百 MB 乃至更大的文件也不会因内存不足而失败。
- **失败自动兜底**：某节点上传失败时自动切换备用节点，静默完成。
- **前台服务保活**：上传任务运行在前台服务中，切换深浅色主题、切换语言、退到桌面、锁屏都不会中断上传。
- **通知栏常驻**：上传期间在通知栏显示"正在上传 + 文件名"（不含进度），点击通知即可回到应用。
- **暂停 / 继续 / 取消**：开始上传后弹窗左侧出现"暂停"和"取消"按钮；暂停可随时"继续"，取消会立即终止上传并移除通知。
- **后台继续**：点击"后台继续"后，进度以"文件名 + 进度条 + 百分比"的形式常驻在应用顶部；点顶部条右侧的 ✕ 即视为**取消上传**（终止任务并移除通知）。
- **中英双语界面**：首页、上传弹窗、结果、失败提示、权限引导及通知栏文案均可一键切换为英文，语言偏好本地保存。
- **深浅色主题**：支持浅色 / 深色界面切换，主题偏好本地保存。
- **统一矢量图标**：界面图标均为代码绘制的矢量图形，不依赖 emoji，显示效果在不同设备上保持一致。

## 技术栈

| 项目 | 说明 |
| --- | --- |
| 语言 | Java 17 |
| 最低版本 | Android 8.0（API 26） |
| 编译版本 | compileSdk 35 / targetSdk 35 |
| UI | AndroidX AppCompat + Material Components 3 |
| 网络 | 原生 `HttpURLConnection`，流式 multipart / PUT 上传 |
| 保活 | 前台服务（Foreground Service，`dataSync` 类型）+ 常驻通知 |
| 多语言 | `I18n` 文案集中管理，SharedPreferences 保存语言偏好 |
| 存储 | SharedPreferences（主题偏好、语言偏好、权限引导状态） |

## 项目结构

```
LinkForgeNative/
├── app/src/main/java/com/linkforge/app/
│   ├── MainActivity.java      # 界面与交互、进度展示、权限引导、语言切换
│   ├── UploadService.java     # 前台服务：实际执行上传、通知栏、共享状态
│   ├── Net.java               # 节点探测与流式上传
│   ├── Nodes.java             # 公共节点与协议适配
│   ├── I18n.java              # 中英文案集中管理与加载
│   └── Prefs.java             # 本地偏好存储（主题/语言/权限引导）
└── app/src/main/res/          # 布局、颜色、主题、图标等资源
```

## 编译

需要 JDK 17 或更高版本，以及 Android SDK（compileSdk 35）。

```bash
tar -xzf LinkForge-source.tar.gz
cd LinkForgeNative
./gradlew assembleDebug
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`。也可以用 Android Studio 直接打开工程编译。

## 法律边界与免责声明

> **请在使用前仔细阅读以下声明。使用本软件即表示你已理解并同意。**

- 本软件**仅提供一个上传文件的通道 / 工具**，本身不生产、不存储、不审核任何上传内容。
- 文件实际由**第三方公共文件托管节点**接收与存储，本软件与这些节点运营方无隶属、控制或代理关系，也不对其可用性、存储期限、内容或隐私政策承担任何责任。
- **严禁使用本软件上传任何违法违规文件**，包括但不限于：违反法律法规的内容、侵犯他人知识产权或隐私的内容、色情低俗、暴力恐怖、赌博诈骗、虚假信息、恶意软件及其他被禁止传播的内容。
- 你应当确保自己有权上传相关文件，并**自行承担上传行为及其内容产生的一切法律责任与后果**。
- 本软件作者 / 开发者**不对用户上传的任何内容或使用行为承担任何直接或间接责任**，亦不对因使用本软件或第三方节点而导致的任何损失、侵权纠纷或法律追责负责。
- 若相关主管部门或权利方提出要求，使用者应自行停止使用并删除相应内容。

---

# English

> **Note: This application supports both Simplified Chinese and English.** You can switch the entire interface language — including notifications — from the "Language" section on the home screen. The app defaults to Chinese on first launch.

## Overview

LinkForge (直链生成器) is an Android file-upload tool. After you pick a local file, the app silently probes its built-in public nodes in parallel, locks onto the fastest available route for the current network, uploads the file, and returns a directly accessible public link.

The app is implemented **fully natively** in Android (Java + AndroidX + Material Components). It does not use WebView and contains no HTML or JavaScript assets.

> **Language: the interface is available in Simplified Chinese and English.** Use the "中文 / English" buttons in the "Language" section to switch. The preference is saved locally, and notification text follows the selected language.

## Features

- **One-click links**: Turn a selected file into a shareable direct link; supports batch upload of multiple files.
- **56 public nodes**: A range of public file-hosting nodes, covering both single-file and batch distribution.
- **Silent speed test · auto selection**: Opening the upload sheet triggers a background parallel probe of node latency; each node's latency / reachability is shown in the dropdown and sorted fastest-first.
- **Large-file support**: Streaming upload reads and sends data chunk by chunk with constant memory usage, so files of hundreds of MB or more will not fail due to insufficient memory.
- **Automatic fallback**: If one node fails, the app silently switches to a backup node.
- **Foreground service**: Uploads run in a foreground service, so switching themes, switching language, going to the home screen, or locking the device does not interrupt them.
- **Persistent notification**: During upload, the notification bar shows "uploading + file name" (no progress bar); tapping the notification returns to the app.
- **Pause / resume / cancel**: After an upload starts, "Pause" and "Cancel" buttons appear on the left of the sheet; pause can be resumed at any time, and cancel immediately terminates the upload and removes the notification.
- **Continue in background**: Tap "Continue in background" to keep progress pinned at the top of the app as "file name + progress bar + percentage". Tapping the ✕ on the top bar **cancels the upload** (terminates the task and removes the notification).
- **Bilingual interface**: The home screen, upload sheet, results, failure messages, permission guide, and notification text can all be switched to English with one tap; the language preference is saved locally.
- **Light / dark theme**: Switch between light and dark UI; the preference is saved locally.
- **Unified vector icons**: All interface icons are code-drawn vector graphics rather than emoji, for consistent rendering across devices.

## Tech stack

| Item | Details |
| --- | --- |
| Language | Java 17 |
| Minimum version | Android 8.0 (API 26) |
| Compile version | compileSdk 35 / targetSdk 35 |
| UI | AndroidX AppCompat + Material Components 3 |
| Network | Native `HttpURLConnection`, streaming multipart / PUT upload |
| Keep-alive | Foreground Service (`dataSync` type) + persistent notification |
| Localization | Centralized `I18n` strings, language preference saved via SharedPreferences |
| Storage | SharedPreferences (theme, language, permission-intro state) |

## Build

Requires JDK 17 or higher and Android SDK (compileSdk 35).

```bash
tar -xzf LinkForge-source.tar.gz
cd LinkForgeNative
./gradlew assembleDebug
```

The output is at `app/build/outputs/apk/debug/app-debug.apk`. You can also open the project directly in Android Studio.

## Legal boundaries & disclaimer

> **Please read the following carefully before use. By using this software, you acknowledge and agree to it.**

- This software provides **only a channel / tool for uploading files**. It does not produce, store, or review any uploaded content.
- Files are actually received and stored by **third-party public file-hosting nodes**. The author has no affiliation with, control over, or agency relationship with these node operators, and assumes no responsibility for their availability, retention period, content, or privacy policies.
- **You are strictly prohibited from using this software to upload any unlawful or non-compliant files**, including but not limited to content that violates laws or regulations, infringes others' intellectual property or privacy, pornographic or vulgar material, violent or terrorist content, gambling, fraud, false information, malware, and any other prohibited content.
- You must ensure you have the right to upload the relevant files and **bear full legal responsibility and consequences** for your upload actions and their content.
- The author / developer of this software **assumes no direct or indirect liability** for any content uploaded or actions taken by users, nor for any loss, infringement dispute, or legal action arising from use of this software or third-party nodes.
- If requested by relevant authorities or rights holders, you should stop using the software and delete the corresponding content on your own.
