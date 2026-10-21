package org.judas.player

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.judas.player.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()
        showVideosTab()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_videos -> showVideosTab()
                R.id.nav_network -> showNetworkTab()
                R.id.nav_files -> showFilesTab()
                R.id.nav_lampa -> showLampaTab()
            }
            true
        }
    }

    private fun showVideosTab() {
        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
        val videoList = getLocalVideos()
        recyclerView.adapter = MediaAdapter(videoList) { videoPath ->
            playMedia(videoPath)
        }
        binding.contentContainer.removeAllViews()
        binding.contentContainer.addView(recyclerView)
    }

    private fun showNetworkTab() {
        val view = LayoutInflater.from(this).inflate(R.layout.tab_network, binding.contentContainer, false)
        val urlInput = view.findViewById<EditText>(R.id.urlInput)
        val playBtn = view.findViewById<Button>(R.id.playUrlBtn)
        val torrServerBtn = view.findViewById<Button>(R.id.torrServerBtn)

        playBtn.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                playMedia(url)
            } else {
                Toast.makeText(this, "Введите URL видео или потока", Toast.LENGTH_SHORT).show()
            }
        }

        torrServerBtn.setOnClickListener {
            urlInput.setText("http://127.0.0.1:8090/stream/")
        }

        binding.contentContainer.removeAllViews()
        binding.contentContainer.addView(view)
    }

    private fun showFilesTab() {
        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
        val downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val files = downloadFolder.listFiles()?.filter { it.extension in listOf("mp4", "mkv", "avi", "ts", "mov") }?.map {
            MediaItem(it.name, it.absolutePath, "Скачанный файл")
        } ?: emptyList()

        recyclerView.adapter = MediaAdapter(files) { filePath ->
            playMedia(filePath)
        }
        binding.contentContainer.removeAllViews()
        binding.contentContainer.addView(recyclerView)
    }

    private fun showLampaTab() {
        val view = LayoutInflater.from(this).inflate(R.layout.tab_lampa, binding.contentContainer, false)
        binding.contentContainer.removeAllViews()
        binding.contentContainer.addView(view)
    }

    private fun getLocalVideos(): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val projection = arrayOf(MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DATA)
        val cursor = contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, null)
        cursor?.use {
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            while (it.moveToNext()) {
                val title = it.getString(titleCol)
                val path = it.getString(dataCol)
                list.add(MediaItem(title, path, "Видеогалерея"))
            }
        }
        if (list.isEmpty()) {
            list.add(MediaItem("Пример торрента / TorrServer", "http://127.0.0.1:8090/stream/", "Онлайн поток"))
        }
        return list
    }

    private fun playMedia(pathOrUrl: String) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("VIDEO_URL", pathOrUrl)
        }
        startActivity(intent)
    }
}

data class MediaItem(val title: String, val path: String, val subtitle: String)

class MediaAdapter(
    private val items: List<MediaItem>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<MediaAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.mediaTitle)
        val subtitle: TextView = v.findViewById(R.id.mediaSubtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_media, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.subtitle.text = item.subtitle
        holder.itemView.setOnClickListener { onClick(item.path) }
    }

    override fun getItemCount() = items.size
}
