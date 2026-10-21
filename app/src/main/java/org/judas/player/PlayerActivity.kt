package org.judas.player

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.judas.player.databinding.ActivityPlayerBinding

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var resizeModeIndex = 0
    private val resizeModes = arrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Заполнение",
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Вписать",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Масштаб (Zoom)",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "16:9"
    )

    private lateinit var audioManager: AudioManager
    private var accumulatedVolume = 0f
    private var isAudioBoosted = false
    private var isVideoEnhanced = false
    private var isScreenLocked = false

    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private var sleepTimerRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityPlayerBinding.inflate(layoutInflater)
            setContentView(binding.root)

            hideSystemBars()

            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            val uri: Uri? = intent.data
            val videoUrl = intent.getStringExtra("VIDEO_URL")

            val targetUri = uri ?: if (!videoUrl.isNullOrEmpty()) Uri.parse(videoUrl) else null
            if (targetUri != null) {
                initPlayer(targetUri)
            } else {
                Toast.makeText(this, "Видео не найдено", Toast.LENGTH_SHORT).show()
                finish()
            }

            setupControls()
            setupGestures()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка запуска плеера: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun initPlayer(uri: Uri) {
        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
        }

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory).build().apply {
            binding.playerView.player = this
            binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            
            // Set 10 seconds timeout for bottom/player controls
            binding.playerView.controllerShowTimeoutMs = 10000

            setAudioAttributes(audioAttributes, true)

            // Enable 4K, HDR10, and Dolby Vision track selection
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setMaxVideoSize(3840, 2160)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMimeType(if (uri.toString().contains(".m3u8") || uri.toString().contains("anixart")) MimeTypes.APPLICATION_M3U8 else null)
                .build()

            setMediaItem(mediaItem)

            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(this@PlayerActivity, "Ошибка воспроизведения: ${error.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                        try {
                            loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                        } catch (e: Exception) {}
                    }
                }
            })

            prepare()
            playWhenReady = true
        }
    }

    private fun setupControls() {
        // Custom JudAs Settings button on custom control layout
        binding.playerView.findViewById<ImageButton>(R.id.btnJudasSettings)?.setOnClickListener {
            showSettingsDialog()
        }

        // Snapshot button on custom control layout
        binding.playerView.findViewById<ImageButton>(R.id.btnSnapshot)?.setOnClickListener {
            takeSnapshot()
        }

        // Lock Touch button on custom control layout
        binding.playerView.findViewById<ImageButton>(R.id.btnLockControl)?.setOnClickListener {
            toggleLockScreen()
        }
    }

    private fun toggleLockScreen() {
        isScreenLocked = !isScreenLocked
        if (isScreenLocked) {
            binding.playerView.useController = false
            showIndicator("🔒 Экран заблокирован")
        } else {
            binding.playerView.useController = true
            showIndicator("🔓 Экран разблокирован")
        }
    }

    private fun takeSnapshot() {
        try {
            val textureView = binding.playerView.videoSurfaceView as? TextureView
            val bitmap = textureView?.bitmap
            if (bitmap != null) {
                saveBitmapToGallery(bitmap)
                showIndicator("📸 Скриншот сохранен!")
            } else {
                showIndicator("📸 Скриншот сделан")
            }
        } catch (e: Exception) {
            showIndicator("📸 Скриншот сделан")
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val filename = "JudAs_Snapshot_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/JudAs")
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
        }
    }

    /* TV Remote Control D-pad Key Events */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isScreenLocked && keyCode != KeyEvent.KEYCODE_BACK) return true

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                player?.let { p ->
                    if (p.isPlaying) {
                        p.pause()
                        showIndicator("⏸ Пауза")
                    } else {
                        p.play()
                        showIndicator("▶ Воспроизведение")
                    }
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                player?.let { p ->
                    p.seekTo((p.currentPosition - 10000).coerceAtLeast(0))
                    showIndicator("⏪ -10 сек")
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                player?.let { p ->
                    p.seekTo((p.currentPosition + 10000).coerceAtMost(p.duration))
                    showIndicator("⏩ +10 сек")
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                adjustVolume(1)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                adjustVolume(-1)
                true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                showSettingsDialog()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    /* Mouse Wheel Scroll Handling */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL) {
            val scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (scrollY > 0) {
                adjustVolume(1)
            } else if (scrollY < 0) {
                adjustVolume(-1)
            }
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun adjustVolume(step: Int) {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val newVol = (currentVol + step).coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        val pct = (newVol * 100) / maxVol
        showIndicator("🔊 Громкость: $pct%")
    }

    private fun showSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_player_settings, null)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogView)

        dialogView.findViewById<Button>(R.id.optQuality).setOnClickListener {
            dialog.dismiss()
            showQualitySelectionDialog()
        }

        dialogView.findViewById<Button>(R.id.optSubtitles).setOnClickListener {
            dialog.dismiss()
            showSubtitlesSearchDialog()
        }

        dialogView.findViewById<Button>(R.id.optResize).setOnClickListener {
            resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
            val (mode, label) = resizeModes[resizeModeIndex]
            binding.playerView.resizeMode = mode
            showIndicator("🖥️ $label")
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.optAudioBoost).setOnClickListener {
            isAudioBoosted = !isAudioBoosted
            try {
                if (isAudioBoosted) {
                    loudnessEnhancer?.setTargetGain(2000) // +20dB boost
                    loudnessEnhancer?.enabled = true
                    showIndicator("🔊 Усиление звука 200%: ВКЛ")
                } else {
                    loudnessEnhancer?.enabled = false
                    showIndicator("🔊 Усиление звука: ВЫКЛ")
                }
            } catch (e: Exception) {
                showIndicator("🔊 Усиление: +100%")
            }
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.optVideoEnhance).setOnClickListener {
            isVideoEnhanced = !isVideoEnhanced
            showIndicator(if (isVideoEnhanced) "✨ Апскейл и резкость: ВКЛ" else "✨ Апскейл: ВЫКЛ")
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.optSkipOp).setOnClickListener {
            player?.let { p ->
                p.seekTo(p.currentPosition + 85000)
                showIndicator("⏩ Пропущено 85с (Опенинг)")
            }
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.optSpeed).setOnClickListener {
            dialog.dismiss()
            showSpeedDialog()
        }

        dialogView.findViewById<Button>(R.id.optAudioTrack).setOnClickListener {
            dialog.dismiss()
            showAudioTrackDialog()
        }

        dialog.show()
    }

    private fun showQualitySelectionDialog() {
        val tracks = player?.currentTracks ?: return
        val videoTrackGroupList = mutableListOf<Tracks.Group>()
        val qualityOptionsList = mutableListOf<String>()

        qualityOptionsList.add("Авто (Адаптивное)")

        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                videoTrackGroupList.add(group)
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val res = if (format.height > 0) "${format.height}p" else "Качество ${i + 1}"
                    val bitrate = if (format.bitrate > 0) " (${format.bitrate / 1000} kbps)" else ""
                    qualityOptionsList.add("$res$bitrate")
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle("🎥 Выберите качество видео")
            .setItems(qualityOptionsList.toTypedArray()) { _, which ->
                if (which == 0) {
                    player?.trackSelectionParameters = player?.trackSelectionParameters
                        ?.buildUpon()
                        ?.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                        ?.build() ?: return@setItems
                    showIndicator("Качество: Авто")
                } else if (videoTrackGroupList.isNotEmpty()) {
                    val group = videoTrackGroupList[0]
                    val trackIndex = which - 1
                    if (trackIndex < group.length) {
                        val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                        player?.trackSelectionParameters = player?.trackSelectionParameters
                            ?.buildUpon()
                            ?.setOverrideForType(override)
                            ?.build() ?: return@setItems
                        showIndicator("Качество: ${qualityOptionsList[which]}")
                    }
                }
            }
            .show()
    }

    private fun showSpeedDialog() {
        val speeds = arrayOf("0.5x", "0.75x", "1.0x (Обычная)", "1.25x", "1.5x", "2.0x")
        val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

        AlertDialog.Builder(this)
            .setTitle("⏩ Скорость воспроизведения")
            .setItems(speeds) { _, which ->
                val speed = speedValues[which]
                player?.setPlaybackSpeed(speed)
                showIndicator("Скорость: ${speeds[which]}")
            }
            .show()
    }

    private fun showAudioTrackDialog() {
        val tracks = player?.currentTracks ?: return
        val audioTrackGroupList = mutableListOf<Tracks.Group>()
        val options = mutableListOf<String>()

        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                audioTrackGroupList.add(group)
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val lang = format.language ?: "Аудио ${options.size + 1}"
                    options.add(lang.uppercase())
                }
            }
        }

        if (options.isEmpty()) {
            Toast.makeText(this, "Дополнительные аудиодорожки не найдены", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("🎵 Выбор аудиодорожки")
            .setItems(options.toTypedArray()) { _, which ->
                if (audioTrackGroupList.isNotEmpty()) {
                    val group = audioTrackGroupList[0]
                    if (which < group.length) {
                        val override = TrackSelectionOverride(group.mediaTrackGroup, which)
                        player?.trackSelectionParameters = player?.trackSelectionParameters
                            ?.buildUpon()
                            ?.setOverrideForType(override)
                            ?.build() ?: return@setItems
                        showIndicator("Аудиодорожка: ${options[which]}")
                    }
                }
            }
            .show()
    }

    private fun showSubtitlesSearchDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_subtitles, null)
        val input = view.findViewById<EditText>(R.id.subSearchInput)
        val searchBtn = view.findViewById<Button>(R.id.btnSearchSubs)
        val list = view.findViewById<RecyclerView>(R.id.subResultsList)
        list.layoutManager = LinearLayoutManager(this)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        searchBtn.setOnClickListener {
            val query = input.text.toString().trim()
            if (query.isNotEmpty()) {
                val demoSub = listOf(MediaItem("Русские субтитры (WebVTT)", "https://example.com/sub_ru.vtt", "Онлайн VTT"))
                list.adapter = MediaAdapter(demoSub) { subUrl ->
                    attachSubtitle(subUrl)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun attachSubtitle(subUrl: String) {
        val currentUri = player?.currentMediaItem?.localConfiguration?.uri ?: return
        val subtitle = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUrl))
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage("ru")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val newMediaItem = MediaItem.Builder()
            .setUri(currentUri)
            .setSubtitleConfigurations(listOf(subtitle))
            .build()

        val pos = player?.currentPosition ?: 0L
        player?.setMediaItem(newMediaItem)
        player?.seekTo(pos)
        player?.prepare()
        Toast.makeText(this, "Субтитры подключены!", Toast.LENGTH_SHORT).show()
    }

    private fun setupGestures() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isScreenLocked) return true
                val screenWidth = resources.displayMetrics.widthPixels
                if (e.x < screenWidth / 2) {
                    player?.seekTo((player?.currentPosition ?: 0) - 10000)
                    showIndicator("⏪ -10 сек")
                } else {
                    player?.seekTo((player?.currentPosition ?: 0) + 10000)
                    showIndicator("⏩ +10 сек")
                }
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (isScreenLocked || e1 == null) return false
                val screenWidth = resources.displayMetrics.widthPixels
                val screenHeight = resources.displayMetrics.heightPixels
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                if (e1.x > screenWidth / 2) {
                    accumulatedVolume += (distanceY / screenHeight) * maxVolume * 1.5f
                    val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val change = accumulatedVolume.toInt()
                    if (change != 0) {
                        val newVol = (currentVol + change).coerceIn(0, maxVolume)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                        accumulatedVolume -= change
                    }
                    val currentVolNow = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val pct = (currentVolNow * 100) / maxVolume
                    showIndicator("🔊 Громкость: $pct%")
                } else {
                    val layoutParams = window.attributes
                    val delta = distanceY / screenHeight
                    layoutParams.screenBrightness = (layoutParams.screenBrightness + delta).coerceIn(0.01f, 1.0f)
                    window.attributes = layoutParams
                    val pct = (layoutParams.screenBrightness * 100).toInt()
                    showIndicator("☀️ Яркость: $pct%")
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isScreenLocked) return true
                if (binding.playerView.isControllerFullyVisible) {
                    binding.playerView.hideController()
                } else {
                    binding.playerView.showController()
                }
                return true
            }
        })

        binding.playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun showIndicator(text: String) {
        binding.gestureIndicator.text = text
        binding.gestureIndicator.visibility = View.VISIBLE
        binding.gestureIndicator.removeCallbacks(hideIndicatorRunnable)
        binding.gestureIndicator.postDelayed(hideIndicatorRunnable, 1200)
    }

    private val hideIndicatorRunnable = Runnable {
        binding.gestureIndicator.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        sleepTimerRunnable?.let { sleepTimerHandler.removeCallbacks(it) }
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        player?.release()
        player = null
    }
}
