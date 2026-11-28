package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.cache;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import java.lang.reflect.Type;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Cache first, then network ("stale-while-revalidate") for one screen load:
 *
 * <pre>
 * Cached.load(getViewLifecycleOwner(), ctx, "dashboard", dto.DashboardResponse.class,
 *             Cached.FRESH_SHORT, () -> api.dashboard(null), new Cached.Result&lt;&gt;() { ... });
 * </pre>
 *
 *  1. The saved copy (if any) is delivered at once with {@code fromCache = true}.
 *  2. If that copy is fresh (see {@link ApiCache#isFresh}) the network is skipped.
 *  3. Otherwise the request runs; a good reply is saved and delivered with
 *     {@code fromCache = false}.
 *
 * The request is cancelled when {@code owner} is destroyed, so a closed screen
 * is never called back (and isn't kept alive by the pending call).
 */
public final class Cached {

    /** Tab switches and quick back-and-forth: reuse a copy this young. */
    public static final long FRESH_SHORT = 30_000L;
    /** Data that rarely changes (library, profile). */
    public static final long FRESH_LONG = 10 * 60_000L;

    public interface Result<T> {
        void onData(@NonNull T data, boolean fromCache);

        /** The request failed; {@code hadCache} says whether something is on screen. */
        default void onError(boolean hadCache, boolean offline) { }

        /** After the last callback (fresh skip, success or error). */
        default void onDone() { }
    }

    public interface CallFactory<T> {
        @NonNull Call<T> create();
    }

    private Cached() { }

    public static <T> void load(@NonNull LifecycleOwner owner, @NonNull Context ctx,
                                @NonNull String key, @NonNull Type type, long freshMs,
                                @NonNull CallFactory<T> factory, @NonNull Result<T> cb) {
        load(owner, ctx, key, type, freshMs, false, factory, cb);
    }

    /** {@code force = true} (pull to refresh) always goes to the network. */
    public static <T> void load(@NonNull LifecycleOwner owner, @NonNull Context ctx,
                                @NonNull String key, @NonNull Type type, long freshMs, boolean force,
                                @NonNull CallFactory<T> factory, @NonNull Result<T> cb) {
        Context app = ctx.getApplicationContext();
        T cached = ApiCache.get(app, key, type);
        if (cached != null) cb.onData(cached, true);
        if (!force && cached != null && ApiCache.isFresh(app, key, freshMs)) {
            cb.onDone();
            return;
        }
        if (owner.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) return;

        Call<T> call = factory.create();
        LifecycleEventObserver cancelOnDestroy = (src, event) -> {
            if (event == Lifecycle.Event.ON_DESTROY) call.cancel();
        };
        owner.getLifecycle().addObserver(cancelOnDestroy);
        boolean hadCache = cached != null;
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(@NonNull Call<T> c, @NonNull Response<T> r) {
                owner.getLifecycle().removeObserver(cancelOnDestroy);
                if (!alive(owner)) return;
                T body = r.body();
                if (r.isSuccessful() && body != null) {
                    ApiCache.put(app, key, body);
                    cb.onData(body, false);
                } else {
                    cb.onError(hadCache, false);
                }
                cb.onDone();
            }

            @Override
            public void onFailure(@NonNull Call<T> c, @NonNull Throwable t) {
                owner.getLifecycle().removeObserver(cancelOnDestroy);
                if (c.isCanceled() || !alive(owner)) return;
                cb.onError(hadCache, t instanceof java.io.IOException);
                cb.onDone();
            }
        });
    }

    /** Save a reply that arrived some other way (e.g. a screen's own request). */
    public static void save(@NonNull Context ctx, @NonNull String key, @Nullable Object body) {
        ApiCache.put(ctx, key, body);
    }

    private static boolean alive(@NonNull LifecycleOwner owner) {
        return owner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.CREATED);
    }
}
