package com.hermes.browserenhance;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Entry implements IXposedHookLoadPackage {

    private static final String PKG = "com.hermes.browserenhance";
    private static final String PREF = "browser_enhance_prefs";

    private static final String GUARD = "(function () {\n  'use strict';\n  if (window.__hermesPopupGuard) return;\n  window.__hermesPopupGuard = true;\n\n  var AD_HOSTS = [\n    'myavlive.com', 'mayzaent.com', 'miyazent.com', 'tsyndicate.com',\n    'snaptrckr.fun', 'mavrtracktor.com', 'propellerads.com', 'juicyads.com',\n    'exoclick.com', 'exosrv.com', 'eas4et.me', 'pl2205.com', 'h64bcvh.com',\n    'popads.net', 'popcash.net', 'adsterra.com', 'hilltopads.net', 'clickadu.com'\n  ];\n  var WRAP_PATHS = ['/pop', '/popup', '/redirect', '/out', '/go', '/jump', '/link', '/open'];\n\n  function dbg(m) {\n    try { console.log('[hermes-guard] ' + m); } catch (e) {}\n  }\n\n  function isAdUrl(abs) {\n    try {\n      var u = new URL(abs, location.href);\n      var p = u.pathname.toLowerCase();\n      for (var i = 0; i < WRAP_PATHS.length; i++) {\n        if (p === WRAP_PATHS[i] || p.indexOf(WRAP_PATHS[i] + '/') === 0) return true;\n      }\n      var h = u.hostname;\n      for (var j = 0; j < AD_HOSTS.length; j++) {\n        if (h.indexOf(AD_HOSTS[j]) !== -1) return true;\n      }\n      return false;\n    } catch (e) { return true; }\n  }\n\n  var fake = {\n    closed: false, opener: null, name: '_blank', parent: null, top: null,\n    location: { href: 'about:blank', replace: function () {}, assign: function () {} },\n    document: { write: function () {}, writeln: function () {}, close: function () {} },\n    close: function () {}, focus: function () {}, blur: function () {}, print: function () {},\n    moveTo: function () {}, moveBy: function () {}, resizeTo: function () {}, resizeBy: function () {},\n    scrollBy: function () {}, scrollTo: function () {},\n    postMessage: function () {},\n    addEventListener: function () {}, removeEventListener: function () {},\n    dispatchEvent: function () { return true; }\n  };\n  fake.parent = fake;\n  fake.top = fake;\n\n  var origOpen = window.open;\n  window.open = function (u, n, f) {\n    var abs = String(u || '');\n    try { abs = new URL(abs, location.href).href; } catch (e) {}\n    if (isAdUrl(abs)) {\n      dbg('blocked -> ' + abs);\n      return fake;\n    }\n    return origOpen.apply(window, arguments);\n  };\n\n  document.addEventListener('click', function (e) {\n    if (e.defaultPrevented) return;\n    var a = e.target;\n    while (a && a.tagName !== 'A') a = a.parentElement;\n    if (!a || !a.target || a.target === '_self') return;\n    if (isAdUrl(a.href || '')) {\n      dbg('click cancel -> ' + a.href);\n      e.preventDefault();\n      e.stopPropagation();\n    }\n  }, true);\n\n  document.addEventListener('submit', function (e) {\n    try {\n      var f = e.target;\n      if (f && f.target && f.target !== '_self' && isAdUrl(f.action || '')) {\n        dbg('submit cancel -> ' + f.action);\n        e.preventDefault();\n        e.stopPropagation();\n      }\n    } catch (err) {}\n  }, true);\n})();\n";

    private static XSharedPreferences prefs;

    private static XSharedPreferences sp() {
        try {
            if (prefs == null) prefs = new XSharedPreferences(PKG, PREF);
            prefs.reload();
        } catch (Throwable t) { }
        return prefs;
    }

    private static boolean on(String key, boolean def) {
        try {
            XSharedPreferences p = sp();
            if (p == null) return def;
            return p.getBoolean(key, def);
        } catch (Throwable t) { return def; }
    }

    private static String[] getRules() {
        try {
            XSharedPreferences p = sp();
            if (p == null) return new String[0];
            String s = p.getString("rules_txt", "");
            if (s == null || s.length() == 0) return new String[0];
            String[] raw = s.split("\n");
            ArrayList<String> out = new ArrayList<String>();
            for (int i = 0; i < raw.length; i++) {
                String r = raw[i].trim();
                if (r.length() > 0 && !r.startsWith("#")) out.add(r);
            }
            return out.toArray(new String[out.size()]);
        } catch (Throwable t) { return new String[0]; }
    }

    private static void record(String tag, String detail) {
        try {
            Context ctx = null;
            try {
                Class<?> at = Class.forName("android.app.ActivityThread");
                Method m = at.getMethod("currentApplication");
                ctx = (Context) m.invoke(null);
            } catch (Throwable t) {}
            if (ctx != null) {
                Bundle b = new Bundle();
                b.putString("detail", detail);
                ctx.getContentResolver().call(Uri.parse("content://com.hermes.browserenhance.log"), "log", tag, b);
            }
        } catch (Throwable t) { }

        try {
            XposedBridge.log("[BrowserEnhance][" + tag + "] " + detail);
        } catch (Throwable t) { }
    }

    private static String extractHost(String url) {
        if (url == null || url.length() == 0) return "";
        try {
            URI u = new URI(url);
            String h = u.getHost();
            return h != null ? h : "";
        } catch (Throwable t) {
            int p = url.indexOf("://");
            if (p >= 0) {
                String rest = url.substring(p + 3);
                int slash = rest.indexOf('/');
                return slash >= 0 ? rest.substring(0, slash) : rest;
            }
            return url;
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.android.browser".equals(lpparam.packageName)) return;

        XposedBridge.log("[BrowserEnhance] active; rules=" + getRules().length);

        // 1) 页面级 Console 日志捕获（捕获页面内部阻止的 window.open 与广告跳转）
        try {
            Class<?> wcc = XposedHelpers.findClass("android.webkit.WebChromeClient", lpparam.classLoader);
            XposedBridge.hookAllMethods(wcc, "onConsoleMessage", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args != null && param.args.length > 0) {
                            Object arg = param.args[0];
                            String msg = null;
                            if (arg instanceof ConsoleMessage) {
                                msg = ((ConsoleMessage) arg).message();
                            } else if (arg != null) {
                                msg = arg.toString();
                            }
                            if (msg != null && msg.startsWith("[hermes-guard]")) {
                                String body = msg.substring(14).trim();
                                if (body.startsWith("blocked ->")) {
                                    record("弹窗", "阻止页面调用 window.open -> " + body.substring(10).trim());
                                } else if (body.startsWith("click cancel ->")) {
                                    record("广告", "取消广告链接点击 -> " + body.substring(15).trim());
                                } else if (body.startsWith("submit cancel ->")) {
                                    record("广告", "取消广告表单提交 -> " + body.substring(16).trim());
                                }
                            }
                        }
                    } catch (Throwable t) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[BrowserEnhance] console hook err: " + t);
        }

        // 2) 底层 onCreateWindow 弹窗拦截（精确比对发出弹窗的「原网页网址」）
        try {
            Class<?> cc = XposedHelpers.findClass("com.android.browser.Tab$MainWebChromeClient", lpparam.classLoader);
            Method[] ms = cc.getDeclaredMethods();
            for (int i = 0; i < ms.length; i++) {
                if (!"onCreateWindow".equals(ms[i].getName())) continue;
                final Method m = ms[i];
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (!on("guard_popup", true)) return;

                            WebView srcView = null;
                            if (param.args != null && param.args.length > 0 && param.args[0] instanceof WebView) {
                                srcView = (WebView) param.args[0];
                            }
                            String srcUrl = srcView != null ? srcView.getUrl() : "";
                            String srcHost = extractHost(srcUrl);

                            String[] rules = getRules();
                            boolean block = false;
                            String hitRule = null;

                            for (int j = 0; j < rules.length; j++) {
                                String r = rules[j];
                                if (r.startsWith("nopopup:")) {
                                    String target = r.substring(8).trim().toLowerCase();
                                    if (target.equals("*")) {
                                        block = true;
                                        hitRule = "nopopup:*";
                                        break;
                                    } else if (target.length() > 0) {
                                        if ((srcHost.length() > 0 && srcHost.toLowerCase().contains(target)) ||
                                            (srcUrl.length() > 0 && srcUrl.toLowerCase().contains(target))) {
                                            block = true;
                                            hitRule = r;
                                            break;
                                        }
                                    }
                                }
                            }

                            if (block) {
                                param.setResult(Boolean.FALSE);
                                String displaySite = srcHost.length() > 0 ? srcHost : (srcUrl.length() > 0 ? srcUrl : "当前网页");
                                record("弹窗", "阻止来自 " + displaySite + " 的新标签页弹窗 (" + hitRule + ")");
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("[BrowserEnhance] onCreateWindow err: " + t);
                        }
                    }
                });
                break;
            }
        } catch (Throwable t) {
            XposedBridge.log("[BrowserEnhance] popup hook err: " + t);
        }

        // 3) OneTrack 行为上报拦截
        if (on("disable_onetrack", true)) {
            try {
                Class<?> c = XposedHelpers.findClassIfExists("miui.util.OneTrack", lpparam.classLoader);
                if (c == null) c = XposedHelpers.findClassIfExists("com.miui.onetrack.OneTrack", lpparam.classLoader);
                if (c != null) {
                    XposedBridge.hookAllMethods(c, "report", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(null);
                            String ev = "";
                            try {
                                if (param.args != null && param.args.length > 0 && param.args[0] != null) {
                                    ev = String.valueOf(param.args[0]);
                                }
                            } catch (Throwable t) {}
                            record("统计", "拦截 OneTrack 行为上报" + (ev.isEmpty() ? "" : ": " + ev));
                        }
                    });
                    XposedBridge.log("[BrowserEnhance] OneTrack.report disabled");
                }
            } catch (Throwable t) { }
        }

        // 4) 运营/更新提示弹窗拦截
        if (on("disable_promo_dialog", true)) {
            try {
                Class<?> c = XposedHelpers.findClassIfExists("com.android.browser.update.UpdateDialog", lpparam.classLoader);
                if (c != null) {
                    XposedBridge.hookAllMethods(c, "show", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(null);
                            record("推广", "拦截应用运营/更新提示弹窗");
                        }
                    });
                }
            } catch (Throwable t) { }
        }

        // 5) 地址栏热搜词与推荐搜索拦截
        if (on("disable_hotword", true)) {
            try {
                XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Context ctx = (Context) param.thisObject;
                            boolean changed = false;
                            android.content.SharedPreferences kw = ctx.getSharedPreferences("key_word_config", 0);
                            if (kw.getInt("PREF_HOT_WORD", 1) != 0) {
                                kw.edit().putInt("PREF_HOT_WORD", 0).apply();
                                changed = true;
                            }
                            android.content.SharedPreferences bp = ctx.getSharedPreferences("com.android.browser_preferences", 0);
                            if (bp.getBoolean("recommending_search_enter_enabled", true)) {
                                bp.edit().putBoolean("recommending_search_enter_enabled", false).apply();
                                changed = true;
                            }
                            if (changed) {
                                record("热搜", "已抑制地址栏推荐热词与搜索发现");
                            }
                        } catch (Throwable t) { }
                    }
                });
            } catch (Throwable t) { }
        }
    }
}
