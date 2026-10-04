package com.hermes.browserenhance;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class LogProvider extends ContentProvider {

    public static final String AUTHORITY = "com.hermes.browserenhance.log";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);
    private static final int MAX_LINES = 300;

    private static File internalFile = null;

    @Override
    public boolean onCreate() {
        Context ctx = getContext();
        if (ctx != null) {
            internalFile = new File(ctx.getFilesDir(), "behavior.log");
        }
        return true;
    }

    private File getLogFile() {
        if (internalFile != null) return internalFile;
        Context ctx = getContext();
        if (ctx != null) {
            File dir = ctx.getFilesDir();
            if (dir != null && !dir.exists()) dir.mkdirs();
            internalFile = new File(dir, "behavior.log");
            return internalFile;
        }
        return new File("/data/data/com.hermes.browserenhance/files/behavior.log");
    }

    public static synchronized void writeLog(File f, String tag, String detail) {
        try {
            if (f == null) return;
            File p = f.getParentFile();
            if (p != null && !p.exists()) p.mkdirs();
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String line = time + "\t" + tag + "\t" + detail.replace("\n", " ").replace("\r", "") + "\n";
            FileOutputStream fos = new FileOutputStream(f, true);
            fos.write(line.getBytes("UTF-8"));
            fos.close();
            f.setReadable(true, false);
            f.setWritable(true, false);
        } catch (Throwable t) { }
    }

    public static synchronized void appendLog(String tag, String detail) {
        writeLog(new File("/data/data/com.hermes.browserenhance/files/behavior.log"), tag, detail);
    }

    @Override
    public synchronized Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        try {
            File f = getLogFile();
            if ("log".equals(method)) {
                String tag = arg != null ? arg : "INFO";
                String detail = extras != null ? extras.getString("detail", "") : "";
                writeLog(f, tag, detail);
                trimLog(f);
                result.putBoolean("ok", true);
            } else if ("clear".equals(method)) {
                if (f != null && f.exists()) f.delete();
                result.putBoolean("ok", true);
            } else if ("get".equals(method)) {
                ArrayList<String> lines = new ArrayList<String>();
                if (f != null && f.exists()) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
                    String l;
                    while ((l = br.readLine()) != null) {
                        if (l.trim().length() > 0) lines.add(l);
                    }
                    br.close();
                }
                result.putStringArrayList("lines", lines);
                result.putBoolean("ok", true);
            }
        } catch (Throwable t) {
            result.putString("err", String.valueOf(t));
        }
        return result;
    }

    private void trimLog(File f) {
        try {
            if (f == null || !f.exists() || f.length() < 40960) return;
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            ArrayList<String> list = new ArrayList<String>();
            String l;
            while ((l = br.readLine()) != null) list.add(l);
            br.close();
            if (list.size() > MAX_LINES) {
                int from = list.size() - MAX_LINES;
                FileOutputStream fos = new FileOutputStream(f, false);
                for (int i = from; i < list.size(); i++) {
                    fos.write((list.get(i) + "\n").getBytes("UTF-8"));
                }
                fos.close();
            }
        } catch (Throwable t) {}
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
