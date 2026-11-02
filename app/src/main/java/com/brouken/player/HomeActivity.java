package com.brouken.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class HomeActivity extends Activity {

    private static final int REQUEST_PICK_VIDEO = 100;
    private Prefs mPrefs;
    private LinearLayout containerRecent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mPrefs = new Prefs(this);
        Utils.setOrientation(this, mPrefs.orientation);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        containerRecent = findViewById(R.id.container_recent_cards);

        LinearLayout cardOpenFile = findViewById(R.id.card_open_file);
        if (cardOpenFile != null) {
            cardOpenFile.setOnClickListener(v -> openVideoFilePicker());
        }

        LinearLayout cardNetwork = findViewById(R.id.card_network_stream);
        if (cardNetwork != null) {
            cardNetwork.setOnClickListener(v -> showNetworkStreamDialog());
        }

        LinearLayout cardTorr = findViewById(R.id.card_torrserver);
        if (cardTorr != null) {
            cardTorr.setOnClickListener(v -> showTorrServerDialog());
        }

        ImageButton btnSettings = findViewById(R.id.home_btn_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> showGlobalSettingsDialog());
        }

        loadRecentCards();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecentCards();
    }

    private void loadRecentCards() {
        if (containerRecent == null) return;
        containerRecent.removeAllViews();

        if (mPrefs.mediaUri != null) {
            View card = createMediaCard(mPrefs.mediaUri, "Последний просмотр", mPrefs.getPosition());
            containerRecent.addView(card);
        } else {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("История пока пуста. Откройте видеофайл или поток из Лампы/TorrServer.");
            emptyTv.setTextColor(Color.parseColor("#94A3B8"));
            emptyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            emptyTv.setPadding(0, 16, 0, 16);
            containerRecent.addView(emptyTv);
        }
    }

    private View createMediaCard(Uri uri, String subtitle, long pos) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 20, 24, 20);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setClickable(true);
        card.setBackgroundResource(R.drawable.card_selector_bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Utils.dpToPx(240), Utils.dpToPx(100));
        lp.setMargins(0, 0, Utils.dpToPx(12), 0);
        card.setLayoutParams(lp);

        TextView titleTv = new TextView(this);
        String name = Utils.getFileName(this, uri);
        if (TextUtils.isEmpty(name)) name = "Медиафайл";
        titleTv.setText(name);
        titleTv.setTextColor(Color.parseColor("#E2E8F0"));
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setMaxLines(2);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(titleTv);

        TextView subTv = new TextView(this);
        subTv.setText(subtitle + (pos > 0 ? " • " + Utils.formatMilis(pos) : ""));
        subTv.setTextColor(Color.parseColor("#10B981"));
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, 8, 0, 0);
        subTv.setLayoutParams(subLp);
        card.addView(subTv);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.setData(uri);
            startActivity(intent);
        });

        card.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().cancel();
            if (hasFocus) {
                v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(150).start();
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
            }
        });

        return card;
    }

    private void openVideoFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Выберите видеофайл"), REQUEST_PICK_VIDEO);
        } catch (Exception e) {
            Toast.makeText(this, "Файловый менеджер недоступен", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            if (requestCode == REQUEST_PICK_VIDEO) {
                Uri videoUri = data.getData();
                Intent intent = new Intent(this, PlayerActivity.class);
                intent.setData(videoUri);
                startActivity(intent);
            }
        }
    }

    private void showNetworkStreamDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.JudAsDialogTheme);
        builder.setTitle("Открыть сетевой поток");

        final EditText input = new EditText(this);
        input.setHint("http:// or https:// stream URL");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        input.setPadding(32, 24, 32, 24);
        builder.setView(input);

        builder.setPositiveButton("Воспроизвести", (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (!TextUtils.isEmpty(url)) {
                Intent intent = new Intent(this, PlayerActivity.class);
                intent.setData(Uri.parse(url));
                startActivity(intent);
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.create().show();
    }

    private void showTorrServerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.JudAsDialogTheme);
        builder.setTitle("TorrServer поток");

        final EditText input = new EditText(this);
        input.setText("http://127.0.0.1:8090/stream");
        input.setTextColor(Color.WHITE);
        input.setPadding(32, 24, 32, 24);
        builder.setView(input);

        builder.setPositiveButton("Открыть поток", (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (!TextUtils.isEmpty(url)) {
                Intent intent = new Intent(this, PlayerActivity.class);
                intent.setData(Uri.parse(url));
                startActivity(intent);
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.create().show();
    }

    private void showGlobalSettingsDialog() {
        String[] mainOptions = new String[] {
                "Цвет субтитров",
                "Размер субтитров",
                "Ориентация экрана",
                "Информация о версии JudAs Player"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.JudAsDialogTheme);
        builder.setTitle("Настройки JudAs Player");
        builder.setItems(mainOptions, (dialog, which) -> {
            if (which == 0) {
                showSubtitleColorDialog();
            } else if (which == 1) {
                showSubtitleSizeDialog();
            } else if (which == 2) {
                showOrientationDialog();
            } else if (which == 3) {
                Toast.makeText(this, "JudAs Player v5.0.0 (Material Design 3 Edition)", Toast.LENGTH_LONG).show();
            }
        });
        builder.create().show();
    }

    private void showSubtitleColorDialog() {
        String[] colors = new String[] { "Жёлтый (Рекомендуется для OLED/HDR)", "Белый (Классический)", "Светло-серый (Комфорт)" };
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.JudAsDialogTheme);
        builder.setTitle("Цвет субтитров");
        builder.setItems(colors, (dialog, which) -> {
            Toast.makeText(this, "Выбран цвет: " + colors[which], Toast.LENGTH_SHORT).show();
        });
        builder.create().show();
    }

    private void showSubtitleSizeDialog() {
        String[] sizes = new String[] { "80% (Мелкий)", "100% (Стандартный)", "120% (Крупный)", "150% (Максимальный)" };
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.JudAsDialogTheme);
        builder.setTitle("Размер субтитров");
        builder.setItems(sizes, (dialog, which) -> {
            Toast.makeText(this, "Выбран размер: " + sizes[which], Toast.LENGTH_SHORT).show();
        });
        builder.create().show();
    }

    private void showOrientationDialog() {
        String[] options = new String[] { "Автоматически (Из видео)", "Альбомная (Landscape)", "Портретная (Portrait)" };
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.JudAsDialogTheme);
        builder.setTitle("Ориентация экрана");
        builder.setItems(options, (dialog, which) -> {
            Toast.makeText(this, "Ориентация: " + options[which], Toast.LENGTH_SHORT).show();
        });
        builder.create().show();
    }
}
