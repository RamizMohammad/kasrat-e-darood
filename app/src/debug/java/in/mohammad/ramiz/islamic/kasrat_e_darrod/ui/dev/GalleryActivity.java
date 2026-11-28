package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.dev;

import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.databinding.ActivityGalleryBinding;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.avatar.AvatarStudioActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.ChoiceSheet;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.KasratDialog;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.PopMenu;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;

/** Debug builds only: every v3 style and shared component, to check P0 in each language. */
public class GalleryActivity extends BaseActivity {

    @Override
    protected boolean requiresSession() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityGalleryBinding b = ActivityGalleryBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padSystemBars(b.column, false);

        ((TextInputLayout) b.errorField).setError(getString(R.string.err_email));
        b.avatarSpec.setAvatar("av1:k2.h3.c1.e0.b1.m2.f0.g0.x0.o3.d5");

        b.openDialog.setOnClickListener(v -> KasratDialog.with(this)
                .icon(R.drawable.ic_lock)
                .title(R.string.fp_title_1)
                .body(R.string.fp_sub_1)
                .primary(R.string.fp_send, KasratDialog::dismiss)
                .secondary(R.string.action_cancel, null)
                .show());
        b.openSheet.setOnClickListener(v -> ChoiceSheet.with(this)
                .title(R.string.lang_title)
                .subtitle(R.string.lang_sub)
                .option(0, getString(R.string.lang_en), getString(R.string.lang_en_sub), true, null)
                .option(0, getString(R.string.lang_hi), getString(R.string.lang_hi_sub), false, null)
                .option(0, getString(R.string.lang_ur), getString(R.string.lang_ur_sub), false, null)
                .show());
        b.openMenu.setOnClickListener(v -> PopMenu.at(v)
                .item(R.drawable.ic_eye, R.string.action_continue, () -> { })
                .danger(R.drawable.ic_trash, R.string.edit_discard_confirm, () -> { })
                .show());
        b.openSnack.setOnClickListener(v ->
                Snackbar.make(b.getRoot(), R.string.err_network, Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_continue, x -> { }).show());
        b.openAvatar.setOnClickListener(v ->
                startActivity(AvatarStudioActivity.intent(this, null, true)));
    }
}
