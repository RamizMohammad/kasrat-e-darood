package in.mohammad.ramiz.islamic.kasrat_e_darrod.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiErrors;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Sends recitation logs (cart, quick-log and tasbih all go through here) and
 * publishes the fresh totals so every screen updates.
 *
 * Uses POST /submissions/bulk: no group id means the community group, and the
 * whole batch is saved together or not at all. Callers pass client_uuids they
 * keep until success, so retrying after a timeout never double-counts.
 */
public final class LogService {

    public interface Result {
        void onLogged(@NonNull dto.Totals totals);

        /** {@code offline} = the request never reached the server. */
        void onFailed(@NonNull String message, boolean offline);
    }

    private LogService() { }

    public static void logOne(@NonNull Context ctx, @NonNull String recitationId, int count,
                              @NonNull String clientUuid, @NonNull Result cb) {
        log(ctx, Collections.singletonList(new dto.BulkItem(recitationId, count, clientUuid)), cb);
    }

    public static void logCart(@NonNull Context ctx, @NonNull List<Cart.Line> lines, @NonNull Result cb) {
        List<dto.BulkItem> items = new ArrayList<>();
        for (Cart.Line l : lines) items.add(new dto.BulkItem(l.id, l.count, l.clientUuid));
        log(ctx, items, cb);
    }

    private static void log(@NonNull Context ctx, @NonNull List<dto.BulkItem> items, @NonNull Result cb) {
        Context app = ctx.getApplicationContext();
        ApiClient.get(app).submitBulk(new dto.BulkSubmitRequest(items)).enqueue(new Callback<dto.Totals>() {
            @Override
            public void onResponse(@NonNull Call<dto.Totals> call, @NonNull Response<dto.Totals> r) {
                if (r.isSuccessful() && r.body() != null) {
                    Totals.publish(r.body());
                    cb.onLogged(r.body());
                } else {
                    cb.onFailed(message(app, r), false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<dto.Totals> call, @NonNull Throwable t) {
                cb.onFailed(app.getString(R.string.error_network), true);
            }
        });
    }

    @NonNull
    private static String message(@NonNull Context app, @NonNull Response<?> r) {
        ApiErrors.Failure f = ApiErrors.of(r);
        if (f.rateLimited()) return app.getString(R.string.error_rate_limited);
        @Nullable String m = f.message;
        return m != null && !m.isEmpty() ? m : app.getString(R.string.log_failed);
    }
}
