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
import android.os.Handler;
import android.os.Looper;
import android.os.LocaleList;
import android.util.LruCache;
import android.media.MediaMetadataRetriever;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.widget.ImageView;
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
import android.widget.ScrollView;
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
    private ImageButton buttonPlaylist;
    private final List<PlaylistItem> activePlaylist = new ArrayList<>();
    private int activeEpisodeIndex = 0;
    private ImageButton exoPlayPause;
    private ProgressBar loadingProgressBar;
    private StyledPlayerControlView controlView;

    private View osdOverlayLayout;
    private TextView osdTextView;

    private ThumbnailProvider thumbnailProvider;
    private View thumbnailSeekerPopup;
    private ImageView thumbnailImageView;
    private TextView thumbnailTimeText;

    private boolean isVirtualSeeking = false;
    private long virtualSeekPosition = 0;
    private int dpadRepeatCount = 0;
    private long lastDpadPressTime = 0;
    private boolean isFastForwarding = false;

    private View mediaFormatNotificationView;
    private final Handler formatNotificationHandler = new Handler(Looper.getMainLooper());

    private final Handler confirmSeekHandler = new Handler(Looper.getMainLooper());
    private final Runnable confirmSeekRunnable = () -> confirmVirtualSeek();

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

    public static Uri extractUriFromIntent(Intent intent) {
        if (intent == null) return null;
        if (intent.getData() != null) {
            return intent.getData();
        }
        if (intent.hasExtra(Intent.EXTRA_STREAM)) {
            Object extra = intent.getExtras().get(Intent.EXTRA_STREAM);
            if (extra instanceof Uri) return (Uri) extra;
            if (extra instanceof String) return Uri.parse((String) extra);
        }
        if (intent.hasExtra("url")) {
            String url = intent.getStringExtra("url");
            if (!TextUtils.isEmpty(url)) return Uri.parse(url);
        }
        if (intent.hasExtra("path")) {
            String path = intent.getStringExtra("path");
            if (!TextUtils.isEmpty(path)) return Uri.parse(path);
        }
        if (intent.hasExtra("link")) {
            String link = intent.getStringExtra("link");
            if (!TextUtils.isEmpty(link)) return Uri.parse(link);
        }
        if (Build.VERSION.SDK_INT >= 16 && intent.getClipData() != null && intent.getClipData().getItemCount() > 0) {
            Uri clipUri = intent.getClipData().getItemAt(0).getUri();
            if (clipUri != null) return clipUri;
        }
        return null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri uri = extractUriFromIntent(intent);
        if (uri != null) {
            releasePlayer();
            mPrefs.updateMedia(this, uri, intent.getType());
            searchSubtitles();
            initializePlayer();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            if (requestCode == REQUEST_CHOOSER_SUBTITLE) {
                Uri subUri = data.getData();
                mPrefs.updateSubtitle(subUri);
                Utils.showText(playerView, "Субтитры подключены");
                releasePlayer();
                initializePlayer();
            }
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

        Uri uri = extractUriFromIntent(getIntent());
        if (uri != null) {
            mPrefs.updateMedia(this, uri, getIntent().getType());
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
        Utils.setButtonEnabled(this, buttonAspectRatio, true);

        buttonQuality = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonQuality.setId(View.generateViewId());

        buttonQuality.setFocusable(true);
        buttonQuality.setFocusableInTouchMode(true);
        buttonQuality.setOnClickListener(view -> showQualityDialog());

        buttonInfo = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonInfo.setImageResource(android.R.drawable.ic_menu_info_details);
        buttonInfo.setFocusable(true);
        buttonInfo.setFocusableInTouchMode(true);
        buttonInfo.setOnClickListener(view -> toggleOsdOverlay());

        buttonPlaylist = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonPlaylist.setImageResource(android.R.drawable.ic_menu_slideshow);
        buttonPlaylist.setFocusable(true);
        buttonPlaylist.setFocusableInTouchMode(true);
        buttonPlaylist.setOnClickListener(view -> showEpisodesSideMenu());

        int titleViewPadding = getResources().getDimensionPixelOffset(R.dimen.exo_styled_bottom_bar_time_padding);
        FrameLayout centerView = playerView != null ? playerView.findViewById(R.id.exo_controls_background) : null;

        titleView = new TextView(this);
        titleView.setBackgroundColor(Color.TRANSPARENT);
        titleView.setTextColor(Color.WHITE);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setShadowLayer(8f, 2f, 2f, Color.BLACK);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.gravity = Gravity.TOP | Gravity.START;
        titleParams.setMargins(Utils.dpToPx(24), Utils.dpToPx(16), Utils.dpToPx(24), 0);
        titleView.setLayoutParams(titleParams);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        titleView.setVisibility(View.GONE);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);

        if (centerView != null) {
            centerView.addView(titleView);

            // Thumbnail Seeker Popup
            LinearLayout popup = new LinearLayout(this);
            popup.setOrientation(LinearLayout.VERTICAL);
            popup.setGravity(Gravity.CENTER);
            popup.setPadding(Utils.dpToPx(8), Utils.dpToPx(8), Utils.dpToPx(8), Utils.dpToPx(8));
            popup.setVisibility(View.GONE);

            GradientDrawable popupBg = new GradientDrawable();
            popupBg.setColor(Color.parseColor("#EE071A12"));
            popupBg.setStroke(Utils.dpToPx(2), Color.parseColor("#10B981"));
            popupBg.setCornerRadius(Utils.dpToPx(12));
            popup.setBackground(popupBg);

            ImageView thumbImg = new ImageView(this);
            thumbImg.setLayoutParams(new LinearLayout.LayoutParams(Utils.dpToPx(192), Utils.dpToPx(108)));
            thumbImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbImg.setBackgroundColor(Color.parseColor("#1A1A1A"));
            popup.addView(thumbImg);

            TextView timeTv = new TextView(this);
            timeTv.setTextColor(Color.parseColor("#10B981"));
            timeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            timeTv.setTypeface(Typeface.DEFAULT_BOLD);
            timeTv.setPadding(0, Utils.dpToPx(4), 0, 0);
            timeTv.setGravity(Gravity.CENTER);
            popup.addView(timeTv);

            FrameLayout.LayoutParams popupParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            popupParams.gravity = Gravity.BOTTOM | Gravity.START;
            popupParams.setMargins(Utils.dpToPx(16), 0, Utils.dpToPx(16), Utils.dpToPx(72));
            centerView.addView(popup, popupParams);

            thumbnailSeekerPopup = popup;
            thumbnailImageView = thumbImg;
            thumbnailTimeText = timeTv;

            thumbnailProvider = new ThumbnailProvider();
            if (mPrefs.mediaUri != null) {
                thumbnailProvider.setSource(this, mPrefs.mediaUri, parseIntentHeaders());
            }

            // OSD Overlay Layout dynamically attached
            LinearLayout osdLayout = new LinearLayout(this);
            osdLayout.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable osdBg = new GradientDrawable();
            osdBg.setColor(Color.parseColor("#E6071A12"));
            osdBg.setCornerRadius(Utils.dpToPx(16));
            osdBg.setStroke(2, Color.parseColor("#10B981"));
            osdLayout.setBackground(osdBg);
            osdLayout.setPadding(32, 32, 32, 32);
            osdLayout.setVisibility(View.GONE);

            osdTextView = new TextView(this);
            osdTextView.setTextColor(Color.parseColor("#10B981"));
            osdTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            osdTextView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
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
            controls.addView(buttonAspectRatio);
            controls.addView(buttonInfo);
            if (buttonPlaylist != null) {
                controls.addView(buttonPlaylist);
            }
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
        applyJudasGreenTheme();
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
        if (bottomBar != null) {
            GradientDrawable bottomBg = new GradientDrawable();
            bottomBg.setColors(new int[] { Color.TRANSPARENT, Color.parseColor("#B3000000") });
            bottomBg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
            bottomBar.setBackground(bottomBg);
            bottomBar.setPadding(0, Utils.dpToPx(16), 0, 0);
        }
        if (controlsBg != null) {
            controlsBg.setBackground(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView defaultTitleView = playerView.findViewById(R.id.title);
        if (defaultTitleView != null) {
            defaultTitleView.setVisibility(View.GONE);
        }

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
                btn.setBackground(null);
                btn.setOnFocusChangeListener((v, hasFocus) -> {
                    if (v instanceof ImageView) {
                        if (hasFocus) {
                            ((ImageView) v).setColorFilter(Color.parseColor("#10B981"));
                            v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).start();
                        } else {
                            ((ImageView) v).clearColorFilter();
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                        }
                    }
                });
            }
        }

        View[] customButtons = new View[] { buttonOpen, buttonAspectRatio, buttonInfo, buttonQuality, buttonPlaylist };
        for (View btn : customButtons) {
            if (btn != null) {
                btn.setFocusable(true);
                btn.setFocusableInTouchMode(true);
                btn.setBackground(null);
                btn.setOnFocusChangeListener((v, hasFocus) -> {
                    if (v instanceof ImageView) {
                        if (hasFocus) {
                            ((ImageView) v).setColorFilter(Color.parseColor("#10B981"));
                            v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).start();
                        } else {
                            ((ImageView) v).clearColorFilter();
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                        }
                    }
                });
            }
        }
    }

    private void showJudasSettingsMenu() {
        if (player == null) return;

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(true);
        scrollView.setFocusable(false);
        scrollView.setFocusableInTouchMode(false);
        scrollView.setClipChildren(false);
        scrollView.setClipToPadding(false);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(32, 32, 32, 32);
        container.setClipChildren(false);
        container.setClipToPadding(false);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#F2071A12"));
        cardBg.setStroke(2, Color.parseColor("#10B981"));
        cardBg.setCornerRadius(Utils.dpToPx(16));
        container.setBackground(cardBg);

        TextView itemQuality = createJudasMenuItem("Качество видео", v -> {
            dialog.dismiss();
            showQualityDialog();
        });
        container.addView(itemQuality);

        TextView itemAudio = createJudasMenuItem("Аудиодорожки", v -> {
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

        TextView itemSubs = createJudasMenuItem("Субтитры (Вшитые дорожки)", v -> {
            dialog.dismiss();
            View exoSubtitle = playerView.findViewById(R.id.exo_subtitle);
            if (exoSubtitle != null) exoSubtitle.performClick();
        });
        container.addView(itemSubs);

        TextView itemLoadSub = createJudasMenuItem("Загрузить файл субтитров (.srt / .vtt)", v -> {
            dialog.dismiss();
            openSubtitleFilePicker();
        });
        container.addView(itemLoadSub);

        if (mPrefs.subtitleUri != null) {
            TextView itemDeleteSub = createJudasMenuItem("Удалить загруженные субтитры", v -> {
                dialog.dismiss();
                clearLoadedSubtitles();
            });
            container.addView(itemDeleteSub);
        }

        TextView itemAfr = createJudasMenuItem("Автофреймрейт (AFR): " + (mPrefs.afrEnabled ? "Вкл" : "Выкл"), v -> {
            dialog.dismiss();
            mPrefs.updateAfr(!mPrefs.afrEnabled);
            Utils.showText(playerView, "AFR: " + (mPrefs.afrEnabled ? "Включен" : "Выключен"));
        });
        container.addView(itemAfr);

        TextView itemOnlineSubs = createJudasMenuItem("Поиск субтитров онлайн", v -> {
            dialog.dismiss();
            searchSubtitlesOnline();
        });
        container.addView(itemOnlineSubs);

        TextView itemSkipIntro = createJudasMenuItem("Пропустить заставку (+85с)", v -> {
            dialog.dismiss();
            skipIntro();
        });
        container.addView(itemSkipIntro);

        TextView itemAudioDelay = createJudasMenuItem("Задержка звука / Рассинхрон", v -> {
            dialog.dismiss();
            showAudioDelayDialog();
        });
        container.addView(itemAudioDelay);

        TextView itemSubDelay = createJudasMenuItem("Задержка субтитров / Рассинхрон", v -> {
            dialog.dismiss();
            showSubtitleDelayDialog();
        });
        container.addView(itemSubDelay);

        scrollView.addView(container);
        dialog.setContentView(scrollView);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.gravity = Gravity.BOTTOM | Gravity.END;
            lp.width = Utils.dpToPx(520);
            lp.height = Utils.dpToPx(480);
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
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        textView.setMaxLines(1);
        textView.setPadding(Utils.dpToPx(16), Utils.dpToPx(12), Utils.dpToPx(16), Utils.dpToPx(12));
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

        textView.setOnClickListener(v -> {
            if (onClickListener != null) onClickListener.onClick(v);
        });
        return textView;
    }

    private void applyJellyBounceAnimation(View view, boolean hasFocus) {
        if (view == null) return;
        view.animate().cancel();
        if (hasFocus) {
            view.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .setDuration(120)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f))
                    .start();
        } else {
            view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
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

    private void skipIntro() {
        if (player == null) return;
        long newPos = Math.min(player.getDuration(), player.getCurrentPosition() + 85000);
        player.seekTo(newPos);
        Utils.showText(playerView, "Пропуск заставки (+85с)");
    }

    private void toggleNightMode() {
        boostLevel = (boostLevel + 1) % 3;
        String label = boostLevel == 0 ? "Выкл" : (boostLevel == 1 ? "Слабое" : "Сильное");
        if (player != null && player.getAudioSessionId() != C.AUDIO_SESSION_ID_UNSET) {
            try {
                if (loudnessEnhancer == null) {
                    loudnessEnhancer = new LoudnessEnhancer(player.getAudioSessionId());
                }
                if (boostLevel == 0) {
                    loudnessEnhancer.setEnabled(false);
                } else {
                    loudnessEnhancer.setEnabled(true);
                    loudnessEnhancer.setTargetGain(boostLevel == 1 ? 400 : 800);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Utils.showText(playerView, "Усиление речи: " + label);
    }

    private void showAudioDelayDialog() {
        String[] options = new String[] { "-1000 ms", "-500 ms", "-250 ms", "0 ms (Норм)", "+250 ms", "+500 ms", "+1000 ms" };

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.JudAsDialogTheme);
        builder.setTitle("Задержка звука");
        builder.setItems(options, (dialog, which) -> {
            Utils.showText(playerView, "Смещение звука: " + options[which]);
        });
        builder.create().show();
    }

    private void openSubtitleFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Выберите файл субтитров (.srt, .vtt)"), REQUEST_CHOOSER_SUBTITLE);
        } catch (Exception e) {
            Toast.makeText(this, "Файловый менеджер недоступен", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearLoadedSubtitles() {
        mPrefs.updateSubtitle(null);
        Utils.showText(playerView, "Субтитры удалены");
        releasePlayer();
        initializePlayer();
    }

    private void searchSubtitlesOnline() {
        String title = getIntent() != null ? getIntent().getStringExtra("title") : null;
        if (TextUtils.isEmpty(title)) {
            title = Utils.getFileName(this, mPrefs.mediaUri);
        }
        if (title == null) title = "";

        // Remove garbage keywords
        title = title.replaceAll("(?i)(1080p|720p|2160p|4k|HDR|WEB-DL|WEBRip|BDRip|BluRay|\\d{4}|HEVC|H264|H\\.264|H\\.265|DTS|AC3|DD5\\.1|x264|x265)", "").replace(".", " ").replaceAll("\\s+", " ").trim();
        
        String url = "https://www.opensubtitles.org/ru/search/sublanguageid-rus/moviebytesize-0/sublanguageid-all/idmovie-0/searchonlymovies-on/moviename-" + Uri.encode(title);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            startActivity(intent);
            Utils.showText(playerView, "Поиск в OpenSubtitles: " + title);
        } catch (Exception e) {
            Toast.makeText(this, "Браузер не найден!", Toast.LENGTH_SHORT).show();
        }
    }

    private void showSubtitleDelayDialog() {
        String[] options = new String[] { "-2000 ms", "-1000 ms", "-500 ms", "0 ms (Норм)", "+500 ms", "+1000 ms", "+2000 ms" };

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.JudAsDialogTheme);
        builder.setTitle("Задержка субтитров");
        builder.setItems(options, (dialog, which) -> {
            Utils.showText(playerView, "Смещение субтитров: " + options[which]);
        });
        builder.create().show();
    }

    private void showMediaFormatNotification() {
        if (player == null || playerView == null) return;

        Format vf = player.getVideoFormat();
        Format af = player.getAudioFormat();

        List<String> badges = new ArrayList<>();

        if (vf != null && vf.height > 0) {
            if (vf.height >= 2160 || vf.width >= 3840) {
                badges.add("4K UHD");
            } else if (vf.height >= 1080) {
                badges.add("1080p FHD");
            } else if (vf.height >= 720) {
                badges.add("720p HD");
            }
        }

        if (vf != null) {
            String mime = vf.sampleMimeType != null ? vf.sampleMimeType.toLowerCase(Locale.US) : "";
            if (mime.contains("dolby-vision") || mime.contains("dv")) {
                badges.add("Dolby Vision");
            } else if (vf.colorInfo != null && (vf.colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084 || vf.colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG)) {
                badges.add("HDR10+");
            }
        }

        if (af != null) {
            String mime = af.sampleMimeType != null ? af.sampleMimeType.toLowerCase(Locale.US) : "";
            String audioLabel = "Dolby Audio";
            if (mime.contains("dts")) {
                audioLabel = "DTS-HD";
            } else if (mime.contains("truehd") || mime.contains("atmos")) {
                audioLabel = "Dolby Atmos";
            } else if (mime.contains("eac3")) {
                audioLabel = "Dolby Digital Plus";
            } else if (mime.contains("ac3")) {
                audioLabel = "Dolby Digital";
            }

            if (af.channelCount >= 8) {
                audioLabel += " 7.1";
            } else if (af.channelCount >= 6) {
                audioLabel += " 5.1";
            }
            badges.add(audioLabel);
        }

        if (badges.isEmpty()) return;

        FrameLayout centerView = playerView.findViewById(R.id.exo_controls_background);
        if (centerView == null) return;

        if (mediaFormatNotificationView != null) {
            centerView.removeView(mediaFormatNotificationView);
        }

        LinearLayout badgeContainer = new LinearLayout(this);
        badgeContainer.setOrientation(LinearLayout.HORIZONTAL);
        badgeContainer.setGravity(Gravity.CENTER_VERTICAL);
        badgeContainer.setPadding(Utils.dpToPx(12), Utils.dpToPx(8), Utils.dpToPx(12), Utils.dpToPx(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#EE071A12"));
        bg.setStroke(Utils.dpToPx(2), Color.parseColor("#10B981"));
        bg.setCornerRadius(Utils.dpToPx(12));
        badgeContainer.setBackground(bg);

        for (int i = 0; i < badges.size(); i++) {
            TextView tagTv = new TextView(this);
            tagTv.setText(badges.get(i));
            tagTv.setTextColor(Color.WHITE);
            tagTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tagTv.setTypeface(Typeface.DEFAULT_BOLD);
            tagTv.setPadding(Utils.dpToPx(10), Utils.dpToPx(4), Utils.dpToPx(10), Utils.dpToPx(4));

            GradientDrawable tagBg = new GradientDrawable();
            tagBg.setColor(Color.parseColor("#0B2419"));
            tagBg.setCornerRadius(Utils.dpToPx(6));
            tagTv.setBackground(tagBg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.setMargins(Utils.dpToPx(8), 0, 0, 0);
            tagTv.setLayoutParams(lp);

            badgeContainer.addView(tagTv);
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setMargins(Utils.dpToPx(32), Utils.dpToPx(16), 0, 0);

        badgeContainer.setAlpha(0f);
        centerView.addView(badgeContainer, params);
        mediaFormatNotificationView = badgeContainer;

        badgeContainer.animate().alpha(1f).setDuration(250).start();

        formatNotificationHandler.removeCallbacksAndMessages(null);
        formatNotificationHandler.postDelayed(() -> {
            if (mediaFormatNotificationView != null) {
                mediaFormatNotificationView.animate().alpha(0f).setDuration(400).withEndAction(() -> {
                    if (mediaFormatNotificationView != null && centerView != null) {
                        centerView.removeView(mediaFormatNotificationView);
                        mediaFormatNotificationView = null;
                    }
                }).start();
            }
        }, 3500);
    }

    private void autoSelectRussianAudioTrack() {
        if (trackSelector == null) return;
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo == null) return;

        for (int r = 0; r < mappedTrackInfo.getRendererCount(); r++) {
            if (mappedTrackInfo.getRendererType(r) == C.TRACK_TYPE_AUDIO) {
                TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(r);
                for (int g = 0; g < trackGroups.length; g++) {
                    TrackGroup group = trackGroups.get(g);
                    for (int t = 0; t < group.length; t++) {
                        Format f = group.getFormat(t);
                        if (f.language != null) {
                            String lang = f.language.toLowerCase(Locale.US);
                            if (lang.contains("ru") || lang.contains("rus") || lang.contains("ру")) {
                                setSelectedTrackAudio(g, false);
                                return;
                            }
                        }
                    }
                }
            }
        }
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
                if (isVirtualSeeking) {
                    confirmVirtualSeek();
                    return true;
                }
                if (event.getRepeatCount() > 2) {
                    if (!isFastForwarding && player != null) {
                        isFastForwarding = true;
                        player.setPlaybackParameters(new PlaybackParameters(2.0f));
                        Utils.showText(playerView, "2.0x >>");
                    }
                    return true;
                }
                if (!controllerVisibleFully) {
                    if (playerView != null) {
                        playerView.showController();
                    }
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_BUTTON_L2:
                if (controllerVisibleFully) {
                    return super.onKeyDown(keyCode, event);
                }
                return handleDpadSeek(false);

            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BUTTON_R2:
                if (controllerVisibleFully) {
                    return super.onKeyDown(keyCode, event);
                }
                return handleDpadSeek(true);

            case KeyEvent.KEYCODE_BACK:
                onBackPressed();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_SELECT:
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SPACE:
                if (isFastForwarding && player != null) {
                    isFastForwarding = false;
                    player.setPlaybackParameters(new PlaybackParameters(1.0f));
                    Utils.showText(playerView, "1.0x");
                    return true;
                }
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean handleDpadSeek(boolean forward) {
        if (player == null) return false;
        long duration = player.getDuration();
        if (duration <= 0) return false;

        long now = System.currentTimeMillis();
        if (now - lastDpadPressTime < 400) {
            dpadRepeatCount++;
        } else {
            dpadRepeatCount = 1;
        }
        lastDpadPressTime = now;

        long stepMs = 5000;
        if (dpadRepeatCount > 20) stepMs = 120000;
        else if (dpadRepeatCount > 12) stepMs = 60000;
        else if (dpadRepeatCount > 7) stepMs = 30000;
        else if (dpadRepeatCount > 3) stepMs = 10000;

        if (!isVirtualSeeking) {
            isVirtualSeeking = true;
            virtualSeekPosition = player.getCurrentPosition();
            if (thumbnailSeekerPopup != null) {
                thumbnailSeekerPopup.setVisibility(View.VISIBLE);
                thumbnailSeekerPopup.setAlpha(0f);
                thumbnailSeekerPopup.animate().alpha(1f).setDuration(120).start();
            }
        }

        virtualSeekPosition = Math.max(0, Math.min(duration, virtualSeekPosition + (forward ? stepMs : -stepMs)));

        if (thumbnailTimeText != null) {
            thumbnailTimeText.setText(Utils.formatMilis(virtualSeekPosition));
        }

        CustomDefaultTimeBar timeBar = playerView.findViewById(R.id.exo_progress);
        if (timeBar != null && thumbnailSeekerPopup != null) {
            int x = timeBar.getScrubberXForPosition(virtualSeekPosition, duration);
            int popupWidth = thumbnailSeekerPopup.getWidth();
            if (popupWidth <= 0) popupWidth = Utils.dpToPx(208);

            float targetX = Math.max(Utils.dpToPx(16), Math.min(playerView.getWidth() - popupWidth - Utils.dpToPx(16), x - (popupWidth / 2f)));

            thumbnailSeekerPopup.animate()
                    .translationX(targetX)
                    .setDuration(100)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }

        if (thumbnailProvider != null) {
            thumbnailProvider.loadThumbnail(virtualSeekPosition, (timeMs, bitmap) -> {
                if (thumbnailImageView != null && isVirtualSeeking) {
                    thumbnailImageView.setImageBitmap(bitmap);
                }
            });
        }

        confirmSeekHandler.removeCallbacks(confirmSeekRunnable);
        confirmSeekHandler.postDelayed(confirmSeekRunnable, 1500);

        return true;
    }

    private void confirmVirtualSeek() {
        confirmSeekHandler.removeCallbacks(confirmSeekRunnable);
        if (isVirtualSeeking && player != null) {
            player.seekTo(virtualSeekPosition);
            if (thumbnailSeekerPopup != null) {
                thumbnailSeekerPopup.setVisibility(View.GONE);
            }
            isVirtualSeeking = false;
        }
    }

    private void cancelVirtualSeek() {
        confirmSeekHandler.removeCallbacks(confirmSeekRunnable);
        if (thumbnailSeekerPopup != null) {
            thumbnailSeekerPopup.setVisibility(View.GONE);
        }
        isVirtualSeeking = false;
    }

    @Override
    public void onBackPressed() {
        if (isVirtualSeeking) {
            cancelVirtualSeek();
            return;
        }
        if (controllerVisible && player != null && player.isPlaying()) {
            if (playerView != null) {
                playerView.hideController();
            }
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - backPressedTime > 2000) {
            backToast = Toast.makeText(this, "Press BACK again to exit", Toast.LENGTH_SHORT);
            backToast.show();
            backPressedTime = currentTime;
        } else {
            if (backToast != null) {
                backToast.cancel();
            }
            finish();
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
        View videoSurface = playerView.getVideoSurfaceView();
        int mode = playerView.getResizeMode();
        int nextMode;
        String modeName;

        if (mode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
            nextMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
            modeName = "Zoom / Crop";
            if (videoSurface != null) {
                videoSurface.setScaleX(1.335f);
                videoSurface.setScaleY(1.335f);
            }
        } else if (mode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
            nextMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
            modeName = "Stretch / Fill";
            if (videoSurface != null) {
                videoSurface.setScaleX(1.335f);
                videoSurface.setScaleY(1.0f);
            }
        } else {
            nextMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
            modeName = "Fit (100%)";
            if (videoSurface != null) {
                videoSurface.setScaleX(1.0f);
                videoSurface.setScaleY(1.0f);
            }
        }

        playerView.setResizeMode(nextMode);
        playerView.requestLayout();
        mPrefs.resizeMode = nextMode;
        Utils.showText(playerView, modeName);
    }

    public static class PlaylistItem {
        public String title;
        public Uri uri;
        public String poster;

        public PlaylistItem(String title, Uri uri, String poster) {
            this.title = title;
            this.uri = uri;
            this.poster = poster;
        }
    }

    private List<PlaylistItem> parsePlaylistFromIntent() {
        List<PlaylistItem> list = new ArrayList<>();
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle extras = intent.getExtras();
            String[] keys = new String[] { "video_list", "playlist", "urls", "video_urls", "episodes", "playlist_items", "videos" };
            for (String key : keys) {
                if (extras.containsKey(key)) {
                    Object obj = extras.get(key);
                    if (obj instanceof String) {
                        try {
                            JSONArray array = new JSONArray((String) obj);
                            for (int i = 0; i < array.length(); i++) {
                                JSONObject item = array.getJSONObject(i);
                                String title = item.optString("title", item.optString("name", item.optString("label", "Серия " + (i + 1))));
                                String url = item.optString("url", item.optString("file", item.optString("link", "")));
                                String poster = item.optString("poster", item.optString("image", item.optString("cover", "")));
                                if (!TextUtils.isEmpty(url)) {
                                    list.add(new PlaylistItem(title, Uri.parse(url), poster));
                                }
                            }
                        } catch (Exception ignored) {}
                    } else if (obj instanceof String[]) {
                        String[] arr = (String[]) obj;
                        for (int i = 0; i < arr.length; i++) {
                            list.add(new PlaylistItem("Серия " + (i + 1), Uri.parse(arr[i]), null));
                        }
                    } else if (obj instanceof ArrayList) {
                        ArrayList<?> listObj = (ArrayList<?>) obj;
                        for (int i = 0; i < listObj.size(); i++) {
                            Object elem = listObj.get(i);
                            if (elem instanceof Bundle) {
                                Bundle b = (Bundle) elem;
                                String title = b.getString("title", "Серия " + (i + 1));
                                String url = b.getString("url", b.getString("file", ""));
                                String poster = b.getString("poster", b.getString("image", ""));
                                if (!TextUtils.isEmpty(url)) {
                                    list.add(new PlaylistItem(title, Uri.parse(url), poster));
                                }
                            } else if (elem instanceof Map) {
                                Map<?, ?> map = (Map<?, ?>) elem;
                                String title = String.valueOf(map.get("title") != null ? map.get("title") : "Серия " + (i + 1));
                                String url = String.valueOf(map.get("url") != null ? map.get("url") : map.get("file"));
                                if (!TextUtils.isEmpty(url) && !"null".equals(url)) {
                                    list.add(new PlaylistItem(title, Uri.parse(url), null));
                                }
                            } else if (elem instanceof String) {
                                list.add(new PlaylistItem("Серия " + (i + 1), Uri.parse((String) elem), null));
                            }
                        }
                    }
                }
            }
        }

        // Fallback: If no intent playlist was passed, auto-detect season/episode pattern (e.g., s24e01 / s24e1 / index=1)
        if (list.isEmpty() && mPrefs.mediaUri != null) {
            String urlStr = mPrefs.mediaUri.toString();
            String titleExtra = getIntent() != null ? getIntent().getStringExtra("title") : null;
            if (TextUtils.isEmpty(titleExtra)) titleExtra = Utils.getFileName(this, mPrefs.mediaUri);

            Pattern epPattern = Pattern.compile("(?i)(s\\d+e|season\\d+ep|\\d+x|\\be|index=|file=|episode=)(\\d+)");
            Matcher matcher = epPattern.matcher(urlStr);
            boolean foundInUrl = matcher.find();
            if (!foundInUrl && !TextUtils.isEmpty(titleExtra)) {
                matcher = epPattern.matcher(titleExtra);
                foundInUrl = matcher.find();
            }

            if (foundInUrl) {
                String prefix = matcher.group(1);
                String numStr = matcher.group(2);
                if (!TextUtils.isEmpty(prefix) && !TextUtils.isEmpty(numStr)) {
                    try {
                        int currentEp = Integer.parseInt(numStr);
                        activeEpisodeIndex = Math.max(0, currentEp - 1);
                        int padLength = numStr.length();
                        int maxEp = Math.max(12, currentEp);
                        for (int ep = 1; ep <= maxEp; ep++) {
                            String formattedEp = padLength > 1 ? String.format(Locale.US, "%0" + padLength + "d", ep) : String.valueOf(ep);
                            String newUrl = urlStr.contains(prefix + numStr) ? 
                                    urlStr.replace(prefix + numStr, prefix + formattedEp) : 
                                    urlStr + (urlStr.contains("?") ? "&" : "?") + "index=" + ep;
                            String epTitle = "Серия " + ep;
                            list.add(new PlaylistItem(epTitle, Uri.parse(newUrl), null));
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        return list;
    }

    private void showEpisodesSideMenu() {
        if (player == null) return;

        if (activePlaylist.isEmpty()) {
            activePlaylist.addAll(parsePlaylistFromIntent());
        }

        if (activePlaylist.isEmpty()) {
            Toast.makeText(this, "Плейлист серий недоступен для текущего видео", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(true);
        scrollView.setClipChildren(false);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(32, 32, 32, 32);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#F2071A12"));
        cardBg.setStroke(2, Color.parseColor("#10B981"));
        cardBg.setCornerRadius(Utils.dpToPx(16));
        container.setBackground(cardBg);

        TextView headerTv = new TextView(this);
        headerTv.setText("Серии и Плейлист (" + activePlaylist.size() + ")");
        headerTv.setTextColor(Color.parseColor("#10B981"));
        headerTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        headerTv.setTypeface(Typeface.DEFAULT_BOLD);
        headerTv.setPadding(8, 0, 8, 20);
        container.addView(headerTv);

        for (int i = 0; i < activePlaylist.size(); i++) {
            PlaylistItem item = activePlaylist.get(i);
            int index = i;
            boolean isCurrent = (i == activeEpisodeIndex);

            TextView itemTv = createJudasMenuItem((isCurrent ? "▶ " : "") + item.title, v -> {
                dialog.dismiss();
                activeEpisodeIndex = index;
                playPlaylistItem(item);
            });

            if (isCurrent) {
                GradientDrawable currentBg = new GradientDrawable();
                currentBg.setColor(Color.parseColor("#10B981"));
                currentBg.setCornerRadius(Utils.dpToPx(8));
                itemTv.setBackground(currentBg);
                itemTv.setTextColor(Color.parseColor("#04100B"));
                itemTv.setTypeface(Typeface.DEFAULT_BOLD);
            }

            container.addView(itemTv);
        }

        TextView itemNext = createJudasMenuItem("▶ Следующая серия (Серия " + (activePlaylist.size() + 1) + ")", v -> {
            dialog.dismiss();
            skipToNext();
        });
        container.addView(itemNext);

        scrollView.addView(container);
        dialog.setContentView(scrollView);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.gravity = Gravity.BOTTOM | Gravity.END;
            lp.width = Utils.dpToPx(420);
            lp.height = Utils.dpToPx(500);
            lp.x = Utils.dpToPx(24);
            lp.y = Utils.dpToPx(60);
            window.setAttributes(lp);
        }

        dialog.show();
    }

    private void playPlaylistItem(PlaylistItem item) {
        if (item == null || item.uri == null) return;
        if (getIntent() != null) {
            getIntent().removeExtra("position");
            getIntent().removeExtra("startfrom");
        }
        releasePlayer();
        mPrefs.updateMedia(this, item.uri, null);
        Utils.showText(playerView, "Переключение: " + item.title);
        initializePlayer();
    }

    private final Handler osdHandler = new Handler(Looper.getMainLooper());
    private final Runnable osdRunnable = new Runnable() {
        @Override
        public void run() {
            if (osdOverlayLayout != null && osdOverlayLayout.getVisibility() == View.VISIBLE) {
                updateOsdText();
                osdHandler.postDelayed(this, 100);
            }
        }
    };

    private void toggleOsdOverlay() {
        if (osdOverlayLayout == null) return;
        if (osdOverlayLayout.getVisibility() == View.VISIBLE) {
            osdOverlayLayout.setVisibility(View.GONE);
            osdHandler.removeCallbacks(osdRunnable);
        } else {
            osdOverlayLayout.setVisibility(View.VISIBLE);
            osdRunnable.run();
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
        sb.append("AFR: ").append(mPrefs.afrEnabled ? "Включен (Авто)" : "Выключен").append("\n");
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
        haveMedia = mPrefs.mediaUri != null && Utils.isSupportedUri(this, mPrefs.mediaUri);

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

            // SAFE TURBO BOOST: DefaultLoadControl with low bufferForPlaybackMs for instant start
            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                            32000, // minBufferMs
                            50000, // maxBufferMs
                            500,   // bufferForPlaybackMs (0.5s for instant playback!)
                            1000   // bufferForPlaybackAfterRebufferMs (1s)
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();

            // SAFE HARDWARE RENDERING: Must be OFF to prevent software decoder crash on Android TV (Amlogic/MediaTek)
            RenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
            final DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                    .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE);

            Map<String, String> httpHeaders = parseIntentHeaders();
            
            // SAFE UNIVERSAL DATA SOURCE: HTTP with timeouts + Content/File wrapper
            DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                    .setUserAgent(Util.getUserAgent(this, "JudAs"))
                    .setConnectTimeoutMs(8000)
                    .setReadTimeoutMs(8000)
                    .setAllowCrossProtocolRedirects(true);
            if (httpHeaders != null && !httpHeaders.isEmpty()) {
                httpDataSourceFactory.setDefaultRequestProperties(httpHeaders);
            }
            
            com.google.android.exoplayer2.upstream.DefaultDataSourceFactory dataSourceFactory = 
                    new com.google.android.exoplayer2.upstream.DefaultDataSourceFactory(this, httpDataSourceFactory);
            
            DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory);

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

            if (mPrefs.subtitleUri != null) {
                String mimeType = MimeTypes.APPLICATION_SUBRIP;
                String subPath = mPrefs.subtitleUri.toString().toLowerCase(Locale.US);
                if (subPath.endsWith(".vtt")) {
                    mimeType = MimeTypes.TEXT_VTT;
                } else if (subPath.endsWith(".ass") || subPath.endsWith(".ssa")) {
                    mimeType = MimeTypes.TEXT_SSA;
                }
                MediaItem.Subtitle subtitle = new MediaItem.Subtitle(
                        mPrefs.subtitleUri,
                        mimeType,
                        "ru",
                        C.SELECTION_FLAG_DEFAULT
                );
                mediaItemBuilder.setSubtitles(Collections.singletonList(subtitle));
            }

            player.setMediaItem(mediaItemBuilder.build());

            videoLoading = true;
            updateLoading(true);

            long startPos = getLampaStartPosition();
            if (startPos <= 0) {
                boolean isTorrServerOrStream = mPrefs.mediaUri != null && 
                    (mPrefs.mediaUri.toString().contains("127.0.0.1") || 
                     mPrefs.mediaUri.toString().contains("localhost") || 
                     mPrefs.mediaUri.toString().contains(":8090") || 
                     mPrefs.mediaUri.toString().contains(":8080"));
                if (!isTorrServerOrStream) {
                    startPos = mPrefs.getPosition();
                }
            }
            if (startPos > 0) {
                player.seekTo(startPos);
            } else {
                player.seekTo(0);
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

        player.setPlayWhenReady(true);
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
        if (thumbnailProvider != null) {
            thumbnailProvider.release();
            thumbnailProvider = null;
        }
        if (loudnessEnhancer != null) {
            try { loudnessEnhancer.release(); } catch (Exception ignored) {}
            loudnessEnhancer = null;
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
        public void onEvents(Player player, Player.Events events) {
            if (playerView != null && player != null) {
                CustomDefaultTimeBar timeBar = playerView.findViewById(R.id.exo_progress);
                if (timeBar != null && player.getDuration() > 0) {
                    timeBar.updateBufferProgress(player.getBufferedPosition(), player.getDuration());
                }
            }
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_READY) {
                if (videoLoading) {
                    videoLoading = false;
                    autoSelectRussianAudioTrack();
                    showMediaFormatNotification();
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
        if (!mPrefs.afrEnabled) return;
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
                    Utils.showText(playerView, String.format(Locale.US, "AFR: %.2f Гц", bestMode.getRefreshRate()));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player != null && player.isPlaying()) {
            try {
                if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                    PictureInPictureParams params = new PictureInPictureParams.Builder()
                            .setAspectRatio(new Rational(16, 9))
                            .build();
                    enterPictureInPictureMode(params);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (playerView != null) {
            if (isInPictureInPictureMode) {
                playerView.hideController();
                playerView.setUseController(false);
            } else {
                playerView.setUseController(true);
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
        if (activePlaylist.isEmpty()) {
            activePlaylist.addAll(parsePlaylistFromIntent());
        }

        Uri currentUri = mPrefs.mediaUri;
        int currentIndex = -1;
        if (currentUri != null && !activePlaylist.isEmpty()) {
            for (int i = 0; i < activePlaylist.size(); i++) {
                if (activePlaylist.get(i).uri != null && activePlaylist.get(i).uri.equals(currentUri)) {
                    currentIndex = i;
                    break;
                }
            }
        }

        if (currentIndex >= 0 && currentIndex + 1 < activePlaylist.size()) {
            playPlaylistItem(activePlaylist.get(currentIndex + 1));
        } else {
            int nextEpNum = activePlaylist.size() + 1;
            String urlStr = currentUri != null ? currentUri.toString() : "";
            Pattern epPattern = Pattern.compile("(?i)(s\\d+e|season\\d+ep|\\d+x|\\be)(\\d+)");
            Matcher matcher = epPattern.matcher(urlStr);
            if (matcher.find()) {
                String prefix = matcher.group(1);
                String numStr = matcher.group(2);
                if (!TextUtils.isEmpty(prefix) && !TextUtils.isEmpty(numStr)) {
                    int padLength = numStr.length();
                    String formattedEp = padLength > 1 ? String.format(Locale.US, "%0" + padLength + "d", nextEpNum) : String.valueOf(nextEpNum);
                    String newUrl = urlStr.replace(prefix + numStr, prefix + formattedEp);
                    PlaylistItem nextItem = new PlaylistItem("Серия " + nextEpNum, Uri.parse(newUrl), null);
                    activePlaylist.add(nextItem);
                    playPlaylistItem(nextItem);
                } else if (nextUri != null) {
                    mPrefs.updateMedia(this, nextUri, null);
                    initializePlayer();
                }
            } else if (nextUri != null) {
                mPrefs.updateMedia(this, nextUri, null);
                initializePlayer();
            }
        }
    }

    private void dispatchPlayPause() {
        if (player == null) return;
        if (player.getPlaybackState() == Player.STATE_ENDED) {
            player.seekTo(0);
            player.play();
        } else if (player.isPlaying()) {
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

    private void updateSubtitleStyle() {
        if (playerView == null) return;
        SubtitleView subtitleView = playerView.findViewById(R.id.exo_subtitles);
        if (subtitleView != null) {
            CaptionStyleCompat style = new CaptionStyleCompat(
                    mPrefs.subtitleColor,
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                    Color.BLACK,
                    Typeface.DEFAULT_BOLD
            );
            subtitleView.setStyle(style);
            subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * mPrefs.subtitleSize);
        }
    }
    void searchSubtitles() {}

    public static class ThumbnailProvider {
        public interface ThumbnailCallback {
            void onThumbnailLoaded(long timeMs, android.graphics.Bitmap bitmap);
        }

        private static final int MAX_CACHE_MEMORY_BYTES = 16 * 1024 * 1024;
        private final LruCache<Long, android.graphics.Bitmap> mCache;
        private final ExecutorService mExecutorService;
        private final Handler mMainHandler;

        private MediaMetadataRetriever mRetriever;
        private Uri mCurrentUri;
        private boolean mIsInitialized = false;

        public ThumbnailProvider() {
            mCache = new LruCache<Long, android.graphics.Bitmap>(MAX_CACHE_MEMORY_BYTES) {
                @Override
                protected int sizeOf(Long key, android.graphics.Bitmap bitmap) {
                    return bitmap.getByteCount();
                }
            };
            mExecutorService = Executors.newSingleThreadExecutor();
            mMainHandler = new Handler(Looper.getMainLooper());
        }

        public void setSource(Context context, Uri uri, Map<String, String> headers) {
            if (uri == null) return;
            if (mCurrentUri != null && mCurrentUri.equals(uri)) return;

            mCurrentUri = uri;
            mIsInitialized = false;
            mCache.evictAll();

            mExecutorService.execute(() -> {
                try {
                    if (mRetriever != null) {
                        try { mRetriever.release(); } catch (Exception ignored) {}
                    }
                    mRetriever = new MediaMetadataRetriever();
                    if (headers != null && !headers.isEmpty()) {
                        mRetriever.setDataSource(uri.toString(), headers);
                    } else {
                        mRetriever.setDataSource(context, uri);
                    }
                    mIsInitialized = true;
                } catch (Exception e) {
                    e.printStackTrace();
                    mIsInitialized = false;
                }
            });
        }

        public void loadThumbnail(long timeMs, ThumbnailCallback callback) {
            long cacheKey = (timeMs / 5000) * 5000;
            android.graphics.Bitmap cachedBitmap = mCache.get(cacheKey);
            if (cachedBitmap != null) {
                callback.onThumbnailLoaded(timeMs, cachedBitmap);
                return;
            }

            mExecutorService.execute(() -> {
                if (!mIsInitialized || mRetriever == null) return;
                try {
                    long timeUs = timeMs * 1000;
                    android.graphics.Bitmap rawFrame = mRetriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (rawFrame == null) {
                        rawFrame = mRetriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST);
                    }
                    if (rawFrame != null) {
                        int targetWidth = 240;
                        int targetHeight = (int) (240f * rawFrame.getHeight() / rawFrame.getWidth());
                        if (targetHeight <= 0) targetHeight = 135;

                        android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(rawFrame, targetWidth, targetHeight, true);
                        if (scaled != rawFrame) {
                            rawFrame.recycle();
                        }
                        mCache.put(cacheKey, scaled);

                        mMainHandler.post(() -> callback.onThumbnailLoaded(timeMs, scaled));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        public void release() {
            mExecutorService.execute(() -> {
                if (mRetriever != null) {
                    try { mRetriever.release(); } catch (Exception ignored) {}
                    mRetriever = null;
                }
            });
            mExecutorService.shutdown();
            mCache.evictAll();
        }
    }
}
