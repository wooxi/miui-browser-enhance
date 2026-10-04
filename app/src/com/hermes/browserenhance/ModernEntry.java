package com.hermes.browserenhance;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.ConsoleMessage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class ModernEntry extends XposedModule {

    private static final String PREF = "browser_enhance_prefs";

    public ModernEntry() {
        super();
    }

    private static Context getAppContext() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Method m = at.getMethod("currentApplication");
            return (Context) m.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Context resolveContext(Object obj) {
        if (obj instanceof Context) return (Context) obj;
        if (obj instanceof android.view.View) return ((android.view.View) obj).getContext();
        return getAppContext();
    }

    private static class Config {
        long timestamp = 0;
        String[] rules = new String[] { "nopopup:missav", "allow:fourhoi.com", "allow:surrit.com", "allow:missav.ai" };
        boolean guardPopup = true;
        boolean disableOnetrack = true;
        boolean disablePromoDialog = true;
        boolean disableHotword = true;
    }

    private static final Config cachedConfig = new Config();

    private static synchronized Config getConfig(Context ctx) {
        long now = System.currentTimeMillis();
        if (now - cachedConfig.timestamp < 2000L && cachedConfig.timestamp > 0) {
            return cachedConfig;
        }
        if (ctx == null) ctx = getAppContext();
        if (ctx != null) {
            try {
                Bundle res = ctx.getContentResolver().call(
                    Uri.parse("content://com.hermes.browserenhance.log"),
                    "get_config",
                    null,
                    null
                );
                if (res != null && res.getBoolean("ok", false)) {
                    String s = res.getString("rules_txt", "");
                    if (s != null && s.length() > 0) {
                        String[] raw = s.split("\n");
                        ArrayList<String> out = new ArrayList<String>();
                        for (int i = 0; i < raw.length; i++) {
                            String r = raw[i].trim();
                            if (r.length() > 0 && !r.startsWith("#")) out.add(r);
                        }
                        cachedConfig.rules = out.toArray(new String[out.size()]);
                    }
                    cachedConfig.guardPopup = res.getBoolean("guard_popup", true);
                    cachedConfig.disableOnetrack = res.getBoolean("disable_onetrack", true);
                    cachedConfig.disablePromoDialog = res.getBoolean("disable_promo_dialog", true);
                    cachedConfig.disableHotword = res.getBoolean("disable_hotword", true);
                    cachedConfig.timestamp = now;
                }
            } catch (Throwable t) { }
        }
        return cachedConfig;
    }

    private void record(Context ctx, String tag, String detail) {
        if (ctx == null) ctx = getAppContext();
        if (ctx != null) {
            try {
                Bundle b = new Bundle();
                b.putString("detail", detail);
                ctx.getContentResolver().call(
                    Uri.parse("content://com.hermes.browserenhance.log"),
                    "log",
                    tag,
                    b
                );
            } catch (Throwable t) { }
        }
        try {
            log(4, "BrowserEnhance", "[" + tag + "] " + detail);
        } catch (Throwable t) { }
    }

    private static String extractHost(String url) {
        if (url == null || url.length() == 0) return "";
        try {
            URI u = new URI(url);
            String h = u.getHost();
            if (h != null && h.length() > 0) return h;
        } catch (Throwable t) { }
        int p = url.indexOf("://");
        if (p >= 0) {
            String rest = url.substring(p + 3);
            int slash = rest.indexOf('/');
            return slash >= 0 ? rest.substring(0, slash) : rest;
        }
        return url;
    }

    private static String findSourceUrl(Object thisObj, Object[] args) {
        String url = null;
        if (args != null && args.length > 0 && args[0] != null) {
            Object a0 = args[0];
            try {
                Method m = a0.getClass().getMethod("getUrl");
                Object r = m.invoke(a0);
                if (r instanceof String && ((String) r).length() > 0) url = (String) r;
            } catch (Throwable t) {}

            if (url == null || url.length() == 0) {
                try {
                    Method m = a0.getClass().getMethod("getOriginalUrl");
                    Object r = m.invoke(a0);
                    if (r instanceof String && ((String) r).length() > 0) url = (String) r;
                } catch (Throwable t) {}
            }
        }
        if ((url == null || url.length() == 0) && thisObj != null) {
            try {
                for (Field f : thisObj.getClass().getDeclaredFields()) {
                    if (f.getName().startsWith("this$") || f.getType().getName().contains("Tab")) {
                        f.setAccessible(true);
                        Object outer = f.get(thisObj);
                        if (outer != null) {
                            try {
                                Method m = outer.getClass().getMethod("getUrl");
                                Object r = m.invoke(outer);
                                if (r instanceof String && ((String) r).length() > 0) {
                                    url = (String) r;
                                    break;
                                }
                            } catch (Throwable t) {}
                            try {
                                Method m = outer.getClass().getMethod("getOriginalUrl");
                                Object r = m.invoke(outer);
                                if (r instanceof String && ((String) r).length() > 0) {
                                    url = (String) r;
                                    break;
                                }
                            } catch (Throwable t) {}
                        }
                    }
                }
            } catch (Throwable t) {}
        }
        return url != null ? url : "";
    }

    private Set<String> getAllowHosts(Config cfg) {
        Set<String> set = new HashSet<String>();
        set.add("fourhoi.com");
        set.add("surrit.com");
        set.add("missav.ai");
        set.add("missav.ws");
        set.add("missav.com");
        if (cfg != null && cfg.rules != null) {
            for (String r : cfg.rules) {
                if (r.startsWith("allow:")) {
                    String h = r.substring(6).trim().toLowerCase();
                    if (h.length() > 0) set.add(h);
                }
            }
        }
        return set;
    }

    private String[] mergeHosts(String[] orig, Set<String> custom) {
        Set<String> all = new HashSet<String>(custom);
        if (orig != null) {
            for (String s : orig) {
                if (s != null && s.length() > 0) all.add(s);
            }
        }
        return all.toArray(new String[all.size()]);
    }

    private volatile boolean whitelistApplied = false;

    private synchronized void applyWhitelistToStatics(Object staticsInstance, Config cfg) {
        if (staticsInstance == null) return;
        try {
            Set<String> allows = getAllowHosts(cfg);
            String[] arr = allows.toArray(new String[allows.size()]);

            Method mAd = staticsInstance.getClass().getMethod("setDisableAdBlockHosts", String[].class);
            mAd.invoke(staticsInstance, (Object) arr);

            Method mPrivacy = staticsInstance.getClass().getMethod("setDisableAdPrivacyHosts", String[].class);
            mPrivacy.invoke(staticsInstance, (Object) arr);

            if (!whitelistApplied) {
                whitelistApplied = true;
                StringBuilder sb = new StringBuilder();
                for (String s : allows) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(s);
                }
                record(null, "白名单", "已向内核注入广告与防跟踪免检: " + sb.toString());
            }
        } catch (Throwable t) {
            log(5, "BrowserEnhance", "applyWhitelistToStatics err: " + t);
        }
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!"com.android.browser".equals(param.getPackageName())) return;

        final ClassLoader cl = param.getClassLoader();
        log(4, "BrowserEnhance", "[BrowserEnhance] Modern API 102 ready for " + param.getPackageName());

        // 1) 拦截网页内部 Console 广告与弹窗信息
        String[] wccClasses = new String[] {
            "android.webkit.WebChromeClient",
            "com.android.browser.Tab$MainWebChromeClient"
        };
        for (String wccName : wccClasses) {
            try {
                Class<?> wcc = cl.loadClass(wccName);
                for (Method m : wcc.getDeclaredMethods()) {
                    if ("onConsoleMessage".equals(m.getName())) {
                        hook(m).intercept(new XposedInterface.Hooker() {
                            @Override
                            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                                try {
                                    if (chain.getArgs().size() > 0) {
                                        Object arg = chain.getArg(0);
                                        String msg = null;
                                        if (arg instanceof ConsoleMessage) {
                                            msg = ((ConsoleMessage) arg).message();
                                        } else if (arg != null) {
                                            msg = arg.toString();
                                        }
                                        if (msg != null && msg.startsWith("[hermes-guard]")) {
                                            Context ctx = resolveContext(chain.getThisObject());
                                            String body = msg.substring(14).trim();
                                            if (body.startsWith("blocked ->")) {
                                                record(ctx, "弹窗", "阻止页面调用 window.open -> " + body.substring(10).trim());
                                            } else if (body.startsWith("click cancel ->")) {
                                                record(ctx, "广告", "取消广告链接点击 -> " + body.substring(15).trim());
                                            } else if (body.startsWith("submit cancel ->")) {
                                                record(ctx, "广告", "取消广告表单提交 -> " + body.substring(16).trim());
                                            }
                                        }
                                    }
                                } catch (Throwable t) {}
                                return chain.proceed();
                            }
                        });
                    }
                }
            } catch (Throwable t) {}
        }

        // 2) 底层 onCreateWindow 弹窗拦截（精确比对发出弹窗的「原网页网址」，保住视频焦点）
        try {
            Class<?> tabClient = cl.loadClass("com.android.browser.Tab$MainWebChromeClient");
            for (Method m : tabClient.getDeclaredMethods()) {
                if (!"onCreateWindow".equals(m.getName())) continue;
                hook(m).intercept(new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        try {
                            Context ctx = resolveContext(chain.getArgs().size() > 0 ? chain.getArg(0) : null);
                            Config cfg = getConfig(ctx);

                            Object[] args = chain.getArgs().toArray();
                            String srcUrl = findSourceUrl(chain.getThisObject(), args);
                            String srcHost = extractHost(srcUrl);

                            String displaySite = srcHost.length() > 0 ? srcHost : (srcUrl.length() > 0 ? srcUrl : "");
                            if (displaySite.length() == 0) {
                                Object a0 = args.length > 0 ? args[0] : null;
                                displaySite = "未知来源 (" + (a0 != null ? a0.getClass().getSimpleName() : "null") + ")";
                            }

                            if (!cfg.guardPopup) {
                                record(ctx, "弹窗", "放行新标签页 (弹窗总开关已关闭), 来源=" + displaySite);
                                return chain.proceed();
                            }

                            boolean block = false;
                            String hitRule = null;

                            for (String r : cfg.rules) {
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
                                record(ctx, "弹窗", "成功拦截来自 " + displaySite + " 的新标签页弹窗 (" + hitRule + ")");
                                return Boolean.FALSE;
                            } else {
                                record(ctx, "弹窗", "放行来自 " + displaySite + " 的新窗口 (未命中 nopopup 规则)");
                            }
                        } catch (Throwable t) {
                            log(5, "BrowserEnhance", "onCreateWindow err: " + t);
                        }
                        return chain.proceed();
                    }
                });
                break;
            }
        } catch (Throwable t) {
            log(5, "BrowserEnhance", "Tab$MainWebChromeClient hook err: " + t);
        }

        // 3) 内核级广告免检白名单 Hook (MiuiStatics，在系统自然创建实例时注入，绝对不提前强制加载)
        try {
            Class<?> ms = cl.loadClass("com.android.webview.chromium.MiuiStatics");
            for (Method m : ms.getDeclaredMethods()) {
                String name = m.getName();
                if ("getInstance".equals(name) && m.getParameterTypes().length == 0) {
                    hook(m).intercept(new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(XposedInterface.Chain chain) throws Throwable {
                            Object inst = chain.proceed();
                            applyWhitelistToStatics(inst, getConfig(null));
                            return inst;
                        }
                    });
                } else if ("setDisableAdBlockHosts".equals(name) || "setDisableAdPrivacyHosts".equals(name)) {
                    hook(m).intercept(new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(XposedInterface.Chain chain) throws Throwable {
                            String[] orig = chain.getArgs().size() > 0 ? (String[]) chain.getArg(0) : null;
                            Config cfg = getConfig(null);
                            String[] merged = mergeHosts(orig, getAllowHosts(cfg));
                            return chain.proceed(new Object[]{ merged });
                        }
                    });
                }
            }
        } catch (Throwable t) {
            log(5, "BrowserEnhance", "MiuiStatics hook err: " + t);
        }

        // 4) 禁用 OneTrack 行为上报
        try {
            Class<?> ot = null;
            try { ot = cl.loadClass("miui.util.OneTrack"); } catch (Throwable t) {}
            if (ot == null) {
                try { ot = cl.loadClass("com.miui.onetrack.OneTrack"); } catch (Throwable t) {}
            }
            if (ot != null) {
                for (Method m : ot.getDeclaredMethods()) {
                    if ("report".equals(m.getName())) {
                        hook(m).intercept(new XposedInterface.Hooker() {
                            @Override
                            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                                Config cfg = getConfig(null);
                                if (!cfg.disableOnetrack) return chain.proceed();

                                String ev = "";
                                try {
                                    if (chain.getArgs().size() > 0 && chain.getArg(0) != null) {
                                        ev = String.valueOf(chain.getArg(0));
                                    }
                                } catch (Throwable t) {}
                                record(null, "统计", "拦截 OneTrack 行为上报" + (ev.isEmpty() ? "" : ": " + ev));
                                return null;
                            }
                        });
                    }
                }
            }
        } catch (Throwable t) { }

        // 5) 屏蔽应用运营/更新提示弹窗
        try {
            Class<?> ud = null;
            try { ud = cl.loadClass("com.android.browser.update.UpdateDialog"); } catch (Throwable t) {}
            if (ud != null) {
                for (Method m : ud.getDeclaredMethods()) {
                    if ("show".equals(m.getName())) {
                        hook(m).intercept(new XposedInterface.Hooker() {
                            @Override
                            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                                Config cfg = getConfig(null);
                                if (!cfg.disablePromoDialog) return chain.proceed();

                                record(null, "推广", "拦截应用运营/更新提示弹窗");
                                return null;
                            }
                        });
                    }
                }
            }
        } catch (Throwable t) { }

        // 6) 抑制地址栏热词与推荐搜索（安全修改 SharedPreferences，不碰 UI 线程）
        try {
            Class<?> appCls = cl.loadClass("android.app.Application");
            Method oc = appCls.getDeclaredMethod("onCreate");
            hook(oc).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object res = chain.proceed();
                    try {
                        Context ctx = (Context) chain.getThisObject();
                        Config cfg = getConfig(ctx);
                        if (cfg.disableHotword) {
                            boolean changed = false;
                            SharedPreferences kw = ctx.getSharedPreferences("key_word_config", 0);
                            if (kw.getInt("PREF_HOT_WORD", 1) != 0) {
                                kw.edit().putInt("PREF_HOT_WORD", 0).apply();
                                changed = true;
                            }
                            SharedPreferences bp = ctx.getSharedPreferences("com.android.browser_preferences", 0);
                            if (bp.getBoolean("recommending_search_enter_enabled", true)) {
                                bp.edit().putBoolean("recommending_search_enter_enabled", false).apply();
                                changed = true;
                            }
                            if (changed) {
                                record(ctx, "热搜", "已抑制地址栏推荐热词与搜索发现");
                            }
                        }
                    } catch (Throwable t) { }
                    return res;
                }
            });
        } catch (Throwable t) { }
    }
}
