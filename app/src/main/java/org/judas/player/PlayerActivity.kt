package org.judas.player

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.judas.player.databinding.ActivityPlayerBinding

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityPlayerBinding.inflate(layoutInflater)
            setContentView(binding.root)

            val uri: Uri? = intent.data
            val videoUrl = intent.getStringExtra("VIDEO_URL")

            when {
                uri != null -> initPlayerWithUri(uri)
                !videoUrl.isNullOrEmpty() -> initPlayerWithUri(Uri.parse(videoUrl))
                else -> {
                    Toast.makeText(this, "Видео не найдено", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка запуска плеера: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initPlayerWithUri(uri: Uri) {
        try {
            player = ExoPlayer.Builder(this).build().apply {
                binding.playerView.player = this
                val mediaItem = MediaItem.fromUri(uri)
                setMediaItem(mediaItem)

                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Toast.makeText(this@PlayerActivity, "Ошибка воспроизведения: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                })

                prepare()
                playWhenReady = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка воспроизведения видео: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            player?.release()
            player = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
