package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.ranks.RankRows;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;

/**
 * The top 3 on a paper plate: second · first · third, standing on blocks of
 * different heights. Rings are gold / silver / bronze by RANK, so two people
 * tied for first both get gold. Fewer than three people leaves the places
 * empty. TalkBack reads it in rank order: "1st, Ali, 1,200 …".
 */
public class PodiumView extends LinearLayout {

    private final Place[] places = new Place[3];   // index = position in the list (0 = first)

    public PodiumView(@NonNull Context context) {
        this(context, null);
    }

    public PodiumView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_podium, this, true);
        places[0] = new Place(R.id.podium_first, R.id.podium_first_ring, R.id.podium_first_avatar,
                R.id.podium_first_name, R.id.podium_first_total, R.id.podium_first_step);
        places[1] = new Place(R.id.podium_second, R.id.podium_second_ring, R.id.podium_second_avatar,
                R.id.podium_second_name, R.id.podium_second_total, R.id.podium_second_step);
        places[2] = new Place(R.id.podium_third, R.id.podium_third_ring, R.id.podium_third_avatar,
                R.id.podium_third_name, R.id.podium_third_total, R.id.podium_third_step);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    /** The board's first (up to) three entries, in order. */
    public void bind(@NonNull List<dto.BoardEntry> top) {
        StringBuilder cd = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            dto.BoardEntry e = i < top.size() ? top.get(i) : null;
            places[i].bind(e);
            if (e != null) {
                if (cd.length() > 0) cd.append(". ");
                cd.append(getContext().getString(R.string.ranks_podium_cd,
                        RankRows.rankText(e), RankRows.name(getContext(), e), Numbers.format(e.total)));
            }
        }
        setContentDescription(cd);
    }

    /** Stitch rings by RANK: 1 gold, 2 deep-teal/60, 3+ hairline (ties share a colour). */
    @DrawableRes
    public static int ringFor(@Nullable Integer rank) {
        if (rank == null || rank >= 3) return R.drawable.ring_k_hairline;
        return rank == 1 ? R.drawable.ring_k_gold : R.drawable.ring_k_teal;
    }

    @DrawableRes
    public static int badgeFor(@Nullable Integer rank) {
        if (rank == null || rank >= 3) return R.drawable.bg_k_badge_hairline;
        return rank == 1 ? R.drawable.bg_k_badge_gold : R.drawable.bg_k_badge_teal;
    }

    private final class Place {
        final View column, ring;
        final AuraAvatarView avatar;
        final TextView name, total, step;
        final android.graphics.Typeface regular;

        Place(int column, int ring, int avatar, int name, int total, int step) {
            this.column = findViewById(column);
            this.ring = findViewById(ring);
            this.avatar = findViewById(avatar);
            this.name = findViewById(name);
            this.total = findViewById(total);
            this.step = findViewById(step);
            this.regular = this.name.getTypeface();
        }

        void bind(@Nullable dto.BoardEntry e) {
            column.setVisibility(e == null ? INVISIBLE : VISIBLE);
            if (e == null) return;
            ring.setBackgroundResource(ringFor(e.rank));
            RankRows.avatar(avatar, e);
            name.setText(RankRows.name(getContext(), e));
            // Bold for you; built from the original face so a recycled podium un-bolds.
            if (e.isMe) name.setTypeface(regular, android.graphics.Typeface.BOLD);
            else name.setTypeface(regular);
            total.setText(Numbers.format(e.total));
            step.setText(RankRows.rankText(e));
            step.setBackgroundResource(badgeFor(e.rank));
        }
    }
}
