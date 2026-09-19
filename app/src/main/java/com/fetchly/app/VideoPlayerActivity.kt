package com.fetchly.app

import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView

class VideoPlayerActivity : BaseActivity() {

    private var currentUri: String? = null
    private var videoView: VideoView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        val uri = intent.getStringExtra("uri") ?: run { finish(); return }
        currentUri = uri
        videoView = findViewById(R.id.videoView)

        val controller = MediaController(this)
        controller.setAnchorView(videoView)
        videoView?.setMediaController(controller)
        videoView?.setVideoURI(Uri.parse(uri))

        // continue watching: jump to last saved position
        val savedPos = Db.get(this).playbackPosition(uri)
        if (savedPos > 0) {
            videoView?.seekTo(savedPos)
        }
        videoView?.start()
    }

    override fun onPause() {
        super.onPause()
        try {
            val vv = videoView ?: return
            val uri = currentUri ?: return
            if (vv.currentPosition > 0) {
                Db.get(this).savePlayback(uri, vv.currentPosition, vv.duration)
            }
        } catch (e: Exception) {
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            videoView?.stopPlayback()
        } catch (e: Exception) {
        }
    }
}
