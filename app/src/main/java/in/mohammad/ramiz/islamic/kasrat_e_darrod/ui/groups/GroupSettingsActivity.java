package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.groups;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.MainActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.AuraAvatarView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.ChoiceSheet;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.GroupAvatarView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.KasratDialog;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.PopMenu;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Phase 6b · Circle settings (Stitch circle_settings_teal).
 *
 * Cards of rows, each opening its own editor and saving straight away (PATCH /groups/{id}
 * with just that field): Details (emblem, name, description), Goal &amp; Privacy (weekly goal,
 * privacy, who can join, board). Invite: the code, Regenerate, share. Members: role and a
 * dots menu (role, remove; approve or decline a request). Announcement: the pinned note.
 * Danger zone: Leave, and Delete for the owner (after typing the circle's name).
 * Owner and admins see all of it; others see Invite, Members and Leave.
 */
public class GroupSettingsActivity extends BaseActivity {

    private static final String EXTRA_ID = "group_id";
    private static final long MAX_GOAL = 1_000_000_000L;
    private static final long[] GOALS = {25_000, 50_000, 100_000};

    @NonNull
    public static Intent intent(@NonNull Context ctx, @NonNull String groupId) {
        return new Intent(ctx, GroupSettingsActivity.class).putExtra(EXTRA_ID, groupId);
    }

    private String groupId;
    @Nullable private dto.GroupDetail group;
    @Nullable private String me;
    private EditText note;
    private TextView noteSave;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_settings);
        EdgeInsets.padSystemBars(findViewById(R.id.root), true);
        groupId = getIntent().getStringExtra(EXTRA_ID);
        if (groupId == null) {
            finish();
            return;
        }
        me = new TokenStore(this).userId();
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        note = findViewById(R.id.gs_note);
        noteSave = findViewById(R.id.gs_note_save);
        noteSave.setOnClickListener(v -> saveNote());
        findViewById(R.id.gs_regenerate).setOnClickListener(v -> confirmRegenerate());
        findViewById(R.id.gs_share).setOnClickListener(v -> {
            if (group != null && group.inviteCode != null) GroupActivity.shareCode(this, group.name, group.inviteCode);
        });
        load();
    }

    private void load() {
        ApiClient.get(this).group(groupId).enqueue(new Callback<dto.GroupDetail>() {
            @Override
            public void onResponse(@NonNull Call<dto.GroupDetail> call, @NonNull Response<dto.GroupDetail> r) {
                if (isFinishing()) return;
                if (r.isSuccessful() && r.body() != null && r.body().isMember()) {
                    bind(r.body());
                    loadMembers();
                } else {
                    Snackbar.make(findViewById(R.id.root), Groups.error(GroupSettingsActivity.this, r), Snackbar.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<dto.GroupDetail> call, @NonNull Throwable t) {
                if (isFinishing()) return;
                Snackbar.make(findViewById(R.id.root), R.string.error_network, Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.action_try_again, v -> load()).show();
            }
        });
    }

    private void bind(@NonNull dto.GroupDetail g) {
        group = g;
        findViewById(R.id.gs_scroll).setVisibility(View.VISIBLE);
        boolean manage = g.canManage();
        findViewById(R.id.gs_details_section).setVisibility(manage ? View.VISIBLE : View.GONE);
        findViewById(R.id.gs_goal_section).setVisibility(manage ? View.VISIBLE : View.GONE);
        findViewById(R.id.gs_note_section).setVisibility(manage ? View.VISIBLE : View.GONE);
        if (manage) {
            bindDetails(g);
            bindGoal(g);
            String text = g.announcement != null && g.announcement.text != null ? g.announcement.text : "";
            if (!note.hasFocus()) note.setText(text);
        }
        boolean code = g.inviteCode != null && !g.inviteCode.isEmpty();
        findViewById(R.id.gs_invite_section).setVisibility(code ? View.VISIBLE : View.GONE);
        if (code) ((TextView) findViewById(R.id.gs_code)).setText(g.inviteCode);
        findViewById(R.id.gs_regenerate).setVisibility(manage ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.gs_members_total)).setText(getString(R.string.circle_members_total,
                Numbers.format(g.memberCount)));
        bindDanger(g);
    }

    // ── Rows ────────────────────────────────────────────────────────────────

    @NonNull
    private View row(@NonNull LinearLayout card, @StringRes int label, boolean greyLabel, @Nullable CharSequence sub,
                     @Nullable CharSequence value, @Nullable View.OnClickListener click) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_k_setting_row, card, false);
        TextView l = row.findViewById(R.id.row_label);
        l.setText(label);
        if (greyLabel) l.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        TextView s = row.findViewById(R.id.row_sub);
        s.setVisibility(sub == null ? View.GONE : View.VISIBLE);
        s.setText(sub);
        TextView v = row.findViewById(R.id.row_value);
        v.setText(value);
        if (click != null) {
            row.setOnClickListener(x -> {
                if (!busy) click.onClick(x);
            });
        } else {
            row.findViewById(R.id.row_caret).setVisibility(View.GONE);
            row.setBackground(null);
        }
        card.addView(row);
        return row;
    }

    private void bindDetails(@NonNull dto.GroupDetail g) {
        LinearLayout card = findViewById(R.id.gs_details);
        card.removeAllViews();
        View emblem = row(card, R.string.circle_emblem, true, null, null, v -> editEmblem());
        GroupAvatarView av = emblem.findViewById(R.id.row_emblem);
        av.setVisibility(View.VISIBLE);
        av.setGroup(g.avatar, g.id, g.isGlobal);
        row(card, R.string.circles_name_label, true, null, g.name, v -> editText(true));
        boolean hasDesc = g.description != null && !g.description.isEmpty();
        View desc = row(card, R.string.circles_desc_label, true, null,
                hasDesc ? g.description : getString(R.string.circle_add_description), v -> editText(false));
        TextView dv = desc.findViewById(R.id.row_value);
        dv.setTextAppearance(R.style.TextAppearance_K_BodySm);
        dv.setTextColor(ContextCompat.getColor(this, hasDesc ? R.color.text_primary : R.color.text_muted));
    }

    private void bindGoal(@NonNull dto.GroupDetail g) {
        LinearLayout card = findViewById(R.id.gs_goal);
        card.removeAllViews();
        boolean hasGoal = g.weeklyGoal != null && g.weeklyGoal > 0;
        View goal = row(card, R.string.circle_weekly_goal, false, getString(R.string.circle_resets),
                hasGoal ? Numbers.format(g.weeklyGoal) : getString(R.string.groups_goal_none), v -> editGoal());
        if (hasGoal) {
            TextView gv = goal.findViewById(R.id.row_value);
            gv.setTextAppearance(R.style.TextAppearance_K_TitleMd);
            gv.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        }
        boolean pub = g.isPublic();
        row(card, R.string.circle_privacy, false, getString(pub ? R.string.circles_public_sub : R.string.circles_private_sub),
                getString(pub ? R.string.groups_public_title : R.string.groups_private_title), v -> editPrivacy());
        boolean approval = "approval".equals(g.joinRule);
        row(card, R.string.circle_who_joins, false,
                getString(approval ? R.string.groups_rule_approval_sub : R.string.groups_rule_code_sub),
                getString(approval ? R.string.circle_join_approval_short : R.string.circle_join_code_short), v -> editJoinRule());
        row(card, R.string.circle_board, false,
                getString(g.totalOnly ? R.string.groups_total_only_sub : R.string.circle_board_full_sub),
                getString(g.totalOnly ? R.string.circle_board_total : R.string.circle_board_full), v -> editBoard());
    }

    // ── Editors ─────────────────────────────────────────────────────────────

    private void editEmblem() {
        if (group == null) return;
        BottomSheetDialog d = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_k_emblem, null, false);
        GroupAvatarPicker picker = v.findViewById(R.id.emblem_picker);
        GroupAvatarView.Spec start = GroupAvatarView.Spec.of(group.avatar, group.id);
        picker.setSpec(start);
        v.findViewById(R.id.emblem_save).setOnClickListener(x -> {
            String a = picker.spec().encode();
            d.dismiss();
            if (a.equals(start.encode())) return;
            JsonObject p = new JsonObject();
            p.addProperty("avatar", a);
            patch(p);
        });
        d.setContentView(v);
        d.show();
    }

    private void editText(boolean isName) {
        if (group == null) return;
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_k_text, null, false);
        TextInputLayout layout = v.findViewById(R.id.text_layout);
        TextInputEditText input = v.findViewById(R.id.text_input);
        int max = isName ? 40 : 160;
        layout.setCounterMaxLength(max);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
        if (!isName) input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        String old = isName ? group.name : (group.description == null ? "" : group.description);
        input.setText(old);
        if (old != null) input.setSelection(input.length());
        AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle(isName ? R.string.circles_name_label : R.string.circles_desc_label)
                .setView(v)
                .setPositiveButton(R.string.action_save, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        d.setOnShowListener(x -> d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(b -> {
            String t = Groups.clean(input.getText());
            if (isName && !Groups.validName(t)) {
                layout.setError(getString(R.string.groups_err_name));
                return;
            }
            d.dismiss();
            if (t.equals(Groups.clean(old))) return;
            JsonObject p = new JsonObject();
            p.addProperty(isName ? "name" : "description", t);
            patch(p);
        }));
        d.show();
    }

    private void editGoal() {
        if (group == null) return;
        long now = group.weeklyGoal != null ? group.weeklyGoal : 0;
        ChoiceSheet s = ChoiceSheet.with(this).title(R.string.circle_weekly_goal).subtitle(R.string.circle_resets);
        boolean preset = now == 0;
        for (long g : GOALS) {
            preset |= g == now;
            s.option(R.drawable.ic_ph_trophy, goalLabel(g), null, g == now, () -> setGoal(g));
        }
        s.option(R.drawable.ic_ph_pencil_simple, getString(R.string.circle_goal_custom),
                preset ? null : Numbers.format(now), !preset, this::askGoal);
        s.option(R.drawable.ic_ph_minus, getString(R.string.groups_goal_none), null, now == 0, () -> setGoal(0));
        s.show();
    }

    @NonNull
    private String goalLabel(long g) {
        return g >= 100_000 && g % 100_000 == 0
                ? getString(R.string.circles_goal_lakh, (int) (g / 100_000))
                : getString(R.string.circles_goal_k, (int) (g / 1000));
    }

    private void askGoal() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_number, null, false);
        TextInputLayout layout = v.findViewById(R.id.number_layout);
        TextInputEditText input = v.findViewById(R.id.number_input);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
        layout.setPlaceholderText(null);
        if (group != null && group.weeklyGoal != null && group.weeklyGoal > 0) input.setText(String.valueOf(group.weeklyGoal));
        AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.circle_weekly_goal)
                .setView(v)
                .setPositiveButton(R.string.action_save, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        d.setOnShowListener(x -> d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(b -> {
            String t = String.valueOf(input.getText()).trim();
            long n;
            try {
                n = t.isEmpty() ? 0 : Long.parseLong(t);
            } catch (NumberFormatException e) {
                n = -1;
            }
            if (n < 0 || n > MAX_GOAL) {
                layout.setError(getString(R.string.groups_err_goal));
                return;
            }
            d.dismiss();
            setGoal(n);
        }));
        d.show();
    }

    private void setGoal(long n) {
        if (group == null) return;
        long old = group.weeklyGoal != null ? group.weeklyGoal : 0;
        if (n == old) return;
        JsonObject p = new JsonObject();
        if (n <= 0) p.add("weekly_goal", JsonNull.INSTANCE);
        else p.addProperty("weekly_goal", n);
        patch(p);
    }

    private void editPrivacy() {
        if (group == null) return;
        boolean pub = group.isPublic();
        ChoiceSheet.with(this).title(R.string.circle_privacy)
                .option(R.drawable.ic_ph_lock, R.string.groups_private_title, R.string.circles_private_sub, !pub,
                        () -> field("privacy", "private", pub))
                .option(R.drawable.ic_ph_globe, R.string.groups_public_title, R.string.circles_public_sub, pub,
                        () -> field("privacy", "public", !pub))
                .show();
    }

    private void editJoinRule() {
        if (group == null) return;
        boolean approval = "approval".equals(group.joinRule);
        ChoiceSheet.with(this).title(R.string.circle_who_joins)
                .option(R.drawable.ic_ph_users_three, R.string.groups_rule_code_title, R.string.groups_rule_code_sub, !approval,
                        () -> field("join_rule", "code", approval))
                .option(R.drawable.ic_ph_check_circle, R.string.groups_rule_approval_title, R.string.groups_rule_approval_sub,
                        approval, () -> field("join_rule", "approval", !approval))
                .show();
    }

    private void editBoard() {
        if (group == null) return;
        boolean only = group.totalOnly;
        ChoiceSheet.with(this).title(R.string.circle_board)
                .option(R.drawable.ic_ph_ranking, R.string.circle_board_full, R.string.circle_board_full_sub, !only,
                        () -> flag(false, only))
                .option(R.drawable.ic_ph_chart_bar, R.string.circle_board_total, R.string.groups_total_only_sub, only,
                        () -> flag(true, !only))
                .show();
    }

    private void field(@NonNull String key, @NonNull String value, boolean changes) {
        if (!changes) return;
        JsonObject p = new JsonObject();
        p.addProperty(key, value);
        patch(p);
    }

    private void flag(boolean totalOnly, boolean changes) {
        if (!changes) return;
        JsonObject p = new JsonObject();
        p.addProperty("total_only", totalOnly);
        patch(p);
    }

    /** PATCH just these fields; the reply redraws the page. */
    private void patch(@NonNull JsonObject body) {
        busy = true;
        ApiClient.get(this).updateGroup(groupId, body).enqueue(new Callback<dto.GroupDetail>() {
            @Override
            public void onResponse(@NonNull Call<dto.GroupDetail> call, @NonNull Response<dto.GroupDetail> r) {
                if (isFinishing()) return;
                busy = false;
                if (r.isSuccessful() && r.body() != null) {
                    Haptics.click(GroupSettingsActivity.this);
                    bind(r.body());
                    snack(getString(R.string.groups_saved));
                } else {
                    Haptics.reject(GroupSettingsActivity.this);
                    snack(Groups.error(GroupSettingsActivity.this, r));
                }
            }

            @Override
            public void onFailure(@NonNull Call<dto.GroupDetail> call, @NonNull Throwable t) {
                if (isFinishing()) return;
                busy = false;
                Haptics.reject(GroupSettingsActivity.this);
                snack(getString(R.string.error_network));
            }
        });
    }

    private void snack(@NonNull CharSequence text) {
        Snackbar.make(findViewById(R.id.root), text, Snackbar.LENGTH_SHORT).show();
    }

    // ── Invite code ─────────────────────────────────────────────────────────

    private void confirmRegenerate() {
        KasratDialog.with(this).icon(R.drawable.ic_ph_arrows_clockwise).title(R.string.circle_regenerate_title)
                .body(R.string.circle_regenerate_body)
                .primary(R.string.circle_regenerate, d -> {
                    d.busy(true);
                    ApiClient.get(this).regenerateInviteCode(groupId).enqueue(new Callback<JsonObject>() {
                        @Override
                        public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> r) {
                            if (isFinishing()) return;
                            if (r.isSuccessful() && r.body() != null && r.body().has("invite_code")
                                    && group != null) {
                                d.dismiss();
                                group.inviteCode = r.body().get("invite_code").getAsString();
                                bind(group);
                                Haptics.click(GroupSettingsActivity.this);
                                snack(getString(R.string.circle_code_new));
                            } else {
                                d.error(Groups.error(GroupSettingsActivity.this, r));
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                            d.error(getString(R.string.error_network));
                        }
                    });
                })
                .secondary(R.string.cancel, null).show();
    }

    // ── Members ─────────────────────────────────────────────────────────────

    private void loadMembers() {
        ApiClient.get(this).groupMembers(groupId).enqueue(new Callback<List<dto.Member>>() {
            @Override
            public void onResponse(@NonNull Call<List<dto.Member>> call, @NonNull Response<List<dto.Member>> r) {
                if (isFinishing()) return;
                if (r.isSuccessful() && r.body() != null) bindMembers(r.body());
                else membersError();
            }

            @Override
            public void onFailure(@NonNull Call<List<dto.Member>> call, @NonNull Throwable t) {
                if (isFinishing()) return;
                membersError();
            }
        });
    }

    private void membersError() {
        LinearLayout card = findViewById(R.id.gs_members);
        card.removeAllViews();
        View r = row(card, R.string.circle_members_error, true, null, getString(R.string.action_try_again), v -> loadMembers());
        ((TextView) r.findViewById(R.id.row_value)).setTextColor(ContextCompat.getColor(this, R.color.amber));
    }

    private static int rank(@Nullable String role) {
        if ("owner".equals(role)) return 0;
        if ("admin".equals(role)) return 1;
        if ("moderator".equals(role)) return 2;
        return 3;
    }

    private void bindMembers(@NonNull List<dto.Member> list) {
        LinearLayout card = findViewById(R.id.gs_members);
        card.removeAllViews();
        // Requests first (they need someone), then owner → admins → moderators → members.
        list.sort((a, b) -> {
            int pa = "pending".equals(a.status) ? -1 : rank(a.role);
            int pb = "pending".equals(b.status) ? -1 : rank(b.role);
            return Integer.compare(pa, pb);
        });
        LayoutInflater inf = LayoutInflater.from(this);
        for (dto.Member m : list) {
            if (m.user == null) continue;
            View row = inf.inflate(R.layout.item_k_member_row, card, false);
            ((AuraAvatarView) row.findViewById(R.id.mem_avatar))
                    .setAvatar(m.user.avatar != null ? m.user.avatar : m.user.photoUrl);
            boolean self = m.user.id != null && m.user.id.equals(me);
            String name = m.user.displayName != null ? m.user.displayName : getString(R.string.ranks_hidden_name);
            ((TextView) row.findViewById(R.id.mem_name)).setText(self ? getString(R.string.circle_member_you, name) : name);
            TextView role = row.findViewById(R.id.mem_role);
            boolean pending = "pending".equals(m.status);
            role.setText(pending ? getString(R.string.circle_member_requested) : getString(Groups.roleLabel(m.role)));
            boolean quiet = !pending && rank(m.role) == 3;
            role.setTextColor(ContextCompat.getColor(this, quiet ? R.color.text_muted : R.color.amber));
            View more = row.findViewById(R.id.mem_more);
            boolean can = canAct(m) && !self;
            more.setVisibility(can ? View.VISIBLE : View.INVISIBLE);
            if (can) more.setOnClickListener(v -> memberMenu(v, m, name));
            card.addView(row);
        }
    }

    /** Owner manages everyone else; admins manage moderators and members; moderators let requests in. */
    private boolean canAct(@NonNull dto.Member m) {
        if (group == null) return false;
        if ("pending".equals(m.status)) return group.canManage() || "moderator".equals(group.role);
        if ("owner".equals(m.role)) return false;
        if (group.isOwner()) return true;
        return "admin".equals(group.role) && rank(m.role) >= 2;
    }

    private void memberMenu(@NonNull View anchor, @NonNull dto.Member m, @NonNull String name) {
        if (group == null || m.user == null || m.user.id == null) return;
        String uid = m.user.id;
        PopMenu menu = PopMenu.at(anchor);
        if ("pending".equals(m.status)) {
            menu.item(R.drawable.ic_ph_check_circle, R.string.circle_member_approve, () -> act(ApiClient.get(this).approveMember(groupId, uid)));
            menu.danger(R.drawable.ic_ph_minus, R.string.circle_member_decline, () -> act(ApiClient.get(this).banMember(groupId, uid)));
            menu.show();
            return;
        }
        if (group.isOwner() && !"admin".equals(m.role)) menu.item(R.drawable.ic_ph_crown, R.string.circle_make_admin, () -> setRole(uid, "admin"));
        if (!"moderator".equals(m.role)) menu.item(R.drawable.ic_ph_certificate, R.string.circle_make_moderator, () -> setRole(uid, "moderator"));
        if (!"member".equals(m.role)) menu.item(R.drawable.ic_ph_user, R.string.circle_make_member, () -> setRole(uid, "member"));
        menu.danger(R.drawable.ic_ph_sign_out, R.string.circle_member_remove, () -> confirmRemove(uid, name));
        menu.show();
    }

    private void setRole(@NonNull String uid, @NonNull String role) {
        JsonObject b = new JsonObject();
        b.addProperty("role", role);
        act(ApiClient.get(this).setMemberRole(groupId, uid, b));
    }

    private void confirmRemove(@NonNull String uid, @NonNull String name) {
        KasratDialog.with(this).icon(R.drawable.ic_ph_sign_out).title(R.string.circle_member_remove)
                .body(getString(R.string.circle_member_remove_body, name)).danger()
                .primary(R.string.circle_member_remove, d -> {
                    d.dismiss();
                    act(ApiClient.get(this).banMember(groupId, uid));
                })
                .secondary(R.string.cancel, null).show();
    }

    /** Run a member change, then reload the circle (count) and its members. */
    private void act(@NonNull Call<dto.Member> call) {
        call.enqueue(new Callback<dto.Member>() {
            @Override
            public void onResponse(@NonNull Call<dto.Member> c, @NonNull Response<dto.Member> r) {
                if (isFinishing()) return;
                if (r.isSuccessful()) {
                    Haptics.click(GroupSettingsActivity.this);
                    load();
                } else {
                    Haptics.reject(GroupSettingsActivity.this);
                    snack(Groups.error(GroupSettingsActivity.this, r));
                }
            }

            @Override
            public void onFailure(@NonNull Call<dto.Member> c, @NonNull Throwable t) {
                if (isFinishing()) return;
                snack(getString(R.string.error_network));
            }
        });
    }

    // ── Announcement ────────────────────────────────────────────────────────

    private void saveNote() {
        if (group == null || busy) return;
        String t = Groups.clean(note.getText());
        String old = group.announcement != null && group.announcement.text != null ? group.announcement.text : "";
        if (t.equals(Groups.clean(old))) {
            note.clearFocus();
            return;
        }
        busy = true;
        noteSave.setEnabled(false);
        JsonObject b = new JsonObject();
        b.addProperty("text", t);
        ApiClient.get(this).postAnnouncement(groupId, b).enqueue(new Callback<dto.Announcement>() {
            @Override
            public void onResponse(@NonNull Call<dto.Announcement> call, @NonNull Response<dto.Announcement> r) {
                if (isFinishing()) return;
                busy = false;
                noteSave.setEnabled(true);
                if (r.isSuccessful()) {
                    Haptics.click(GroupSettingsActivity.this);
                    if (group != null) {
                        dto.Announcement a = r.body() != null ? r.body() : new dto.Announcement();
                        if (a.text == null) a.text = t;
                        group.announcement = t.isEmpty() ? null : a;
                    }
                    note.clearFocus();
                    snack(getString(R.string.circle_note_saved));
                } else {
                    Haptics.reject(GroupSettingsActivity.this);
                    snack(Groups.error(GroupSettingsActivity.this, r));
                }
            }

            @Override
            public void onFailure(@NonNull Call<dto.Announcement> call, @NonNull Throwable t) {
                if (isFinishing()) return;
                busy = false;
                noteSave.setEnabled(true);
                snack(getString(R.string.error_network));
            }
        });
    }

    // ── Leave / delete ──────────────────────────────────────────────────────

    private void bindDanger(@NonNull dto.GroupDetail g) {
        LinearLayout danger = findViewById(R.id.gs_danger);
        danger.removeAllViews();
        dangerRow(danger, R.string.circle_leave, R.drawable.ic_ph_sign_out, v -> {
            if (g.isOwner()) snack(getString(R.string.groups_err_owner_leave));
            else confirmLeave(g);
        });
        if (g.isOwner()) dangerRow(danger, R.string.circle_delete, R.drawable.ic_ph_trash, v -> confirmDelete(g));
    }

    private void dangerRow(@NonNull LinearLayout card, @StringRes int label, @DrawableRes int icon,
                           @NonNull View.OnClickListener click) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_k_setting_row, card, false);
        int red = ContextCompat.getColor(this, R.color.k_muted_red);
        TextView l = row.findViewById(R.id.row_label);
        l.setText(label);
        l.setTextColor(red);
        row.findViewById(R.id.row_value).setVisibility(View.INVISIBLE);
        ImageView caret = row.findViewById(R.id.row_caret);
        caret.setImageResource(icon);
        caret.setImageTintList(android.content.res.ColorStateList.valueOf(red));
        row.setOnClickListener(click);
        card.addView(row);
    }

    private void confirmLeave(@NonNull dto.GroupDetail g) {
        KasratDialog.with(this).icon(R.drawable.ic_ph_sign_out).title(R.string.circle_leave_title)
                .body(getString(R.string.circle_leave_body, g.name)).danger()
                .primary(R.string.circle_leave, d -> {
                    d.busy(true);
                    ApiClient.get(this).leaveGroup(groupId).enqueue(new Callback<dto.OkResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<dto.OkResponse> call, @NonNull Response<dto.OkResponse> r) {
                            if (r.isSuccessful()) {
                                d.dismiss();
                                backToCircles();
                            } else {
                                d.error(Groups.error(GroupSettingsActivity.this, r));
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<dto.OkResponse> call, @NonNull Throwable t) {
                            d.error(getString(R.string.error_network));
                        }
                    });
                })
                .secondary(R.string.cancel, null).show();
    }

    private void confirmDelete(@NonNull dto.GroupDetail g) {
        KasratDialog.with(this).icon(R.drawable.ic_ph_trash).title(R.string.circle_delete_title)
                .body(getString(R.string.circle_delete_body, g.name))
                .textField(R.string.groups_delete_hint).danger()
                .primary(R.string.circle_delete, d -> {
                    if (!Groups.clean(d.text()).equalsIgnoreCase(Groups.clean(g.name))) {
                        d.error(getString(R.string.groups_err_name_mismatch));
                        return;
                    }
                    d.busy(true);
                    JsonObject body = new JsonObject();
                    body.addProperty("confirm_name", Groups.clean(d.text()));
                    ApiClient.get(this).deleteGroup(groupId, body).enqueue(new Callback<dto.OkResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<dto.OkResponse> call, @NonNull Response<dto.OkResponse> r) {
                            if (r.isSuccessful()) {
                                d.dismiss();
                                backToCircles();
                            } else {
                                d.error(Groups.error(GroupSettingsActivity.this, r));
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<dto.OkResponse> call, @NonNull Throwable t) {
                            d.error(getString(R.string.error_network));
                        }
                    });
                })
                .secondary(R.string.cancel, null).show();
    }

    /** Close this and the circle's home, and land on Circles. */
    private void backToCircles() {
        Haptics.click(this);
        startActivity(new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_GROUPS)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
}
