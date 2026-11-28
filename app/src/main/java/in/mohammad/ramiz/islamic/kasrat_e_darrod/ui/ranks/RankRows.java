package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.ranks;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.AuraAvatarView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;

/** Fills an item_rank_row (list rows and the pinned row share it). */
public final class RankRows {

    private RankRows() { }

    @NonNull
    public static String rankText(@NonNull dto.BoardEntry e) {
        return e.rank == null ? "–" : String.valueOf(e.rank);
    }

    /** The name to show: "A member" for people who hide themselves. */
    @NonNull
    public static String name(@NonNull Context ctx, @NonNull dto.BoardEntry e) {
        if (e.user == null || e.user.hidden || e.user.displayName == null) {
            return ctx.getString(R.string.ranks_hidden_name);
        }
        return e.user.displayName;
    }

    public static void avatar(@NonNull AuraAvatarView view, @NonNull dto.BoardEntry e) {
        if (e.user == null || e.user.hidden) {
            view.setAvatar(null);                       // neutral avatar
            return;
        }
        view.setAvatar(e.user.avatar != null ? e.user.avatar : e.user.photoUrl);
    }

    /** -1 down, 0 same / unknown, +1 up since last week. */
    public static int change(@NonNull dto.BoardEntry e) {
        if (e.previousRank == null || e.rank == null) return 0;
        return Integer.compare(e.previousRank, e.rank);
    }

    /**
     * List rows keep the card segment BoardAdapter gives them; the pinned copy becomes the
     * Stitch "You" plate: teal-soft, "#142", a 28dp avatar with a deep-teal/40 hairline, "You".
     */
    public static void bind(@NonNull View row, @NonNull dto.BoardEntry e, boolean pinned) {
        Context ctx = row.getContext();
        TextView number = row.findViewById(R.id.rank_number);
        number.setText(pinned ? ctx.getString(R.string.ranks_pinned_rank, rankText(e)) : rankText(e));
        number.setTextColor(androidx.core.content.ContextCompat.getColor(ctx,
                pinned ? R.color.on_secondary_container : R.color.text_muted));
        avatar(row.findViewById(R.id.rank_avatar), e);
        String name = name(ctx, e);
        ((TextView) row.findViewById(R.id.rank_name)).setText(pinned ? ctx.getString(R.string.ranks_you) : name);
        ((TextView) row.findViewById(R.id.rank_total)).setText(Numbers.format(e.total));
        row.findViewById(R.id.rank_you).setVisibility(e.isMe && !pinned ? View.VISIBLE : View.GONE);

        TextView move = row.findViewById(R.id.rank_change);
        int change = change(e);
        move.setVisibility(change == 0 ? View.GONE : View.VISIBLE);
        if (change != 0) {
            int by = Math.abs(e.previousRank - e.rank);
            move.setText(ctx.getString(change > 0 ? R.string.rank_up : R.string.rank_down, by));
            move.setTextColor(androidx.core.content.ContextCompat.getColor(ctx,
                    change > 0 ? R.color.amber : R.color.brick));
        }

        View frame = row.findViewById(R.id.rank_avatar_frame);
        float dp = ctx.getResources().getDisplayMetrics().density;
        android.view.ViewGroup.LayoutParams lp = frame.getLayoutParams();
        lp.width = lp.height = (int) ((pinned ? 28 : 32) * dp);
        frame.setLayoutParams(lp);
        if (pinned) {
            row.setBackgroundResource(R.drawable.bg_k_pinned);
            android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
            ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            ring.setColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.surface_lowest));
            ring.setStroke((int) dp, androidx.core.content.ContextCompat.getColor(row.getContext(), R.color.k_teal_40));
            frame.setBackground(ring);
            row.setPadding((int) (16 * dp), (int) (12 * dp), (int) (16 * dp), (int) (12 * dp));
            row.setElevation(6 * dp);
            row.setOutlineSpotShadowColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.k_shadow_card));
        } else {
            frame.setBackgroundResource(R.drawable.bg_k_avatar_disc);
        }

        String cd = ctx.getString(R.string.ranks_row_cd, rankText(e), name, Numbers.format(e.total));
        if (change != 0) {
            cd += ". " + ctx.getString(change > 0 ? R.string.ranks_change_up : R.string.ranks_change_down,
                    e.previousRank);
        }
        if (e.isMe) cd = ctx.getString(R.string.ranks_you) + ". " + cd;
        row.setContentDescription(cd);
        row.setFocusable(true);
    }
}
