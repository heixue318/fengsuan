package com.fengluanchui.fengsuan;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

final class AppUpdater {
    private static final String API = "https://api.github.com/repos/heixue318/fengsuan/releases/latest";
    private final Activity activity;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean cancelled;
    private volatile boolean closed;
    private volatile HttpURLConnection connection;
    private boolean busy;
    private ProgressDialog progress;
    private File pendingInstall;
    private int expectedVersion;
    private boolean waitingPermission;

    AppUpdater(Activity activity) { this.activity = activity; }

    void check() {
        if (busy) return;
        busy = true;
        cancelled = false;
        showProgress("正在检查更新", false);
        worker.execute(() -> {
            try {
                JSONObject release = latestRelease();
                if (release.optBoolean("draft") || release.optBoolean("prerelease"))
                    throw new IOException("没有可用的正式版本");
                String tag = release.getString("tag_name");
                PackageInfo current = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                if (UpdateVersion.compare(tag, current.versionName) <= 0) {
                    finish(() -> message("检查更新", "当前已是最新版本 " + current.versionName));
                    return;
                }
                JSONArray assets = release.getJSONArray("assets");
                JSONObject apk = null;
                String name = "FengSuan-" + tag + "-release-signed.apk";
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject candidate = assets.getJSONObject(i);
                    if (name.equals(candidate.optString("name"))) apk = candidate;
                }
                if (apk == null) throw new IOException("新版安装包尚未就绪，请稍后重试");
                final String url = apk.getString("browser_download_url");
                if (!url.startsWith("https://github.com/heixue318/fengsuan/releases/download/"))
                    throw new IOException("更新地址无效");
                final long size = apk.getLong("size");
                final String digest = apk.optString("digest", "");
                final String notes = release.optString("body", "修复与改进");
                finish(() -> new AlertDialog.Builder(activity).setTitle("发现新版本 " + tag)
                        .setMessage("下载来源：GitHub\n大小：" + (size / 1024) + " KB\n\n"
                                + notes.substring(0, Math.min(notes.length(), 2400)))
                        .setNegativeButton("稍后", null)
                        .setPositiveButton("下载更新", (d, w) -> download(url, size, digest)).show());
            } catch (Exception e) { failed("检查失败，请检查网络后重试。GitHub 暂时无法访问时可稍后再试。"); }
        });
    }

    private void download(String url, long size, String digest) {
        if (busy) return;
        busy = true;
        cancelled = false;
        pendingInstall = null;
        showProgress("正在下载更新", true);
        worker.execute(() -> {
            File file = new File(activity.getCacheDir(), "update.apk");
            boolean verified = false;
            try {
                if (size <= 0 || size > 100 * 1024 * 1024) throw new IOException("安装包大小异常");
                HttpURLConnection c = open(url);
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                long total = 0;
                int previous = -1;
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(file)) {
                    byte[] buffer = new byte[16384];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        ensureActive();
                        total += n;
                        if (total > size) throw new IOException("安装包大小不符");
                        out.write(buffer, 0, n);
                        sha.update(buffer, 0, n);
                        int percent = (int) (total * 100 / size);
                        if (percent != previous) {
                            previous = percent;
                            ui(() -> { if (progress != null) progress.setProgress(percent); });
                        }
                    }
                } finally { c.disconnect(); }
                ensureActive();
                if (total != size) throw new IOException("下载不完整");
                StringBuilder hex = new StringBuilder();
                for (byte b : sha.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
                if (!digest.isEmpty() && !digest.equalsIgnoreCase("sha256:" + hex))
                    throw new IOException("文件校验失败");
                expectedVersion = verifyApk(file);
                verified = true;
                finish(() -> {
                    pendingInstall = file;
                    new AlertDialog.Builder(activity).setTitle("更新已下载")
                            .setMessage("安装包已校验，点击安装后请在安卓系统界面确认。")
                            .setNegativeButton("稍后", null)
                            .setPositiveButton("安装", (d, w) -> install()).show();
                });
            } catch (Exception e) {
                failed("下载或校验失败，请检查网络后重新下载。未安装任何更新。");
            } finally { if (!verified) file.delete(); }
        });
    }

    private JSONObject latestRelease() throws Exception {
        try {
            return new JSONObject(new String(fetch(API, 1024 * 1024), "UTF-8"));
        } catch (Exception unavailable) {
            ensureActive();
            HttpURLConnection page = open("https://github.com/heixue318/fengsuan/releases/latest");
            String finalUrl = page.getURL().toString();
            page.disconnect();
            String prefix = "https://github.com/heixue318/fengsuan/releases/tag/";
            if (!finalUrl.startsWith(prefix)) throw new IOException("无法确认最新版本");
            String tag = finalUrl.substring(prefix.length());
            UpdateVersion.compare(tag, "0");
            JSONObject release = new JSONObject().put("tag_name", tag);
            PackageInfo current = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            if (UpdateVersion.compare(tag, current.versionName) <= 0) return release;
            String name = "FengSuan-" + tag + "-release-signed.apk";
            String url = "https://github.com/heixue318/fengsuan/releases/download/" + tag + "/" + name;
            HttpURLConnection apk = open(url);
            long size;
            try { size = Long.parseLong(apk.getHeaderField("Content-Length")); }
            finally { apk.disconnect(); }
            if (size <= 0) throw new IOException("无法确认安装包大小");
            return release.put("body", "GitHub 接口受限，已通过备用方式检测到正式版本。下载后将校验包名、版本和签名。")
                    .put("assets", new JSONArray().put(new JSONObject().put("name", name)
                            .put("browser_download_url", url).put("size", size)));
        }
    }

    private int verifyApk(File file) throws Exception {
        PackageManager pm = activity.getPackageManager();
        PackageInfo installed = pm.getPackageInfo(activity.getPackageName(), PackageManager.GET_SIGNATURES);
        PackageInfo candidate = pm.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_SIGNATURES);
        if (candidate == null || !activity.getPackageName().equals(candidate.packageName)
                || candidate.versionCode <= installed.versionCode)
            throw new IOException("包名或版本不符");
        if (!signatures(installed).equals(signatures(candidate))) throw new IOException("签名不符");
        return candidate.versionCode;
    }

    private Set<String> signatures(PackageInfo info) throws IOException {
        if (info.signatures == null || info.signatures.length == 0) throw new IOException("缺少签名");
        Set<String> values = new HashSet<>();
        for (Signature signature : info.signatures) values.add(signature.toCharsString());
        return values;
    }

    private void install() {
        try {
            if (pendingInstall == null || verifyApk(pendingInstall) != expectedVersion)
                throw new IOException("安装包已失效");
            if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity).setTitle("允许安装更新")
                        .setMessage("请在下一页允许风算安装应用，然后返回风算继续安装。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("去设置", (d, w) -> {
                            try {
                                waitingPermission = true;
                                activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:" + activity.getPackageName())));
                            } catch (Exception e) { waitingPermission = false; message("无法打开设置", "请在系统设置中允许风算安装应用。"); }
                        }).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse("content://" + activity.getPackageName() + ".updates/update.apk"),
                    "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(intent);
        } catch (Exception e) { message("无法安装", "请重新检查更新并下载，或确认系统允许安装应用。"); }
    }

    void resume() {
        if (waitingPermission) {
            waitingPermission = false;
            if (Build.VERSION.SDK_INT >= 26 && activity.getPackageManager().canRequestPackageInstalls()) install();
            else message("尚未授权", "未安装更新。稍后可重新检查更新。");
        }
    }

    private HttpURLConnection open(String address) throws Exception {
        URL url = new URL(address);
        for (int i = 0; i < 6; i++) {
            ensureActive();
            if (!"https".equals(url.getProtocol())) throw new IOException("需要 HTTPS");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            connection = c;
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "FengSuan-Android");
            c.setRequestProperty("Accept-Encoding", "identity");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = c.getHeaderField("Location");
                c.disconnect();
                if (location == null) throw new IOException("无效重定向");
                url = new URL(url, location);
                continue;
            }
            if (code != 200) { c.disconnect(); throw new IOException("HTTP " + code); }
            return c;
        }
        throw new IOException("重定向次数过多");
    }

    private byte[] fetch(String address, int limit) throws Exception {
        HttpURLConnection c = open(address);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192];
            int count;
            while ((count = in.read(bytes)) != -1) {
                ensureActive();
                if (out.size() + count > limit) throw new IOException("响应过大");
                out.write(bytes, 0, count);
            }
            return out.toByteArray();
        } finally { c.disconnect(); }
    }

    private void ensureActive() throws IOException {
        if (cancelled || closed) throw new IOException("cancelled");
    }
    private void showProgress(String title, boolean downloading) {
        progress = new ProgressDialog(activity);
        progress.setTitle(title);
        progress.setMessage("下载和检查均在风算内完成");
        progress.setProgressStyle(downloading ? ProgressDialog.STYLE_HORIZONTAL : ProgressDialog.STYLE_SPINNER);
        progress.setMax(100);
        progress.setCanceledOnTouchOutside(false);
        progress.setButton(ProgressDialog.BUTTON_NEGATIVE, "取消", (d, w) -> cancel());
        progress.setOnCancelListener(d -> cancel());
        progress.show();
    }
    private void cancel() {
        cancelled = true;
        HttpURLConnection active = connection;
        if (active != null) workerCancel(active);
    }
    private void workerCancel(HttpURLConnection active) {
        new Thread(active::disconnect, "update-cancel").start();
    }
    private void ui(Runnable action) {
        activity.runOnUiThread(() -> { if (!closed && !activity.isFinishing() && !activity.isDestroyed()) action.run(); });
    }
    private void finish(Runnable action) {
        ui(() -> {
            busy = false;
            if (progress != null) { progress.dismiss(); progress = null; }
            if (!cancelled) action.run();
        });
    }
    private void failed(String text) {
        finish(() -> new AlertDialog.Builder(activity).setTitle("更新未完成").setMessage(text)
                .setNegativeButton("关闭", null).setPositiveButton("重试", (d, w) -> check()).show());
    }
    private void message(String title, String text) {
        new AlertDialog.Builder(activity).setTitle(title).setMessage(text).setPositiveButton("知道了", null).show();
    }
    void close() {
        closed = true;
        cancel();
        if (progress != null) progress.dismiss();
        worker.shutdownNow();
    }
}
