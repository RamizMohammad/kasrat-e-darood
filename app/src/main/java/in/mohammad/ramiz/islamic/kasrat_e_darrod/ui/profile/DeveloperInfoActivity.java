package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.profile;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.google.android.material.snackbar.Snackbar;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.BuildConfig;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Motion;

/** P3 · About (screen 33): the app, who made it, how to reach him, privacy. */
public class DeveloperInfoActivity extends BaseActivity {

    private static final String EMAIL = "ramizanas6@gmail.com";
    private static final String WEBSITE = "https://mohammadramiz.in";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_developer_info);
        EdgeInsets.padSystemBars(findViewById(R.id.root), false);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        ((TextView) findViewById(R.id.about_version))
                .setText(getString(R.string.me_version, BuildConfig.VERSION_NAME));

        LinearLayout people = findViewById(R.id.about_people);
        addRow(people, R.drawable.ic_person, R.string.about_developer, getString(R.string.about_developer_name), null);
        addRow(people, R.drawable.ic_mail, R.string.contact, EMAIL,
                v -> open(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + EMAIL))
                        .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))));
        addRow(people, R.drawable.ic_globe, R.string.about_website, "mohammadramiz.in",
                v -> open(new Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE))));

        if (savedInstanceState == null) Motion.enterStack(findViewById(R.id.about_column));
    }

    private void addRow(@NonNull LinearLayout parent, @DrawableRes int icon, @StringRes int label,
                        @NonNull String value, View.OnClickListener click) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_menu_row, parent, false);
        ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.row_label)).setText(label);
        TextView v = row.findViewById(R.id.row_value);
        v.setVisibility(View.VISIBLE);
        v.setText(value);
        v.setTextDirection(View.TEXT_DIRECTION_LTR);
        if (click != null) {
            row.setOnClickListener(click);
        } else {
            row.setClickable(false);
            row.findViewById(R.id.row_chevron).setVisibility(View.GONE);
        }
        parent.addView(row);
    }

    private void open(@NonNull Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Snackbar.make(findViewById(R.id.root), R.string.about_no_app, Snackbar.LENGTH_SHORT).show();
        }
    }
}
