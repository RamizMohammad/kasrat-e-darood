package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.groups;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.Consumer;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.GroupAvatarView;

/**
 * Emblem builder (Stitch new_circle_teal): a live 96dp preview and three rows of 32dp
 * options. Shape and pattern options are ivory discs with a hairline (deep teal when
 * chosen) holding a small glyph; colour options are the disc colours themselves, the
 * chosen one wrapped in a 2dp deep-teal ring with a 2dp gap.
 */
public class GroupAvatarPicker extends LinearLayout {

    private final int TEAL, INK, GREY, HAIRLINE, IVORY;
    private final int[] discColors;

    private final GroupAvatarView preview;
    private final LinearLayout marks, colours, patterns;
    private final float dp;
    private GroupAvatarView.Spec spec = new GroupAvatarView.Spec(0, 0, 0);
    @Nullable private Consumer<GroupAvatarView.Spec> listener;

    public GroupAvatarPicker(@NonNull Context context) { this(context, null); }

    public GroupAvatarPicker(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TEAL = androidx.core.content.ContextCompat.getColor(context, R.color.k_emblem_mark);
        INK = androidx.core.content.ContextCompat.getColor(context, R.color.text_primary);
        GREY = androidx.core.content.ContextCompat.getColor(context, R.color.text_muted);
        HAIRLINE = androidx.core.content.ContextCompat.getColor(context, R.color.hairline);
        IVORY = androidx.core.content.ContextCompat.getColor(context, R.color.ink);
        discColors = GroupAvatarView.colors(context);
        setOrientation(VERTICAL);
        dp = getResources().getDisplayMetrics().density;
        LayoutInflater.from(context).inflate(R.layout.view_group_avatar_picker, this, true);
        preview = findViewById(R.id.gap_preview);
        marks = findViewById(R.id.gap_marks);
        colours = findViewById(R.id.gap_colours);
        patterns = findViewById(R.id.gap_patterns);
        build();
    }

    public void setSpec(@NonNull GroupAvatarView.Spec s) {
        spec = s;
        refresh();
    }

    @NonNull public GroupAvatarView.Spec spec() { return spec; }

    public void setOnChanged(@Nullable Consumer<GroupAvatarView.Spec> l) { listener = l; }

    private void build() {
        for (int i = 0; i < GroupAvatarView.MARKS; i++) {
            int m = i;
            marks.addView(option(getContext().getString(R.string.groups_avatar_mark_n, i + 1),
                    GroupAvatarView.Bare.MARK, v -> pick(spec.withMark(m))));
        }
        for (int i = 0; i < GroupAvatarView.COLORS.length; i++) {
            int c = i;
            colours.addView(option(getContext().getString(R.string.groups_avatar_colour_n, i + 1),
                    null, v -> pick(spec.withColor(c))));
        }
        for (int i = 0; i < GroupAvatarView.PATTERNS; i++) {
            int p = i;
            patterns.addView(option(getContext().getString(R.string.groups_avatar_pattern_n, i + 1),
                    GroupAvatarView.Bare.PATTERN, v -> pick(spec.withPattern(p))));
        }
        refresh();
    }

    /** A 36dp slot (room for the colour ring) holding a 32dp option. */
    @NonNull
    private View option(@NonNull String cd, @Nullable GroupAvatarView.Bare glyph, @NonNull OnClickListener click) {
        FrameLayout slot = new FrameLayout(getContext());
        int slotSize = (int) (40 * dp);
        LayoutParams lp = new LayoutParams(slotSize, slotSize);
        lp.setMarginStart((int) (2 * dp));
        slot.setLayoutParams(lp);
        FrameLayout disc = new FrameLayout(getContext());
        slot.addView(disc, new FrameLayout.LayoutParams((int) (32 * dp), (int) (32 * dp), Gravity.CENTER));
        if (glyph != null) {
            GroupAvatarView g = new GroupAvatarView(getContext());
            g.setBare(glyph, GREY);
            disc.addView(g, new FrameLayout.LayoutParams((int) (16 * dp), (int) (16 * dp), Gravity.CENTER));
        }
        slot.setContentDescription(cd);
        slot.setFocusable(true);
        slot.setOnClickListener(v -> {
            Haptics.tick(getContext());
            click.onClick(v);
        });
        return slot;
    }

    private void pick(@NonNull GroupAvatarView.Spec s) {
        spec = s;
        refresh();
        if (listener != null) listener.accept(s);
    }

    private void refresh() {
        preview.set(spec);
        for (int i = 0; i < marks.getChildCount(); i++) {
            glyphOption(marks.getChildAt(i), spec.withMark(i), i == spec.mark);
        }
        for (int i = 0; i < colours.getChildCount(); i++) {
            colourOption(colours.getChildAt(i), discColors[i], i == spec.color);
        }
        for (int i = 0; i < patterns.getChildCount(); i++) {
            glyphOption(patterns.getChildAt(i), spec.withPattern(i), i == spec.pattern);
        }
    }

    private void glyphOption(@NonNull View slot, @NonNull GroupAvatarView.Spec s, boolean on) {
        FrameLayout disc = (FrameLayout) ((FrameLayout) slot).getChildAt(0);
        disc.setBackground(oval(IVORY, on ? TEAL : HAIRLINE, 1));
        GroupAvatarView g = (GroupAvatarView) disc.getChildAt(0);
        g.set(s);
        g.setBare(g == null ? GroupAvatarView.Bare.MARK : barKind(slot), on ? INK : GREY);
        slot.setSelected(on);
    }

    @NonNull
    private GroupAvatarView.Bare barKind(@NonNull View slot) {
        return slot.getParent() == patterns ? GroupAvatarView.Bare.PATTERN : GroupAvatarView.Bare.MARK;
    }

    private void colourOption(@NonNull View slot, int color, boolean on) {
        FrameLayout disc = (FrameLayout) ((FrameLayout) slot).getChildAt(0);
        disc.setBackground(oval(color, HAIRLINE, 1));
        // ring-2 ring-deep-teal ring-offset-2: a 2dp teal ring 2dp outside the disc.
        slot.setBackground(on ? oval(0x00000000, TEAL, 2) : null);
        slot.setSelected(on);
    }

    @NonNull
    private GradientDrawable oval(int fill, int stroke, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        d.setStroke((int) (strokeDp * dp), stroke);
        return d;
    }
}
