package org.judas.player

import android.app.AlertDialog
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
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
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.judas.player.databinding.ActivityPlayerBinding

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null

    private var resizeModeIndex = 0
    private val resizeModes = arrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Экран: Заполнение",
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Экран: Вписать",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Экран: Масштаб",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "Экран: 16:9"
    )

    private lateinit var audioManager: AudioManager
    private var accumulatedVolume = 0f

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
        }

        player = ExoPlayer.Builder(this, renderersFactory).build().apply {
            binding.playerView.player = this
            binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL

            binding.playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                binding.playerTopBar.visibility = visibility
            })

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMimeType(if (uri.toString().contains(".m3u8") || uri.toString().contains("anixart")) MimeTypes.APPLICATION_M3U8 else null)
                .build()

            setMediaItem(mediaItem)

            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(this@PlayerActivity, "Ошибка воспроизведения: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })

            prepare()
            playWhenReady = true
        }
    }

    private fun setupControls() {
        binding.btnResize.setOnClickListener {
            resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
            val (mode, label) = resizeModes[resizeModeIndex]
            binding.playerView.resizeMode = mode
            binding.btnResize.text = label
        }

        binding.btnQuality.setOnClickListener {
            showQualitySelectionDialog()
        }

        binding.btnSubtitles.setOnClickListener {
            showSubtitlesSearchDialog()
        }

        binding.btnSkipOp.setOnClickListener {
            player?.let { p ->
                p.seekTo(p.currentPosition + 85000)
                showIndicator("⏩ Пропущено 85с (Опенинг)")
            }
        }
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
                    Toast.makeText(this, "Качество: Авто", Toast.LENGTH_SHORT).show()
                } else if (videoTrackGroupList.isNotEmpty()) {
                    val group = videoTrackGroupList[0]
                    val trackIndex = which - 1
                    if (trackIndex < group.length) {
                        val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                        player?.trackSelectionParameters = player?.trackSelectionParameters
                            ?.buildUpon()
                            ?.setOverrideForType(override)
                            ?.build() ?: return@setItems
                        Toast.makeText(this, "Выбрано: ${qualityOptionsList[which]}", Toast.LENGTH_SHORT).show()
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
                val demoSub = listOf(MediaItem("Субтитры (Русские WebVTT)", "https://example.com/sub_ru.vtt", "Онлайн VTT"))
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
                if (e1 == null) return false
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
        player?.release()
        player = null
    }
}
