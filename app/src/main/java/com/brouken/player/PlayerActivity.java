package com.brouken.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.UriPermission;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.LocaleList;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Rational;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.CaptioningManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.documentfile.provider.DocumentFile;

import com.brouken.player.dtpv.DoubleTapPlayerView;
import com.brouken.player.dtpv.youtube.YouTubeOverlay;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioListener;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.CaptionStyleCompat;
import com.google.android.exoplayer2.ui.DefaultTimeBar;
import com.google.android.exoplayer2.ui.StyledPlayerControlView;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.ui.TimeBar;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayerActivity extends Activity {

    private PlaybackStateListener playbackStateListener;
    private BroadcastReceiver mReceiver;
    private AudioManager mAudioManager;
    private MediaSessionCompat mediaSession;
    private DefaultTrackSelector trackSelector;
    public static LoudnessEnhancer loudnessEnhancer;

    private CustomStyledPlayerView playerView;
    public static SimpleExoPlayer player;

    private Object mPictureInPictureParamsBuilder;

    public Prefs mPrefs;
    public static BrightnessControl mBrightnessControl;
    public static boolean haveMedia;
    private boolean videoLoading;
    public static boolean controllerVisible;
    public static boolean controllerVisibleFully;
    public static Snackbar snackbar;
    private ExoPlaybackException errorToShow;
    public static int boostLevel = 0;

    private static final int REQUEST_CHOOSER_VIDEO = 1;
    private static final int REQUEST_CHOOSER_SUBTITLE = 2;
    private static final int REQUEST_CHOOSER_SCOPE_DIR = 10;
    public static final int CONTROLLER_TIMEOUT = 3500;
    private static final String ACTION_MEDIA_CONTROL = "media_control";
    private static final String EXTRA_CONTROL_TYPE = "control_type";
    private static final int REQUEST_PLAY = 1;
    private static final int REQUEST_PAUSE = 2;
    private static final int CONTROL_TYPE_PLAY = 1;
    private static final int CONTROL_TYPE_PAUSE = 2;

    private CoordinatorLayout coordinatorLayout;
    private TextView titleView;
    private ImageButton buttonOpen;
    private ImageButton buttonPiP;
    private ImageButton buttonAspectRatio;
    private ImageButton buttonQuality;
    private ImageButton buttonInfo;
    private ImageButton exoPlayPause;
    private ProgressBar loadingProgressBar;
    private StyledPlayerControlView controlView;

    private View osdOverlayLayout;
    private TextView osdTextView;

    private boolean restoreOrientationLock;
    private boolean restorePlayState;
    private boolean play;
    private float subtitlesScale;
    private boolean isScrubbing;
    private boolean scrubbingNoticeable;
    private long scrubbingStart;
    public boolean frameRendered;
    private boolean alive;
    public static boolean focusPlay = false;
    private Uri nextUri;
    public static boolean restoreControllerTimeout = false;
    public static boolean shortControllerTimeout = false;

    private long backPressedTime = 0;
    private Toast backToast;

    final Rational rationalLimitWide = new Rational(239, 100);
    final Rational rationalLimitTall = new Rational(100, 239);

    private static class QualityOption {
        String name;
        String url;
        int groupIndex = -1;
        int trackIndex = -1;
        int width;
        int height;

        QualityOption(String name, String url, int width, int height) {
            this.name = name;
            this.url = url;
            this.width = width;
            this.height = height;
        }

        QualityOption(String name, int groupIndex, int trackIndex, int width, int height) {
            this.name = name;
            this.groupIndex = groupIndex;
            this.trackIndex = trackIndex;
            this.width = width;
            this.height = height;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mPrefs = new Prefs(this);
        Utils.setOrientation(this, mPrefs.orientation);

        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT == 28 && Build.MANUFACTURER.toLowerCase().equals("xiaomi") && Build.DEVICE.toLowerCase().equals("oneday")) {
            setContentView(R.layout.activity_player_textureview);
        } else {
            setContentView(R.layout.activity_player);
        }

        if (getIntent() != null && getIntent().getData() != null) {
            mPrefs.updateMedia(this, getIntent().getData(), getIntent().getType());
            searchSubtitles();
            focusPlay = true;
        }

        coordinatorLayout = findViewById(R.id.coordinatorLayout);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        playerView = findViewById(R.id.video_view);

        if (playerView != null) {
            exoPlayPause = playerView.findViewById(R.id.exo_play_pause);
            loadingProgressBar = playerView.findViewById(R.id.loading);

            playerView.setShowNextButton(false);
            playerView.setShowPreviousButton(false);
            playerView.setShowFastForwardButton(false);
            playerView.setShowRewindButton(false);

            playerView.setControllerHideOnTouch(false);
            playerView.setControllerAutoShow(true);

            if (playerView instanceof DoubleTapPlayerView) {
                ((DoubleTapPlayerView) playerView).setDoubleTapEnabled(false);
            }
        }

        applyJudasGreenTheme();

        buttonOpen = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonOpen.setImageResource(R.drawable.ic_folder_open_24dp);
        buttonOpen.setId(View.generateViewId());
        buttonOpen.setFocusable(true);
        buttonOpen.setFocusableInTouchMode(true);
        buttonOpen.setOnClickListener(view -> openFile(mPrefs.mediaUri));

        buttonAspectRatio = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonAspectRatio.setImageResource(R.drawable.ic_aspect_ratio_24dp);
        buttonAspectRatio.setFocusable(true);
        buttonAspectRatio.setFocusableInTouchMode(true);
        buttonAspectRatio.setOnClickListener(view -> cycleAspectRatio());
        Utils.setButtonEnabled(this, buttonAspectRatio, false);

        buttonQuality = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonQuality.setId(View.generateViewId());
        buttonQuality.setImageResource(R.drawable.ic_brightness_medium_24);
        buttonQuality.setFocusable(true);
        buttonQuality.setFocusableInTouchMode(true);
        buttonQuality.setOnClickListener(view -> showQualityDialog());

        buttonInfo = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonInfo.setImageResource(R.drawable.ic_brightness_auto_24dp);
        buttonInfo.setFocusable(true);
        buttonInfo.setFocusableInTouchMode(true);
        buttonInfo.setOnClickListener(view -> toggleOsdOverlay());

        int titleViewPadding = getResources().getDimensionPixelOffset(R.dimen.exo_styled_bottom_bar_time_padding);
        FrameLayout centerView = playerView != null ? playerView.findViewById(R.id.exo_controls_background) : null;

        titleView = new TextView(this);
        titleView.setBackgroundResource(R.color.ui_controls_background);
        titleView.setTextColor(Color.WHITE);
        titleView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        titleView.setPadding(titleViewPadding, titleViewPadding, titleViewPadding, titleViewPadding);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setVisibility(View.GONE);
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);

        if (centerView != null) {
            centerView.addView(titleView);

            // OSD Overlay Layout dynamically attached
            LinearLayout osdLayout = new LinearLayout(this);
            osdLayout.setOrientation(LinearLayout.VERTICAL);
            osdLayout.setBackgroundResource(R.color.ui_controls_background);
            osdLayout.setPadding(24, 24, 24, 24);
            osdLayout.setVisibility(View.GONE);

            osdTextView = new TextView(this);
            osdTextView.setTextColor(Color.WHITE);
            osdTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            osdTextView.setTypeface(Typeface.MONOSPACE);
            osdLayout.addView(osdTextView);

            FrameLayout.LayoutParams osdParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            osdParams.gravity = Gravity.TOP | Gravity.START;
            osdParams.setMargins(32, 32, 32, 32);
            centerView.addView(osdLayout, osdParams);
            osdOverlayLayout = osdLayout;
        }

        controlView = playerView != null ? playerView.findViewById(R.id.exo_controller) : null;

        View delBtn = playerView != null ? playerView.findViewById(R.id.delete) : null;
        if (delBtn != null) {
            delBtn.setOnClickListener(view -> askDeleteMedia());
        }

        View nxtBtn = playerView != null ? playerView.findViewById(R.id.next) : null;
        if (nxtBtn != null) {
            nxtBtn.setOnClickListener(view -> skipToNext());
        }

        if (exoPlayPause != null) {
            exoPlayPause.setOnClickListener(view -> dispatchPlayPause());
        }

        playbackStateListener = new PlaybackStateListener();
        mBrightnessControl = new BrightnessControl(this);

        final LinearLayout exoBasicControls = playerView != null ? playerView.findViewById(R.id.exo_basic_controls) : null;
        if (exoBasicControls != null) {
            final ImageButton exoSubtitle = exoBasicControls.findViewById(R.id.exo_subtitle);
            if (exoSubtitle != null) {
                exoBasicControls.removeView(exoSubtitle);
            }
            final ImageButton exoSettings = exoBasicControls.findViewById(R.id.exo_settings);
            if (exoSettings != null) {
                exoBasicControls.removeView(exoSettings);
                exoSettings.setOnClickListener(v -> showJudasSettingsMenu());
            }

            final HorizontalScrollView horizontalScrollView = (HorizontalScrollView) getLayoutInflater().inflate(R.layout.controls, null);
            final LinearLayout controls = horizontalScrollView.findViewById(R.id.controls);

            controls.addView(buttonOpen);
            if (exoSubtitle != null) {
                controls.addView(exoSubtitle);
            }
            controls.addView(buttonQuality);
            controls.addView(buttonAspectRatio);
            controls.addView(buttonInfo);
            if (exoSettings != null) {
                controls.addView(exoSettings);
            }

            exoBasicControls.addView(horizontalScrollView);
        }

        if (playerView != null) {
            playerView.setControllerVisibilityListener(new StyledPlayerControlView.VisibilityListener() {
                @Override
                public void onVisibilityChange(int visibility) {
                    controllerVisible = visibility == View.VISIBLE;
                    controllerVisibleFully = playerView.isControllerFullyVisible();

                    if (visibility == View.VISIBLE) {
                        Utils.showSystemUi(playerView);
                        if (exoPlayPause != null) {
                            exoPlayPause.requestFocus();
                        }
                    } else {
                        Utils.hideSystemUi(playerView);
                    }
                }
            });
        }
    }

    private void applyJudasGreenTheme() {
        if (playerView == null) return;

        CustomDefaultTimeBar timeBar = playerView.findViewById(R.id.exo_progress);
        if (timeBar != null) {
            timeBar.setPlayedColor(Color.parseColor("#10B981"));
            timeBar.setScrubberColor(Color.parseColor("#34D399"));
            timeBar.setBufferedColor(Color.parseColor("#6610B981"));
            timeBar.setUnplayedColor(Color.parseColor("#33FFFFFF"));
        }

        View bottomBar = playerView.findViewById(R.id.exo_bottom_bar);
        View controlsBg = playerView.findViewById(R.id.exo_controls_background);
        if (bottomBar != null) bottomBar.setBackgroundColor(Color.parseColor("#E6071A12"));
        if (controlsBg != null) controlsBg.setBackgroundColor(Color.parseColor("#6604100B"));

        int[] buttonIds = new int[] {
                R.id.exo_play_pause,
                R.id.exo_settings,
                R.id.exo_subtitle,
                R.id.exo_prev,
                R.id.exo_next,
                R.id.delete,
                R.id.next
        };

        for (int id : buttonIds) {
            View btn = playerView.findViewById(id);
            if (btn != null) {
                btn.setFocusable(true);
                btn.setFocusableInTouchMode(true);
                btn.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        GradientDrawable focusedBg = new GradientDrawable();
                        focusedBg.setColor(Color.parseColor("#10B981"));
                        focusedBg.setCornerRadius(Utils.dpToPx(8));
                        v.setBackground(focusedBg);
                    } else {
                        v.setBackgroundColor(Color.TRANSPARENT);
                    }
                });
            }
        }

        if (buttonQuality != null) {
            buttonQuality.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    GradientDrawable focusedBg = new GradientDrawable();
                    focusedBg.setColor(Color.parseColor("#10B981"));
                    focusedBg.setCornerRadius(Utils.dpToPx(8));
                    v.setBackground(focusedBg);
                } else {
                    v.setBackgroundColor(Color.TRANSPARENT);
                }
            });
        }
    }

    private void showJudasSettingsMenu() {
        if (player == null) return;

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(32, 32, 32, 32);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#F2071A12"));
        cardBg.setStroke(2, Color.parseColor("#10B981"));
        cardBg.setCornerRadius(Utils.dpToPx(16));
        container.setBackground(cardBg);

        TextView titleTv = new TextView(this);
        titleTv.setText("Настройки JudAs");
        titleTv.setTextColor(Color.parseColor("#10B981"));
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setPadding(16, 0, 16, 24);
        container.addView(titleTv);

        TextView itemQuality = createJudasMenuItem("Качество: " + getCurrentQualityLabel(), v -> {
            dialog.dismiss();
            showQualityDialog();
        });
        container.addView(itemQuality);

        TextView itemAudio = createJudasMenuItem("Аудио: " + getCurrentAudioLabel(), v -> {
            dialog.dismiss();
            showAudioTrackDialog();
        });
        container.addView(itemAudio);

        float speed = player.getPlaybackParameters().speed;
        TextView itemSpeed = createJudasMenuItem("Скорость: " + String.format(Locale.US, "%.2fx", speed), v -> {
            dialog.dismiss();
            showSpeedDialog();
        });
        container.addView(itemSpeed);

        dialog.setContentView(container);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.gravity = Gravity.BOTTOM | Gravity.END;
            lp.x = Utils.dpToPx(24);
            lp.y = Utils.dpToPx(60);
            window.setAttributes(lp);
        }

        dialog.show();
        itemQuality.requestFocus();
    }

    private TextView createJudasMenuItem(String text, View.OnClickListener onClickListener) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        textView.setPadding(24, 20, 24, 20);
        textView.setFocusable(true);
        textView.setFocusableInTouchMode(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 8, 0, 8);
        textView.setLayoutParams(lp);

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(Color.parseColor("#0B2419"));
        normalBg.setCornerRadius(Utils.dpToPx(8));

        GradientDrawable focusedBg = new GradientDrawable();
        focusedBg.setColor(Color.parseColor("#10B981"));
        focusedBg.setCornerRadius(Utils.dpToPx(8));

        textView.setBackground(normalBg);
        textView.setTextColor(Color.WHITE);

        textView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                textView.setBackground(focusedBg);
                textView.setTextColor(Color.parseColor("#04100B"));
            } else {
                textView.setBackground(normalBg);
                textView.setTextColor(Color.WHITE);
            }
        });

        textView.setOnClickListener(onClickListener);
        return textView;
    }

    private String getCurrentQualityLabel() {
        if (player == null) return "Auto";
        Format vf = player.getVideoFormat();
        if (vf != null && vf.height > 0) {
            return vf.height + "p";
        }
        return "Auto";
    }

    private String getCurrentAudioLabel() {
        if (player == null) return "Default";
        Format af = player.getAudioFormat();
        if (af != null && !TextUtils.isEmpty(af.language)) {
            return af.language.toUpperCase(Locale.US);
        }
        return "1";
    }

    private void showAudioTrackDialog() {
        if (trackSelector == null) return;
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo == null) return;

        List<String> labels = new ArrayList<>();
        List<Integer> groupIndices = new ArrayList<>();
        labels.add("Автоматически");
        groupIndices.add(-1);

        for (int r = 0; r < mappedTrackInfo.getRendererCount(); r++) {
            if (mappedTrackInfo.getRendererType(r) == C.TRACK_TYPE_AUDIO) {
                TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(r);
                for (int g = 0; g < trackGroups.length; g++) {
                    TrackGroup group = trackGroups.get(g);
                    for (int t = 0; t < group.length; t++) {
                        Format f = group.getFormat(t);
                        String lang = f.language != null ? f.language.toUpperCase() : "Track " + (g + 1);
                        String mime = f.sampleMimeType != null ? f.sampleMimeType.replace("audio/", "") : "";
                        labels.add(lang + " (" + mime + " " + f.channelCount + "ch)");
                        groupIndices.add(g);
                    }
                }
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.JudAsDialogTheme);
        builder.setTitle("Аудиодорожка");
        builder.setItems(labels.toArray(new String[0]), (dialog, which) -> {
            if (which == 0) {
                setSelectedTrackAudio(-1, false);
            } else {
                setSelectedTrackAudio(groupIndices.get(which), false);
            }
        });
        builder.create().show();
    }

    private void showSpeedDialog() {
        if (player == null) return;
        String[] speeds = new String[] { "0.5x", "0.75x", "1.0x (Норм)", "1.25x", "1.5x", "2.0x" };
        float[] speedVals = new float[] { 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f };

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.JudAsDialogTheme);
        builder.setTitle("Скорость воспроизведения");
        builder.setItems(speeds, (dialog, which) -> {
            player.setPlaybackParameters(new PlaybackParameters(speedVals[which]));
        });
        builder.create().show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                dispatchPlayPause();
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (mAudioManager != null && playerView != null) {
                    Utils.adjustVolume(mAudioManager, playerView, keyCode == KeyEvent.KEYCODE_VOLUME_UP, event.getRepeatCount() == 0);
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_SELECT:
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SPACE:
                if (!controllerVisibleFully) {
                    dispatchPlayPause();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_BUTTON_L2:
                if (!controllerVisibleFully && player != null) {
                    long seekTo = Math.max(0, player.getCurrentPosition() - 10000);
                    player.seekTo(seekTo);
                    Utils.showText(playerView, Utils.formatMilis(seekTo));
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BUTTON_R2:
                if (!controllerVisibleFully && player != null) {
                    long seekTo = Math.min(player.getDuration(), player.getCurrentPosition() + 10000);
                    player.seekTo(seekTo);
                    Utils.showText(playerView, Utils.formatMilis(seekTo));
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                onBackPressed();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (controllerVisible && player != null && player.isPlaying()) {
            if (playerView != null) {
                playerView.hideController();
            }
            return;
        }

        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            if (backToast != null) {
                backToast.cancel();
            }
            finish();
        } else {
            backToast = Toast.makeText(this, "Нажмите ещё раз для выхода", Toast.LENGTH_SHORT);
            backToast.show();
            backPressedTime = System.currentTimeMillis();
        }
    }

    @Override
    public void finish() {
        sendLampaResult();
        super.finish();
    }

    private void sendLampaResult() {
        if (player == null) return;
        long currentPos = player.getCurrentPosition();
        long duration = player.getDuration();
        boolean isEnded = (player.getPlaybackState() == Player.STATE_ENDED)
                || (duration > 0 && currentPos + 3000 >= duration);
        String endBy = isEnded ? "playback_completion" : "user";

        Intent resultIntent = new Intent("com.mxtech.intent.result.VIEW");
        resultIntent.putExtra("position", (int) currentPos);
        resultIntent.putExtra("duration", (int) (duration > 0 ? duration : 0));
        resultIntent.putExtra("end_by", endBy);

        if (getIntent() != null && getIntent().getData() != null) {
            resultIntent.setData(getIntent().getData());
        }

        setResult(Activity.RESULT_OK, resultIntent);

        Intent bIntent = new Intent("com.judas.player.POSITION_UPDATE");
        bIntent.putExtra("position", (int) currentPos);
        bIntent.putExtra("duration", (int) (duration > 0 ? duration : 0));
        bIntent.putExtra("end_by", endBy);
        if (getIntent() != null && getIntent().getData() != null) {
            bIntent.setData(getIntent().getData());
        }
        sendBroadcast(bIntent);
    }

    private long getLampaStartPosition() {
        Intent intent = getIntent();
        if (intent == null) return -1;
        Bundle extras = intent.getExtras();
        if (extras == null) return -1;

        long pos = -1;
        if (extras.containsKey("position")) {
            Object obj = extras.get("position");
            if (obj instanceof Number) {
                pos = ((Number) obj).longValue();
            } else if (obj instanceof String) {
                try { pos = Long.parseLong((String) obj); } catch (Exception ignored) {}
            }
        }
        if (pos <= 0 && extras.containsKey("startfrom")) {
            Object obj = extras.get("startfrom");
            if (obj instanceof Number) {
                pos = ((Number) obj).longValue();
            } else if (obj instanceof String) {
                try { pos = Long.parseLong((String) obj); } catch (Exception ignored) {}
            }
        }
        return pos;
    }

    private Map<String, String> parseIntentHeaders() {
        Intent intent = getIntent();
        if (intent == null) return null;
        Bundle extras = intent.getExtras();
        if (extras == null) return null;

        Map<String, String> headers = new HashMap<>();
        if (extras.containsKey("headers")) {
            Object obj = extras.get("headers");
            if (obj instanceof Bundle) {
                Bundle headerBundle = (Bundle) obj;
                for (String key : headerBundle.keySet()) {
                    headers.put(key, headerBundle.getString(key));
                }
            } else if (obj instanceof String[]) {
                String[] arr = (String[]) obj;
                for (int i = 0; i + 1 < arr.length; i += 2) {
                    headers.put(arr[i], arr[i + 1]);
                }
            }
        }
        return headers;
    }

    private void cycleAspectRatio() {
        if (playerView == null) return;
        int mode = playerView.getResizeMode();
        String modeName;
        if (mode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
            modeName = "Zoom / Crop";
        } else if (mode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
            modeName = "Stretch / Fill";
        } else if (mode == AspectRatioFrameLayout.RESIZE_MODE_FILL) {
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH);
            modeName = "16:9";
        } else if (mode == AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH) {
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT);
            modeName = "4:3";
        } else {
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            modeName = "Fit";
        }
        Utils.showText(playerView, modeName);
    }

    private void toggleOsdOverlay() {
        if (osdOverlayLayout == null) return;
        if (osdOverlayLayout.getVisibility() == View.VISIBLE) {
            osdOverlayLayout.setVisibility(View.GONE);
        } else {
            updateOsdText();
            osdOverlayLayout.setVisibility(View.VISIBLE);
        }
    }

    private void updateOsdText() {
        if (osdTextView == null || player == null) return;

        Format videoFormat = player.getVideoFormat();
        Format audioFormat = player.getAudioFormat();

        StringBuilder sb = new StringBuilder();
        sb.append("JudAs OSD Info\n");
        sb.append("----------------------------\n");

        if (videoFormat != null) {
            sb.append("Video: ").append(videoFormat.sampleMimeType != null ? videoFormat.sampleMimeType : "Unknown").append("\n");
            sb.append("Resolution: ").append(videoFormat.width).append("x").append(videoFormat.height).append("\n");
            if (videoFormat.frameRate > 0) {
                sb.append("FPS: ").append(String.format(Locale.US, "%.3f", videoFormat.frameRate)).append("\n");
            }
            if (videoFormat.bitrate > 0) {
                sb.append("Video Bitrate: ").append(videoFormat.bitrate / 1000).append(" kbps\n");
            }
        }

        if (audioFormat != null) {
            sb.append("Audio: ").append(audioFormat.sampleMimeType != null ? audioFormat.sampleMimeType : "Unknown").append("\n");
            sb.append("Channels: ").append(audioFormat.channelCount).append(" ch / ").append(audioFormat.sampleRate).append(" Hz\n");
        }

        long bufferedPos = player.getBufferedPosition();
        long currentPos = player.getCurrentPosition();
        long bufferSec = Math.max(0, (bufferedPos - currentPos) / 1000);
        sb.append("Buffer: ").append(bufferSec).append(" sec");

        osdTextView.setText(sb.toString());
    }

    private List<QualityOption> getQualityOptions() {
        List<QualityOption> options = new ArrayList<>();
        Intent intent = getIntent();

        // Source B: Lampa Intent Extras
        if (intent != null && intent.getExtras() != null) {
            Bundle extras = intent.getExtras();
            if (extras.containsKey("qualities")) {
                Object obj = extras.get("qualities");
                if (obj instanceof String) {
                    try {
                        JSONArray array = new JSONArray((String) obj);
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject item = array.getJSONObject(i);
                            String title = item.optString("title", item.optString("name", item.optString("label", "Quality " + (i + 1))));
                            String url = item.optString("url", item.optString("file", ""));
                            if (!TextUtils.isEmpty(url)) {
                                options.add(new QualityOption(title, url, 0, 0));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else if (extras.containsKey("Urls") && extras.containsKey("Names")) {
                String[] urls = extras.getStringArray("Urls");
                String[] names = extras.getStringArray("Names");
                if (urls != null && names != null && urls.length == names.length) {
                    for (int i = 0; i < urls.length; i++) {
                        options.add(new QualityOption(names[i], urls[i], 0, 0));
                    }
                }
            }
        }

        // Source A: ExoPlayer HLS/DASH Video Tracks
        if (trackSelector != null) {
            MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
            if (mappedTrackInfo != null) {
                for (int rendererIndex = 0; rendererIndex < mappedTrackInfo.getRendererCount(); rendererIndex++) {
                    if (mappedTrackInfo.getRendererType(rendererIndex) == C.TRACK_TYPE_VIDEO) {
                        TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
                        if (trackGroups.length > 1 || (trackGroups.length == 1 && trackGroups.get(0).length > 1)) {
                            options.add(new QualityOption("Auto (Dynamic)", -1, -1, 0, 0));
                            for (int g = 0; g < trackGroups.length; g++) {
                                TrackGroup group = trackGroups.get(g);
                                for (int t = 0; t < group.length; t++) {
                                    Format format = group.getFormat(t);
                                    if (format.height > 0) {
                                        String label = format.height + "p";
                                        if (format.height >= 2160) label = "2160p (4K)";
                                        else if (format.height >= 1080) label = "1080p (FHD)";
                                        else if (format.height >= 720) label = "720p (HD)";
                                        options.add(new QualityOption(label, g, t, format.width, format.height));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Source C: Balancer direct MP4 / MKV links with resolution pattern in URL
        if (options.isEmpty() && mPrefs.mediaUri != null) {
            String currentUrl = mPrefs.mediaUri.toString();
            Pattern pattern = Pattern.compile("(2160|1080|720|480|360)");
            Matcher matcher = pattern.matcher(currentUrl);
            if (matcher.find()) {
                String currentResStr = matcher.group(1);
                int[] resolutions = new int[] { 2160, 1080, 720, 480, 360 };
                for (int res : resolutions) {
                    String newUrl = currentUrl.replaceAll("(2160|1080|720|480|360)", String.valueOf(res));
                    String label = res + "p";
                    if (res == 2160) label = "2160p (4K)";
                    else if (res == 1080) label = "1080p (FHD)";
                    else if (res == 720) label = "720p (HD)";
                    if (String.valueOf(res).equals(currentResStr)) {
                        label += " ✓";
                    }
                    options.add(new QualityOption(label, newUrl, 0, res));
                }
            }
        }

        return options;
    }

    private void showQualityDialog() {
        if (player == null) return;
        List<QualityOption> options = getQualityOptions();
        if (options.isEmpty()) {
            Toast.makeText(this, "Качество определено автоматически", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[options.size()];
        for (int i = 0; i < options.size(); i++) {
            names[i] = options.get(i).name;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.JudAsDialogTheme);
        builder.setTitle("Качество видео");
        builder.setItems(names, (dialog, which) -> {
            QualityOption option = options.get(which);
            long pos = player.getCurrentPosition();
            if (option.url != null) {
                Uri newUri = Uri.parse(option.url);
                mPrefs.updateMedia(this, newUri, mPrefs.mediaType);
                player.setMediaItem(MediaItem.fromUri(newUri), pos);
                player.prepare();
                player.play();
            } else if (trackSelector != null) {
                DefaultTrackSelector.ParametersBuilder builder1 = trackSelector.getParameters().buildUpon();
                if (option.height > 0) {
                    builder1.setMaxVideoSize(option.width > 0 ? option.width : Integer.MAX_VALUE, option.height);
                } else {
                    builder1.clearVideoSizeConstraints();
                }
                trackSelector.setParameters(builder1);
            }
        });
        builder.create().show();
    }

    @Override
    public void onStart() {
        super.onStart();
        alive = true;
        updateSubtitleStyle();
        initializePlayer();
    }

    @Override
    public void onStop() {
        super.onStop();
        alive = false;
        releasePlayer();
    }

    public void initializePlayer() {
        haveMedia = mPrefs.mediaUri != null && (Utils.fileExists(this, mPrefs.mediaUri) || Utils.isSupportedNetworkUri(mPrefs.mediaUri));

        if (player == null) {
            trackSelector = new DefaultTrackSelector(this);
            if (Build.VERSION.SDK_INT >= 24) {
                final LocaleList localeList = Resources.getSystem().getConfiguration().getLocales();
                final List<String> locales = new ArrayList<>();
                for (int i = 0; i < localeList.size(); i++) {
                    locales.add(localeList.get(i).getISO3Language());
                }
                trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setPreferredAudioLanguages(locales.toArray(new String[0]))
                );
            }

            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(30000, 120000, 2500, 5000)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();

            RenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
            final DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                    .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE);

            Map<String, String> httpHeaders = parseIntentHeaders();
            DefaultMediaSourceFactory mediaSourceFactory;
            if (httpHeaders != null && !httpHeaders.isEmpty()) {
                DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                        .setUserAgent(Util.getUserAgent(this, "JudAs"))
                        .setDefaultRequestProperties(httpHeaders);
                mediaSourceFactory = new DefaultMediaSourceFactory(httpDataSourceFactory, extractorsFactory);
            } else {
                mediaSourceFactory = new DefaultMediaSourceFactory(this, extractorsFactory);
            }

            player = new SimpleExoPlayer.Builder(this, renderersFactory)
                    .setTrackSelector(trackSelector)
                    .setLoadControl(loadControl)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build();

            final AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.CONTENT_TYPE_MOVIE)
                    .build();
            player.setAudioAttributes(audioAttributes, true);

            final YouTubeOverlay youTubeOverlay = findViewById(R.id.youtube_overlay);
            if (youTubeOverlay != null) {
                youTubeOverlay.player(player);
            }
        }

        if (playerView != null) {
            playerView.setPlayer(player);
        }

        mediaSession = new MediaSessionCompat(this, getString(R.string.app_name));
        MediaSessionConnector mediaSessionConnector = new MediaSessionConnector(mediaSession);
        mediaSessionConnector.setPlayer(player);

        if (playerView != null) {
            playerView.setControllerShowTimeoutMs(-1);
        }

        if (haveMedia) {
            if (playerView != null) {
                playerView.setResizeMode(mPrefs.resizeMode);
            }

            MediaItem.Builder mediaItemBuilder = new MediaItem.Builder()
                    .setUri(mPrefs.mediaUri)
                    .setMimeType(mPrefs.mediaType);
            player.setMediaItem(mediaItemBuilder.build());

            videoLoading = true;
            updateLoading(true);

            long startPos = getLampaStartPosition();
            if (startPos > 0) {
                player.seekTo(startPos);
            } else {
                player.seekTo(mPrefs.getPosition());
            }

            String titleExtra = getIntent() != null ? getIntent().getStringExtra("title") : null;
            if (TextUtils.isEmpty(titleExtra) && getIntent() != null) {
                titleExtra = getIntent().getStringExtra("forcename");
            }
            if (TextUtils.isEmpty(titleExtra)) {
                titleExtra = Utils.getFileName(this, mPrefs.mediaUri);
            }

            if (titleView != null) {
                titleView.setText(titleExtra);
                titleView.setVisibility(View.VISIBLE);
            }

            Utils.setButtonEnabled(this, buttonAspectRatio, true);
            if (playerView instanceof DoubleTapPlayerView) {
                ((DoubleTapPlayerView) playerView).setDoubleTapEnabled(true);
            }

            player.setHandleAudioBecomingNoisy(true);
            mediaSession.setActive(true);
        } else {
            if (playerView != null) {
                playerView.showController();
            }
        }

        player.addListener(playbackStateListener);
        player.prepare();
        player.play();
    }

    public void releasePlayer() {
        if (player != null) {
            mediaSession.setActive(false);
            mediaSession.release();

            if (haveMedia && player.isCurrentWindowSeekable()) {
                mPrefs.updatePosition(player.getCurrentPosition());
            }

            player.removeListener(playbackStateListener);
            player.clearMediaItems();
            player.release();
            player = null;
        }
        if (titleView != null) {
            titleView.setVisibility(View.GONE);
        }
        Utils.setButtonEnabled(this, buttonAspectRatio, false);
    }

    public int getSelectedTrackSubtitle() { return -1; }
    public void setSelectedTrackSubtitle(int i) {}
    public int getSelectedTrackAudio(boolean b) { return -1; }
    public void setSelectedTrackAudio(int trackIndex, boolean b) {
        if (trackSelector != null) {
            MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
            if (mappedTrackInfo != null) {
                DefaultTrackSelector.ParametersBuilder builder = trackSelector.getParameters().buildUpon();
                for (int r = 0; r < mappedTrackInfo.getRendererCount(); r++) {
                    if (mappedTrackInfo.getRendererType(r) == C.TRACK_TYPE_AUDIO) {
                        if (trackIndex < 0) {
                            builder.clearSelectionOverrides(r);
                        } else {
                            DefaultTrackSelector.SelectionOverride override = new DefaultTrackSelector.SelectionOverride(trackIndex, 0);
                            builder.setSelectionOverride(r, mappedTrackInfo.getTrackGroups(r), override);
                        }
                    }
                }
                trackSelector.setParameters(builder);
            }
        }
    }

    private class PlaybackStateListener implements Player.EventListener {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (playerView != null) {
                playerView.setKeepScreenOn(isPlaying);
            }
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_READY) {
                if (videoLoading) {
                    videoLoading = false;
                    Format format = player.getVideoFormat();
                    if (format != null) {
                        applyAfr(format.frameRate);
                    }
                    updateLoading(false);
                }
            }
        }
    }

    private void applyAfr(float frameRate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && frameRate > 0) {
            try {
                Window window = getWindow();
                Display display = getWindowManager().getDefaultDisplay();
                Display.Mode[] modes = display.getSupportedModes();
                Display.Mode bestMode = null;
                float bestDiff = Float.MAX_VALUE;
                for (Display.Mode mode : modes) {
                    float diff = Math.abs(mode.getRefreshRate() - frameRate);
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        bestMode = mode;
                    }
                }
                if (bestMode != null && bestDiff < 1.0f) {
                    WindowManager.LayoutParams lp = window.getAttributes();
                    lp.preferredDisplayModeId = bestMode.getModeId();
                    window.setAttributes(lp);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void openFile(Uri pickerInitialUri) {
        Utils.alternativeChooser(this, pickerInitialUri, true);
    }

    private void askDeleteMedia() {
        releasePlayer();
        finish();
    }

    private void skipToNext() {
        if (nextUri != null) {
            releasePlayer();
            mPrefs.updateMedia(this, nextUri, null);
            initializePlayer();
        }
    }

    private void dispatchPlayPause() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
    }

    private void updateLoading(boolean enableLoading) {
        if (enableLoading) {
            if (exoPlayPause != null) exoPlayPause.setVisibility(View.GONE);
            if (loadingProgressBar != null) loadingProgressBar.setVisibility(View.VISIBLE);
        } else {
            if (loadingProgressBar != null) loadingProgressBar.setVisibility(View.GONE);
            if (exoPlayPause != null) {
                exoPlayPause.setVisibility(View.VISIBLE);
            }
        }
    }

    private void reportScrubbing(long position) {
        if (player != null) {
            player.seekTo(position);
        }
    }

    private void updateSubtitleStyle() {}
    void searchSubtitles() {}
}
