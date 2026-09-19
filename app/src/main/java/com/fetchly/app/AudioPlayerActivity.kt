package com.fetchly.app

import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import android.widget.TextView

class AudioPlayerActivity : BaseActivity() {

    companion object {
        var queue: List<Db.DownloadRecord> = emptyList()
        var startIndex = 0
    }

    private var player: MediaPlayer? = null
    private var index = 0
    private var shuffle = false
    private var repeatMode = 0 // 0 = off, 1 = repeat all, 2 = repeat one

    private val handler = Handler(Looper.getMainLooper())
    private val updater = object : Runnable {
        override fun run() {
            val p = player
            if (p != null && p.isPlaying) {
                val sb = findViewById<SeekBar>(R.id.sbAudio)
                sb.max = p.duration
                sb.progress = p.currentPosition
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_player)

        setVolumeControlStream(AudioManager.STREAM_MUSIC)

        index = if (startIndex in queue.indices) startIndex else 0
        if (queue.isEmpty()) run { finish(); return }

        val uri = intent.getStringExtra("uri")
        if (uri != null) {
            val match = queue.indexOfFirst { it.uri == uri }
            if (match >= 0) index = match
        }

        findViewById<TextView>(R.id.btnPlay).setOnClickListener { togglePlay() }
        findViewById<TextView>(R.id.btnNext).setOnClickListener { play(nextIndex()) }
        findViewById<TextView>(R.id.btnPrev).setOnClickListener { play(prevIndex()) }
        findViewById<TextView>(R.id.btnShuffle).setOnClickListener {
            shuffle = !shuffle
            (it as TextView).alpha = if (shuffle) 1f else 0.4f
        }
        findViewById<TextView>(R.id.btnRepeat).setOnClickListener {
            repeatMode = (repeatMode + 1) % 3
            val tv = it as TextView
            tv.alpha = when (repeatMode) {
                0 -> 0.4f
                else -> 1f
            }
        }

        findViewById<SeekBar>(R.id.sbAudio).setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) player?.seekTo(progress)
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            }
        )

        play(index)
        findViewById<TextView>(R.id.btnShuffle).alpha = 0.4f
        findViewById<TextView>(R.id.btnRepeat).alpha = 0.4f
    }

    override fun onResume() {
        super.onResume()
        handler.post(updater)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updater)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            player?.release()
        } catch (e: Exception) {
        }
        player = null
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
            findViewById<TextView>(R.id.btnPlay).text = "▶"
        } else {
            p.start()
            findViewById<TextView>(R.id.btnPlay).text = "⏸"
        }
    }

    private fun nextIndex(): Int {
        if (shuffle && queue.size > 1) {
            var n = index
            while (n == index) n = (0 until queue.size).random()
            return n
        }
        return (index + 1) % queue.size
    }

    private fun prevIndex(): Int {
        if (shuffle && queue.size > 1) {
            var n = index
            while (n == index) n = (0 until queue.size).random()
            return n
        }
        return if (index - 1 < 0) queue.size - 1 else index - 1
    }

    private fun play(newIndex: Int) {
        index = newIndex
        val rec = queue.getOrNull(index) ?: return
        val uri = rec.uri ?: return

        findViewById<TextView>(R.id.tvPlayerTitle).text = rec.title ?: rec.url
        findViewById<TextView>(R.id.tvPlayerMeta).text = Utils.formatSize(rec.size)

        try {
            player?.release()
        } catch (e: Exception) {
        }
        player = MediaPlayer().apply {
            setAudioStreamType(AudioManager.STREAM_MUSIC)
            setDataSource(applicationContext, Uri.parse(uri))
            setOnPreparedListener { it.start(); findViewById<TextView>(R.id.btnPlay).text = "⏸" }
            setOnCompletionListener {
                when {
                    repeatMode == 2 -> play(index)
                    index == queue.size - 1 && repeatMode == 0 -> {
                        findViewById<TextView>(R.id.btnPlay).text = "▶"
                    }
                    else -> play(nextIndex())
                }
            }
            prepare()
        }
    }
}
