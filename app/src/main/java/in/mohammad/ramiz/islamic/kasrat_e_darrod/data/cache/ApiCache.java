package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.cache;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.security.SecureBox;

/**
 * Last good server responses, so screens paint instantly (and offline) and
 * then refresh in the background.
 *
 *  - In memory for this process, and on disk in no_backup/ (never in backups),
 *    encrypted with the Keystore key: a rooted dump or a backup can't read it.
 *  - Per signed-in user (the key includes the user id) and wiped on sign-out.
 *  - {@link #isFresh} lets a screen skip the network when the copy is only a
 *    few seconds old and nothing was written since ({@link #markWrite} is
 *    called by ApiClient after every successful POST/PATCH/DELETE).
 */
public final class ApiCache {

    private static final String DIR = "kcache";
    private static final int MEMORY_ENTRIES = 48;
    private static final Gson GSON = new Gson();

    private static final class Saved {
        final long savedAt;
        final String json;
        Saved(long savedAt, String json) { this.savedAt = savedAt; this.json = json; }
    }

    private static final Map<String, Saved> MEMORY = new LinkedHashMap<String, Saved>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Saved> e) {
            return size() > MEMORY_ENTRIES;
        }
    };
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "kasrat-cache");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static volatile long lastWriteAt;

    private ApiCache() { }

    // ── Read ────────────────────────────────────────────────────────────────

    /** The saved copy for this user, or null. Reads disk only on a memory miss. */
    @Nullable
    public static <T> T get(@NonNull Context ctx, @NonNull String key, @NonNull Type type) {
        Saved e = entry(ctx, key);
        if (e == null) return null;
        try {
            return GSON.fromJson(e.json, type);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** True when the copy is younger than {@code maxAgeMs} and nothing was written since. */
    public static boolean isFresh(@NonNull Context ctx, @NonNull String key, long maxAgeMs) {
        Saved e = entry(ctx, key);
        if (e == null) return false;
        long now = System.currentTimeMillis();
        return e.savedAt > lastWriteAt && now - e.savedAt < maxAgeMs && now >= e.savedAt;
    }

    @Nullable
    private static Saved entry(@NonNull Context ctx, @NonNull String key) {
        String full = scoped(ctx, key);
        if (full == null) return null;
        synchronized (MEMORY) {
            Saved e = MEMORY.get(full);
            if (e != null) return e;
        }
        Saved e = readDisk(ctx, full);
        if (e != null) synchronized (MEMORY) { MEMORY.put(full, e); }
        return e;
    }

    // ── Write ───────────────────────────────────────────────────────────────

    public static void put(@NonNull Context ctx, @NonNull String key, @Nullable Object value) {
        String full = scoped(ctx, key);
        if (full == null || value == null) return;
        Saved e = new Saved(System.currentTimeMillis(), GSON.toJson(value));
        synchronized (MEMORY) { MEMORY.put(full, e); }
        Context app = ctx.getApplicationContext();
        IO.execute(() -> writeDisk(app, full, e));
    }

    /** Drop one saved copy (e.g. the group was deleted or you were banned). */
    public static void remove(@NonNull Context ctx, @NonNull String key) {
        String full = scoped(ctx, key);
        if (full == null) return;
        synchronized (MEMORY) { MEMORY.remove(full); }
        Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            //noinspection ResultOfMethodCallIgnored
            file(app, full).delete();
        });
    }

    /** Something changed on the server: every copy is stale (still shown, but refreshed). */
    public static void markWrite() {
        lastWriteAt = System.currentTimeMillis();
    }

    /** Sign-out: forget everything, in memory and on disk. */
    public static void clearAll(@NonNull Context ctx) {
        synchronized (MEMORY) { MEMORY.clear(); }
        lastWriteAt = System.currentTimeMillis();
        Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            File[] files = dir(app).listFiles();
            if (files != null) for (File f : files) //noinspection ResultOfMethodCallIgnored
                f.delete();
        });
    }

    // ── Disk ────────────────────────────────────────────────────────────────

    @Nullable
    private static String scoped(@NonNull Context ctx, @NonNull String key) {
        String uid = new TokenStore(ctx).userId();
        return uid == null ? null : uid + "|" + key;
    }

    @NonNull
    private static File dir(@NonNull Context ctx) {
        File d = new File(ctx.getNoBackupFilesDir(), DIR);
        if (!d.isDirectory()) //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        return d;
    }

    @NonNull
    private static File file(@NonNull Context ctx, @NonNull String full) {
        return new File(dir(ctx), sha256(full));
    }

    @Nullable
    private static Saved readDisk(@NonNull Context ctx, @NonNull String full) {
        File f = file(ctx, full);
        if (!f.isFile() || f.length() > 4 * 1024 * 1024) return null;
        byte[] raw = new byte[(int) f.length()];
        try (FileInputStream in = new FileInputStream(f)) {
            int off = 0;
            while (off < raw.length) {
                int n = in.read(raw, off, raw.length - off);
                if (n < 0) break;
                off += n;
            }
        } catch (IOException e) {
            return null;
        }
        byte[] plain = SecureBox.decrypt(raw);
        if (plain == null) {                       // damaged, or the key changed
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            return null;
        }
        String s = new String(plain, StandardCharsets.UTF_8);
        int bar = s.indexOf('\n');
        if (bar <= 0) return null;
        try {
            return new Saved(Long.parseLong(s.substring(0, bar)), s.substring(bar + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void writeDisk(@NonNull Context ctx, @NonNull String full, @NonNull Saved e) {
        byte[] box = SecureBox.encrypt((e.savedAt + "\n" + e.json).getBytes(StandardCharsets.UTF_8));
        if (box == null) return;
        File f = file(ctx, full);
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(box);
            out.getFD().sync();
        } catch (IOException ex) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(f);
    }

    @NonNull
    private static String sha256(@NonNull String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder(d.length * 2);
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
