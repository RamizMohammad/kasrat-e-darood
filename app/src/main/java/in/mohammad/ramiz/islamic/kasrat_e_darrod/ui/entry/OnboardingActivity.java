package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.entry;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.databinding.ActivityOnboardingBinding;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.avatar.AvatarStudioActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.nav.Nav;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Motion;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.AppPrefs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Onboarding (Stitch onboarding_1–3_teal), shown once per device after sign-in:
 * three slides (intention · never lose count · recite together), each with a
 * quote card, then the avatar studio. Skip goes straight home; the avatar can be
 * made later from Me.
 */
public class OnboardingActivity extends BaseActivity {

    private static final String STATE_SLIDE = "slide";

    private static final int[] TAGS = {R.string.onb_1_tag, R.string.onb_2_tag, R.string.onb_3_tag};
    private static final int[] TITLES = {R.string.onb_1_title, R.string.onb_2_title, R.string.onb_3_title};
    private static final int[] BODIES = {R.string.onb_1_body, R.string.onb_2_body, R.string.onb_3_body};
    private static final int[] REFS = {R.string.onb_1_ref, R.string.onb_2_ref, R.string.onb_3_ref};
    private static final int[] ARABIC = {R.string.onb_1_ar, R.string.onb_2_ar, R.string.onb_3_ar};
    private static final int[] TRANSLATIONS = {R.string.onb_1_trans, R.string.onb_2_trans, R.string.onb_3_trans};

    private ActivityOnboardingBinding b;
    private View[] dots;
    private int slide;
    private boolean saving;

    private final ActivityResultLauncher<Intent> avatarStudio = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                String spec = result.getData() == null ? null
                        : result.getData().getStringExtra(AvatarStudioActivity.EXTRA_SPEC);
                if (result.getResultCode() == RESULT_OK && spec != null) saveAvatar(spec);
                else finishOnboarding();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padSystemBars(b.root, false);
        dots = new View[]{b.dot0, b.dot1, b.dot2};
        slide = savedInstanceState != null ? savedInstanceState.getInt(STATE_SLIDE, 0) : 0;

        b.btnSkip.setOnClickListener(v -> finishOnboarding());
        b.btnNext.setOnClickListener(v -> {
            if (saving) return;
            Haptics.tick(this);
            if (slide < TITLES.length - 1) {
                show(slide + 1, true);
            } else {
                avatarStudio.launch(AvatarStudioActivity.intent(this, new TokenStore(this).avatar(), false));
            }
        });
        show(slide, false);
    }

    private void show(int index, boolean animate) {
        slide = index;
        bind(index);
        float dp = getResources().getDisplayMetrics().density;
        for (int i = 0; i < dots.length; i++) {
            boolean on = i == index;
            dots[i].setSelected(on);
            dots[i].getLayoutParams().width = (int) (dp * (on ? 24 : 6));
            dots[i].requestLayout();
        }
        boolean last = index == TITLES.length - 1;
        b.btnNext.setText(last ? R.string.onb_start : R.string.onb_next);
        if (animate && Motion.enabled()) {
            // Stitch surface-enter: 280ms, cubic-bezier(0.2, 0, 0, 1).
            android.view.animation.Interpolator enter = android.view.animation.AnimationUtils
                    .loadInterpolator(this, R.interpolator.k_surface_enter);
            int ms = getResources().getInteger(R.integer.k_motion_enter);
            b.main.setAlpha(0f);
            b.main.setTranslationY(12 * dp);
            b.main.animate().alpha(1f).translationY(0f).setDuration(ms).setInterpolator(enter).start();
        }
    }

    private void bind(int i) {
        b.slideTag.setText(TAGS[i]);
        b.quoteRef.setText(REFS[i]);
        b.quoteAr.setText(ARABIC[i]);
        b.quoteEn.setText(TRANSLATIONS[i]);
        b.slideTitle.setText(TITLES[i]);
        b.slideBody.setText(BODIES[i]);
        b.membersRow.setVisibility(i == 2 ? View.VISIBLE : View.GONE);

        // Emblem: 48dp teal-soft disc with the Open Quran (1), 56dp white disc with "33" (2), none (3).
        float dp = getResources().getDisplayMetrics().density;
        b.art.setVisibility(i == 2 ? View.GONE : View.VISIBLE);
        boolean tasbih = i == 1;
        int size = (int) (dp * (tasbih ? 56 : 48));
        b.art.getLayoutParams().width = size;
        b.art.getLayoutParams().height = size;
        b.art.setBackgroundResource(tasbih ? R.drawable.bg_k_emblem_white : R.drawable.bg_k_emblem_soft);
        b.art.setElevation(tasbih ? 6 * dp : 0);
        b.art.setOutlineSpotShadowColor(getColor(R.color.k_shadow_card));
        b.art.setOutlineAmbientShadowColor(android.graphics.Color.TRANSPARENT);
        b.artIcon.setVisibility(tasbih ? View.GONE : View.VISIBLE);
        b.artCount.setVisibility(tasbih ? View.VISIBLE : View.GONE);
        b.art.requestLayout();
    }

    private void saveAvatar(@NonNull String spec) {
        saving = true;
        b.btnNext.setEnabled(false);
        b.btnSkip.setEnabled(false);
        b.btnNext.setText(R.string.onb_saving);
        ApiClient.get(this).updateMe(dto.UserUpdateRequest.avatar(spec)).enqueue(new Callback<dto.UserDto>() {
            @Override
            public void onResponse(@NonNull Call<dto.UserDto> call, @NonNull Response<dto.UserDto> response) {
                if (response.isSuccessful()) {
                    new TokenStore(OnboardingActivity.this).setAvatar(spec);
                } else {
                    Toast.makeText(OnboardingActivity.this, R.string.onb_avatar_failed, Toast.LENGTH_LONG).show();
                }
                finishOnboarding();
            }

            @Override
            public void onFailure(@NonNull Call<dto.UserDto> call, @NonNull Throwable t) {
                Toast.makeText(OnboardingActivity.this, R.string.onb_avatar_failed, Toast.LENGTH_LONG).show();
                finishOnboarding();
            }
        });
    }

    private void finishOnboarding() {
        new AppPrefs(this).markOnboardingDone();
        Nav.next(this);
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_SLIDE, slide);
    }
}
