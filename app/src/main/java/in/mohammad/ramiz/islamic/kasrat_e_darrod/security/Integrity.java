package in.mohammad.ramiz.islamic.kasrat_e_darrod.security;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.Debug;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.security.MessageDigest;
import java.util.Locale;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.BuildConfig;

/**
 * On-device checks for a compromised environment. These raise the bar; they
 * are not proof (a determined attacker on a rooted phone can hide from any
 * client-side check). The strong version is Google Play Integrity verified
 * on the server.
 *
 *  ROOTED   — su binaries, root-manager apps, test-keys build, writable system.
 *  TAMPERED — a copy re-signed by someone else, a debugger attached to a
 *             release build, or a hooking framework (Frida / Xposed / LSPosed)
 *             loaded into this process.
 */
public final class Integrity {

    public enum Status { OK, ROOTED, TAMPERED }

    private static final String[] SU_PATHS = {
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/su", "/system/bin/.ext/su",
            "/system/usr/we-need-root/su", "/system/app/Superuser.apk", "/data/local/su",
            "/data/local/bin/su", "/data/local/xbin/su", "/cache/su", "/dev/su",
            "/system/sbin/su", "/vendor/bin/su", "/su/bin/su", "/debug_ramdisk/su",
    };
    private static final String[] ROOT_DIRS = {
            "/sbin/.magisk", "/data/adb/magisk", "/data/adb/modules", "/data/adb/ksu", "/data/adb/ap",
            "/cache/.disable_magisk", "/dev/.magisk.unblock", "/init.magisk.rc",
    };
    private static final String[] ROOT_APPS = {
            "com.topjohnwu.magisk", "io.github.vvb2060.magisk", "io.github.huskydg.magisk",
            "me.weishu.kernelsu", "me.bmax.apatch", "eu.chainfire.supersu", "com.koushikdutta.superuser",
            "com.noshufou.android.su", "com.thirdparty.superuser", "com.kingroot.kinguser",
            "com.kingo.root", "com.yellowes.su",
    };
    private static final String[] HOOK_APPS = {
            "de.robv.android.xposed.installer", "org.lsposed.manager", "org.meowcat.edxposed.manager",
            "com.saurik.substrate", "io.va.exposed", "org.lsposed.lspatch",
    };
    private static final String[] HOOK_LIBS = {
            "frida", "libgadget", "gum-js", "linjector", "xposed", "lsposed", "substrate", "riru", "zygisk",
    };

    private Integrity() { }

    @NonNull
    public static Status check(@NonNull Context ctx) {
        if (isTampered(ctx)) return Status.TAMPERED;
        if (isRooted(ctx)) return Status.ROOTED;
        return Status.OK;
    }

    // ── Root ────────────────────────────────────────────────────────────────

    public static boolean isRooted(@NonNull Context ctx) {
        String tags = Build.TAGS;
        if (tags != null && tags.contains("test-keys")) return true;
        for (String p : SU_PATHS) if (new File(p).exists()) return true;
        for (String p : ROOT_DIRS) if (new File(p).exists()) return true;
        if (anyInstalled(ctx, ROOT_APPS)) return true;
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(":")) if (!dir.isEmpty() && new File(dir, "su").exists()) return true;
        }
        return systemMountedWritable();
    }

    /** /system mounted read-write is a strong root sign on modern Android. */
    private static boolean systemMountedWritable() {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/mounts"))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split(" ");
                if (f.length >= 4 && ("/system".equals(f[1]) || "/".equals(f[1]))
                        && ("," + f[3] + ",").contains(",rw,") && !"tmpfs".equals(f[2])) {
                    return true;
                }
            }
        } catch (Exception ignored) { }
        return false;
    }

    // ── Tamper / hooking ────────────────────────────────────────────────────

    public static boolean isTampered(@NonNull Context ctx) {
        boolean debuggable = (ctx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (!debuggable && BuildConfig.DEBUG) return true;          // flags don't match: rebuilt
        if (!debuggable && (Debug.isDebuggerConnected() || Debug.waitingForDebugger())) return true;
        if (!signatureMatches(ctx)) return true;
        if (anyInstalled(ctx, HOOK_APPS)) return true;
        return hookLoaded();
    }

    /** RELEASE_CERT_SHA256 (comma-separated) vs the certificate this copy is signed with. */
    private static boolean signatureMatches(@NonNull Context ctx) {
        String expected = BuildConfig.RELEASE_CERT_SHA256 == null ? "" : BuildConfig.RELEASE_CERT_SHA256.trim();
        if (expected.isEmpty() || BuildConfig.DEBUG) return true;      // not configured / debug build
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),
                    PackageManager.GET_SIGNING_CERTIFICATES);
            SigningInfo si = pi.signingInfo;
            if (si == null) return false;
            Signature[] sigs = si.hasMultipleSigners() ? si.getApkContentsSigners() : si.getSigningCertificateHistory();
            String want = normalise(expected);
            for (Signature s : sigs) {
                String have = hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray()));
                for (String w : want.split(",")) if (!w.isEmpty() && w.equals(have)) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Frida / Xposed and friends show up in this process's memory map or threads. */
    private static boolean hookLoaded() {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String line;
            while ((line = r.readLine()) != null) {
                String l = line.toLowerCase(Locale.ROOT);
                for (String h : HOOK_LIBS) if (l.contains(h)) return true;
            }
        } catch (Exception ignored) { }
        File[] tasks = new File("/proc/self/task").listFiles();
        if (tasks != null) {
            for (File t : tasks) {
                try (BufferedReader r = new BufferedReader(new FileReader(new File(t, "comm")))) {
                    String name = r.readLine();
                    if (name != null && (name.startsWith("gum-js") || name.startsWith("gmain")
                            || name.startsWith("gdbus") || name.contains("frida"))) return true;
                } catch (Exception ignored) { }
            }
        }
        // Frida's default server port (27042 = 0x69A2) listening on this device.
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/net/tcp"))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.trim().split("\\s+");
                if (f.length > 3 && f[1].endsWith(":69A2") && "0A".equals(f[3])) return true;
            }
        } catch (Exception ignored) { }
        try {
            Class.forName("de.robv.android.xposed.XposedBridge");
            return true;
        } catch (Throwable ignored) { }
        return false;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static boolean anyInstalled(@NonNull Context ctx, @NonNull String[] packages) {
        PackageManager pm = ctx.getPackageManager();
        for (String p : packages) {
            try {
                pm.getPackageInfo(p, 0);
                return true;
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        return false;
    }

    @NonNull
    private static String normalise(@NonNull String s) {
        return s.replace(":", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }

    @NonNull
    private static String hex(@NonNull byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
