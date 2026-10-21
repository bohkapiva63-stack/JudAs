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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.judas.player.databinding.ActivityPlayerBinding
import java.io.IOException

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private val httpClient = OkHttpClient()

    private var resizeModeIndex = 0
    private val resizeModes = arrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Экран: Заполнение",
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Экран: Вписать",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Экран: Масштаб",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "Экран: 16:9"
    )

    private var openingStart = -1L
    private var openingEnd = -1L

    private lateinit var audioManager: AudioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityPlayerBinding.inflate(layoutInflater)
            setContentView(binding.root)

            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            val uri: Uri? = intent.data
            val videoUrl = intent.getStringExtra("VIDEO_URL")

            val targetUri = uri ?: if (!videoUrl.isNullOrEmpty()) Uri.parse(videoUrl) else null
            if (targetUri != null) {
                initPlayer(targetUri)
                fetchAnimeSegments(targetUri.toString())
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

    private fun initPlayer(uri: Uri) {
        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }

        player = ExoPlayer.Builder(this, renderersFactory).build().apply {
            binding.playerView.player = this
            binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMimeType(if (uri.toString().contains(".m3u8") || uri.toString().contains("anixart")) MimeTypes.APPLICATION_M3U8 else null)
                .build()

            setMediaItem(mediaItem)

            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(this@PlayerActivity, "Кодек/воспроизведение: ${error.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    checkSegments(newPosition.positionMs)
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

        binding.btnSubtitles.setOnClickListener {
            showSubtitlesSearchDialog()
        }

        binding.btnSkipSegment.setOnClickListener {
            if (openingEnd > 0) {
                player?.seekTo(openingEnd)
                binding.btnSkipSegment.visibility = View.GONE
            }
        }
    }

    private fun checkSegments(currentPosMs: Long) {
        if (openingStart > 0 && openingEnd > 0) {
            if (currentPosMs in openingStart..openingEnd) {
                binding.btnSkipSegment.visibility = View.VISIBLE
            } else {
                binding.btnSkipSegment.visibility = View.GONE
            }
        }
    }

    private fun fetchAnimeSegments(url: String) {
        // Query Aniskip API for opening/ending segments
        val requestUrl = "https://api.aniskip.com/v2/skip-times/1/1?types=op&types=ed"
        val request = Request.Builder().url(requestUrl).build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { jsonStr ->
                    try {
                        val json = JSONObject(jsonStr)
                        if (json.getBoolean("found")) {
                            val results = json.getJSONArray("results")
                            for (i in 0 until results.length()) {
                                val item = results.getJSONObject(i)
                                val interval = item.getJSONObject("interval")
                                openingStart = (interval.getDouble("startTime") * 1000).toLong()
                                openingEnd = (interval.getDouble("endTime") * 1000).toLong()
                                break
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
        })
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
                val demoSub = listOf(MediaItem("Субтитры (Русские)", "https://example.com/sub_ru.vtt", "Онлайн VTT"))
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
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                if (e1.x > screenWidth / 2) {
                    val delta = (distanceY / 30).toInt()
                    val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val newVol = (currentVol + delta).coerceIn(0, maxVolume)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                    val pct = (newVol * 100) / maxVolume
                    showIndicator("🔊 Громкость: $pct%")
                } else {
                    val layoutParams = window.attributes
                    val delta = distanceY / 1000f
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
        binding.gestureIndicator.postDelayed({
            binding.gestureIndicator.visibility = View.GONE
        }, 1200)
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
