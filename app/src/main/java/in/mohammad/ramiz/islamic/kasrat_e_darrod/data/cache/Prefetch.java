package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.cache;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.RecitationStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiService;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.ranks.BoardPane;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Warm the cache while the splash screen is up: the tabs' first requests go
 * out in parallel at launch, so by the time a tab opens its data is usually
 * already here (and on a slow network the saved copy shows meanwhile).
 */
public final class Prefetch {

    /** Cache keys shared with the screens that read them. */
    public static final String DASHBOARD = "dashboard";
    public static final String RECENT = "recent:100";
    public static final String MY_GROUPS = "groups:mine";
    public static final String MY_STATS = "stats:me";

    /** BoardPane's key for a board ({@code source} = "global", "group:<id>"...). */
    @NonNull
    public static String board(@NonNull String source, @NonNull String scope) {
        return "board:" + source + ":" + scope;
    }

    private Prefetch() { }

    public static void warm(@NonNull Context ctx) {
        Context app = ctx.getApplicationContext();
        if (!new TokenStore(app).isLoggedIn()) return;
        ApiService api = ApiClient.get(app);
        fetch(app, DASHBOARD, api.dashboard(null));
        fetch(app, RECENT, api.recentSubmissions(100));
        fetch(app, MY_GROUPS, api.myGroups());
        fetch(app, MY_STATS, api.myStats());
        fetch(app, board("global", "week"), api.globalBoard("week", null, BoardPane.LIMIT));
        // The library isn't prefetched: it's the biggest reply, and downloading it
        // at launch made every small request on the shared connection wait behind it.
        // RecitationStore checks it on its own (ETag, at most every 10 minutes).
    }

    private static <T> void fetch(@NonNull Context app, @NonNull String key, @NonNull Call<T> call) {
        if (ApiCache.isFresh(app, key, Cached.FRESH_SHORT)) return;
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(@NonNull Call<T> c, @NonNull Response<T> r) {
                if (r.isSuccessful() && r.body() != null) ApiCache.put(app, key, r.body());
            }

            @Override
            public void onFailure(@NonNull Call<T> c, @NonNull Throwable t) { }
        });
    }
}
