package in.mohammad.ramiz.islamic.kasrat_e_darrod.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import retrofit2.Call;
import retrofit2.Response;

/**
 * The recitation library, kept in memory and in a file so the Library opens
 * instantly and still works offline ("showing saved data").
 *
 *   RecitationStore.get(ctx).load(cb)   → cb.onList(cached, fromCache=true) at once if a copy
 *                                         exists, then cb.onList(fresh, false) from the server,
 *                                         or cb.onError(offline) if that fails.
 */
public final class RecitationStore {

    public interface Callback {
        void onList(@NonNull List<dto.RecitationDto> list, boolean fromCache);

        void onError(boolean offline);
    }

    private static final String FILE = "recitations_v1.json";
    @Nullable private static RecitationStore instance;

    public static synchronized RecitationStore get(@NonNull Context ctx) {
        if (instance == null) instance = new RecitationStore(ctx.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final List<Callback> waiting = new CopyOnWriteArrayList<>();
    @Nullable private volatile List<dto.RecitationDto> memory;
    private boolean fetching;

    private RecitationStore(Context app) { this.app = app; }

    /** Whatever is in memory right now (may be empty before the first load). */
    @NonNull
    public List<dto.RecitationDto> now() {
        List<dto.RecitationDto> m = memory;
        return m != null ? m : Collections.emptyList();
    }

    @Nullable
    public dto.RecitationDto byId(@Nullable String id) {
        if (id == null) return null;
        for (dto.RecitationDto r : now()) if (id.equals(r.id)) return r;
        return null;
    }

    /**
     * The saved library at once; the server is asked again only when the copy
     * hasn't been checked for {@link #CHECK_EVERY_MS} (the library rarely
     * changes, and it's the biggest download in the app). Pull to refresh
     * ({@link #refresh}) always checks.
     */
    public void load(@NonNull Callback cb) {
        List<dto.RecitationDto> m = memory;
        if (m != null) {
            cb.onList(m, true);
            if (!checkedRecently()) refresh(cb);
            return;
        }
        io.execute(() -> {
            List<dto.RecitationDto> disk = readFile();
            main.post(() -> {
                if (disk != null && memory == null) memory = disk;
                if (memory != null) {
                    cb.onList(memory, true);
                    if (checkedRecently()) return;
                }
                refresh(cb);
            });
        });
    }

    /** How often load() asks the server whether the library changed. */
    private static final long CHECK_EVERY_MS = 10 * 60_000L;
    private static final String PREFS = "kasrat_library", KEY_ETAG = "etag", KEY_CHECKED = "checked_at";

    private android.content.SharedPreferences prefs() {
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private boolean checkedRecently() {
        long at = prefs().getLong(KEY_CHECKED, 0);
        long now = System.currentTimeMillis();
        return now >= at && now - at < CHECK_EVERY_MS;
    }

    /** Server fetch only (pull to refresh, after adding a recitation). */
    public void refresh(@NonNull Callback cb) {
        waiting.add(cb);
        if (fetching) return;
        fetching = true;
        // Only claim a version we actually hold, or a 304 would leave us with nothing.
        String etag = memory != null ? prefs().getString(KEY_ETAG, null) : null;
        ApiClient.get(app).recitations(null, null, etag).enqueue(new retrofit2.Callback<List<dto.RecitationDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<dto.RecitationDto>> call,
                                   @NonNull Response<List<dto.RecitationDto>> r) {
                fetching = false;
                List<dto.RecitationDto> current = memory;
                if (r.code() == 304 && current != null) {
                    // Unchanged on the server: no download, the saved copy is current.
                    prefs().edit().putLong(KEY_CHECKED, System.currentTimeMillis()).apply();
                    for (Callback c : drain()) c.onList(current, false);
                } else if (r.isSuccessful() && r.body() != null) {
                    List<dto.RecitationDto> fresh = new ArrayList<>(r.body());
                    memory = fresh;
                    String newTag = r.headers().get("ETag");
                    io.execute(() -> writeFile(fresh));
                    prefs().edit().putString(KEY_ETAG, newTag)
                            .putLong(KEY_CHECKED, System.currentTimeMillis()).apply();
                    for (Callback c : drain()) c.onList(fresh, false);
                } else {
                    for (Callback c : drain()) c.onError(false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<dto.RecitationDto>> call, @NonNull Throwable t) {
                fetching = false;
                for (Callback c : drain()) c.onError(true);
            }
        });
    }

    private List<Callback> drain() {
        List<Callback> out = new ArrayList<>(waiting);
        waiting.clear();
        return out;
    }

    @Nullable
    private List<dto.RecitationDto> readFile() {
        File f = new File(app.getFilesDir(), FILE);
        if (!f.exists()) return null;
        try (Reader in = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(in, new TypeToken<List<dto.RecitationDto>>() { }.getType());
        } catch (Exception e) {
            return null;
        }
    }

    private void writeFile(List<dto.RecitationDto> list) {
        File tmp = new File(app.getFilesDir(), FILE + ".tmp");
        try (Writer out = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            new Gson().toJson(list, out);
        } catch (Exception e) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(new File(app.getFilesDir(), FILE));
    }

    /** Name to show for a recitation in the current language. */
    @NonNull
    public static String title(@NonNull dto.RecitationDto r, @NonNull String lang) {
        if ("ur".equals(lang) && r.urduName != null && !r.urduName.isEmpty()) return r.urduName;
        if (r.englishName != null && !r.englishName.isEmpty()) return r.englishName;
        return r.arabicName != null ? r.arabicName : "";
    }

    public static int step(@NonNull dto.RecitationDto r) {
        return r.defaultIncrement > 0 ? r.defaultIncrement : 1;
    }
}
