package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.profile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth.LoginActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.KasratDialog;

/** "Sign out?" and, on yes, forget the session and the Me copy, then go to sign in. */
public final class SignOut {

    private SignOut() { }

    public static void confirm(@NonNull Activity a) {
        KasratDialog.with(a).icon(R.drawable.ic_ph_sign_out).title(R.string.me_logout_title)
                .body(R.string.me_logout_body).danger()
                .primary(R.string.me_menu_logout, d -> {
                    d.dismiss();
                    now(a);
                })
                .secondary(R.string.cancel, null).show();
    }

    public static void now(@NonNull Activity a) {
        TokenStore store = new TokenStore(a);
        // Revoke the refresh token on the server too, so a copy of it (from a
        // backup, another app, a stolen phone image) stops working. Fire and forget.
        String refresh = store.refreshToken();
        if (refresh != null) {
            in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient.get(a)
                    .logout(new in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto.RefreshRequest(refresh))
                    .enqueue(new retrofit2.Callback<Void>() {
                        @Override public void onResponse(@NonNull retrofit2.Call<Void> c, @NonNull retrofit2.Response<Void> r) { }
                        @Override public void onFailure(@NonNull retrofit2.Call<Void> c, @NonNull Throwable t) { }
                    });
        }
        store.clear();
        a.getSharedPreferences(MeFragment.PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        Intent i = new Intent(a, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(i);
    }
}
