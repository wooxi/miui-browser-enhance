# 小米浏览器增强 (Xiaomi Browser Enhance)

[![LSPosed API](https://img.shields.io/badge/LSPosed-API%20102%20(Modern)-green.svg)](https://github.com/libxposed)
[![Android](https://img.shields.io/badge/Android-14%20~%2017-blue.svg)](https://android.com)
[![Target](https://img.shields.io/badge/Target-MIUIBrowser-orange.svg)](https://mi.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

专为**小米/红米旗舰设备（深度适配小米折叠屏 MIX Fold 5 / 18 Fold）及 HyperOS 系统**打造的小米原版浏览器（MIUIBrowser）现代增强模块。

全面基于官方 **LibXposed (API 102)** 现代规范构建，在 LSPosed 框架中显示为原生活跃状态。

---

## 🌟 核心功能

### 1. 智能来源弹窗拦截 (Smart Popup Guard)
* **精准来源识别**：传统的弹窗拦截常因目标网址跳转而误拦。本模块利用多层反射机制深度穿透 `Tab$MainWebChromeClient`、外部 `Tab` 对象及底层 WebView，**精确获取发起弹窗的「原网页网址（Origin Host）」**。
* **规则灵活配置**：
  * `nopopup:domain`：仅拦截该域名网页主动弹出的新标签页/新窗口。
  * `nopopup:*`：全局禁止任意网页弹出新窗口。
  * `allow:domain`：免检放行规则。
* **焦点保护**：阻止恶意广告弹窗抢夺页面焦点，避免触发 MIUI 原生视频播放器 `is hidden, don't start` 导致的播放中断。

### 2. Chromium 内核级广告免检白名单 (Kernel AdBlock Bypass)
* **底层直通**：直接 Hook 小米浏览器底层 `com.android.webview.chromium.MiuiStatics` JNI 核心接口：
  * `setDisableAdBlockHosts`（通知底层 Chromium 对指定域名彻底免除一切广告过滤）
  * `setDisableAdPrivacyHosts`（对指定域名彻底免除防跟踪与内容阻断）
* **免除误杀**：默认内置并支持动态将 `fourhoi.com`（封面图源）、`surrit.com`（视频流）、`missav.ai` 等加入内核免检池，彻底解决浏览器自带广告引擎误杀视频封面与媒体资源的问题。

### 3. 抑制地址栏热搜与搜索发现
* 自动静默修改 `key_word_config` 中的 `PREF_HOT_WORD` 与 `recommending_search_enter_enabled`，输入网址时不再出现无意义的灰色推荐热搜词，保持地址栏极致干净。

### 4. 屏蔽 OneTrack 行为统计上报
* 深度拦截 `miui.util.OneTrack` 与 `com.miui.onetrack.OneTrack` 的数据采集与上传入口，保护个人隐私，减少后台无效唤醒与耗电。

### 5. 屏蔽应用运营与更新提示弹窗
* 拦截 `com.android.browser.update.UpdateDialog` 弹窗，阻断烦人的更新提醒与运营活动。

### 6. 原生卡片式设置界面 & 实时行为流水
* **自适应现代 UI**：符合 Xiaomi / Material 原生卡片规范，深色模式自适应，支持规则折叠编辑与收起自动保存。
* **跨进程行为日志流**：内置基于 `LogProvider` 的实时行为记录面板，带专属彩色标签徽章：
  * `[弹窗]` 红色徽章
  * `[广告]` 橙色徽章
  * `[白名单]` 青色徽章
  * `[热搜]` 绿色徽章
  * `[统计]` 蓝色徽章
  * `[推广]` 紫色徽章

---

## 🛠️ 编译与打包

本模块支持纯命令行秒级编译（无需臃肿的 Android Studio）：
```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```
编译产物位于根目录：`BrowserEnhance-api102.apk`。

---

## 📄 License
MIT License
