package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.groups;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiErrors;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.KasratDialog;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;
import retrofit2.Response;

/** Small shared bits for the group screens. */
public final class Groups {

    private Groups() { }

    /** Name and description as the server stores them: trimmed, runs of spaces collapsed. */
    @NonNull
    public static String clean(@Nullable CharSequence s) {
        return s == null ? "" : s.toString().trim().replaceAll("\\s+", " ");
    }

    public static boolean validName(@NonNull String name) {
        return name.length() >= 3 && name.length() <= 40;
    }

    @NonNull
    public static String members(@NonNull Context ctx, int n) {
        return ctx.getResources().getQuantityString(R.plurals.groups_members, n, Numbers.format(n));
    }

    @StringRes
    public static int privacyLabel(@Nullable String privacy) {
        return "public".equals(privacy) ? R.string.group_public : R.string.groups_private;
    }

    @StringRes
    public static int roleLabel(@Nullable String role) {
        if ("owner".equals(role)) return R.string.groups_role_owner;
        if ("admin".equals(role)) return R.string.groups_role_admin;
        if ("moderator".equals(role)) return R.string.groups_role_moderator;
        return R.string.groups_role_member;
    }

    /** A readable message for a failed group call. */
    @NonNull
    public static String error(@NonNull Context ctx, @NonNull Response<?> r) {
        ApiErrors.Failure f = ApiErrors.of(r);
        if (f.code != null) {
            switch (f.code) {
                case "too_many_groups": return ctx.getString(R.string.groups_err_too_many);
                case "group_deleted": return ctx.getString(R.string.groups_deleted_title);
                case "banned": return ctx.getString(R.string.groups_banned_title);
                case "group_full": return ctx.getString(R.string.groups_err_full);
                case "already_member": return ctx.getString(R.string.groups_err_already);
                case "owner_must_transfer": return ctx.getString(R.string.groups_err_owner_leave);
                case "name_mismatch": return ctx.getString(R.string.groups_err_name_mismatch);
                case "rate_limited": return ctx.getString(R.string.groups_err_rate);
                default: break;
            }
        }
        if (f.status == 429) return ctx.getString(R.string.groups_err_rate);
        if (f.message != null && !f.message.isEmpty()) return f.message;
        return ctx.getString(R.string.error_load_failed);
    }

    /** Join with code / Scan QR before P6. */
    public static void comingSoon(@NonNull Context ctx) {
        KasratDialog.with(ctx).icon(R.drawable.ic_groups).title(R.string.groups_soon_join_title)
                .body(R.string.groups_soon_join_body).primary(R.string.ok_got_it, KasratDialog::dismiss).show();
    }
}
