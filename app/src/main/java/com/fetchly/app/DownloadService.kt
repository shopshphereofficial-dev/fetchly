package com.fetchly.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.Collections

class DownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val FOREGROUND_ID = 1
        private const val MAX_PARALLEL = 2

        const val ACTION_START = "com.fetchly.app.START"
        const val ACTION_CANCEL = "com.fetchly.app.CANCEL"
        const val ACTION_RETRY = "com.fetchly.app.RETRY"

        fun start(context: Context, url: String, mode: String, height: Int) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(ACTION_START)
                .putExtra("url", url)
                .putExtra("mode", mode)
                .putExtra("height", height)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context, id: Long) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra("id", id)
            ContextCompat.startForegroundService(context, intent)
        }

        fun retry(context: Context, id: Long) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(ACTION_RETRY)
                .putExtra("id", id)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val runningIds = Collections.synchronizedSet(HashSet<Long>())
    private val SPEED_REGEX = Regex("at\\s+([\\d.]+\\s*[KMGT]?i?B/s)")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // must always enter foreground promptly when started via startForegroundService
        startForeground(FOREGROUND_ID, buildForegroundNotification())

        when (intent?.action) {
            ACTION_CANCEL -> {
                val id = intent.getLongExtra("id", -1L)
                if (id > 0) {
                    // kills the yt-dlp process for this download
                    YoutubeDL.getInstance().destroyProcessById("fetchly_$id")
                }
            }
            ACTION_RETRY -> {
                val id = intent.getLongExtra("id", -1L)
                if (id > 0) {
                    val db = Db.get(this)
                    val cv = ContentValues().apply { put("status", Db.STATUS_QUEUED) }
                    db.updateDownload(id, cv)
                    pumpQueue()
                }
            }
            ACTION_START -> {
                val url = intent.getStringExtra("url") ?: return START_NOT_STICKY
                val mode = intent.getStringExtra("mode") ?: "video"
                val height = intent.getIntExtra("height", 0)

                if (Prefs.getWifiOnly(this) && isMetered()) {
                    val id = Db.get(this).insertDownload(url, url, mode, height)
                    val cv = ContentValues().apply {
                        put("status", Db.STATUS_FAILED)
                        put("title", getString(R.string.wifi_wait))
                    }
                    Db.get(this).updateDownload(id, cv)
                } else {
                    Db.get(this).insertDownload(url, url, mode, height)
                    pumpQueue()
                }
            }
        }

        maybeStop()
        return START_NOT_STICKY
    }

    private fun isMetered(): Boolean {
        return try {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
        } catch (e: Exception) {
            false
        }
    }

    @Synchronized
    private fun pumpQueue() {
        val db = Db.get(this)
        while (runningIds.size < MAX_PARALLEL) {
            val nextId = db.nextQueued() ?: break
            if (!runningIds.add(nextId)) break
            startWorker(nextId)
        }
    }

    private fun startWorker(id: Long) {
        Thread {
            try {
                doDownload(id)
            } catch (e: Exception) {
                val cv = ContentValues().apply { put("status", Db.STATUS_FAILED) }
                try {
                    Db.get(this).updateDownload(id, cv)
                } catch (e2: Exception) {
                }
            } finally {
                runningIds.remove(id)
                pumpQueue()
                maybeStop()
            }
        }.start()
    }

    private fun doDownload(id: Long) {
        val db = Db.get(this)
        val rec = db.download(id) ?: return
        val url = rec.url

        db.updateDownload(id, ContentValues().apply { put("status", Db.STATUS_PREPARING) })

        val title = try {
            val infoRequest = YoutubeDLRequest(url)
            val cookies = CookieStore.write(this, url)
            if (cookies != null) infoRequest.addOption("--cookies", cookies.absolutePath)
            infoRequest.addOption("--no-playlist")
            infoRequest.addOption("--no-warnings")
            YoutubeDL.getInstance().getInfo(infoRequest).title ?: rec.title ?: url
        } catch (e: Exception) {
            rec.title ?: url
        }
        db.updateDownload(id, ContentValues().apply { put("title", title) })
        notifyProgress(id, title, 0, "")

        // keep the same work folder on retry so --continue can resume
        val workDir = if (!rec.path.isNullOrBlank() && File(rec.path).exists()) {
            File(rec.path)
        } else {
            File(File(getExternalFilesDir(null), "work"), "${id}_${System.currentTimeMillis()}").apply { mkdirs() }
        }
        db.updateDownload(id, ContentValues().apply { put("path", workDir.absolutePath) })

        val cookies = CookieStore.write(this, url)
        val request = YoutubeDLRequest(url).apply {
            when (rec.mode) {
                "mp3" -> {
                    addOption("-f", "ba/b")
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "320K")
                }
                "audio" -> {
                    addOption("-f", "ba/b")
                }
                else -> {
                    val h = if (rec.height > 0) rec.height else 2160
                    addOption("-f", "bv*[height<=$h]+ba/b[height<=$h]")
                    addOption("--merge-output-format", "mp4")
                }
            }
            addOption("-o", workDir.absolutePath + "/%(title)s.%(ext)s")
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--no-warnings")
            addOption("--no-update")
            addOption("--continue")
            addOption("--downloader", "libaria2c.so")
            if (cookies != null) addOption("--cookies", cookies.absolutePath)
        }

        db.updateDownload(id, ContentValues().apply { put("status", Db.STATUS_DOWNLOADING) })

        try {
            YoutubeDL.getInstance().execute(request, "fetchly_$id") { progress, _, line ->
                val speed = parseSpeed(line)
                try {
                    db.updateDownload(
                        id,
                        ContentValues().apply {
                            put("progress", progress.toInt())
                            put("speed", speed)
                        }
                    )
                } catch (e: Exception) {
                }
                notifyProgress(id, title, progress.toInt(), speed)
            }
        } catch (e: Exception) {
            val cancelled = e is YoutubeDL.CanceledException
            db.updateDownload(
                id,
                ContentValues().apply {
                    put("status", if (cancelled) Db.STATUS_CANCELLED else Db.STATUS_FAILED)
                }
            )
            notifyDone(id, title, false, if (cancelled) "" else (e.message ?: ""))
            return
        }

        val file = workDir.listFiles()
            ?.firstOrNull { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            ?: throw Exception("downloaded file not found")
        val mime = mimeFor(file)
        val size = file.length()
        val uri = saveToDownloads(file, mime)

        db.updateDownload(
            id,
            ContentValues().apply {
                put("status", Db.STATUS_COMPLETED)
                put("uri", uri.toString())
                put("mime", mime)
                put("size", size)
                put("progress", 100)
                put("speed", "")
            }
        )
        workDir.deleteRecursively()
        notifyDone(id, title, true, "")
    }

    private fun parseSpeed(line: String?): String {
        if (line == null) return ""
        val m = SPEED_REGEX.find(line)
        return m?.groupValues?.get(1) ?: ""
    }

    private fun maybeStop() {
        val empty = synchronized(runningIds) { runningIds.isEmpty() }
        if (empty) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildForegroundNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_preparing))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun notifyProgress(id: Long, title: String, progress: Int, speed: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val text = if (speed.isBlank()) "$progress%" else "$progress% • $speed"
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, progress <= 0)
            .addAction(0, getString(R.string.notif_cancel), cancelPendingIntent(id))
            .build()
        nm.notify(notifId(id), n)
    }

    private fun notifyDone(id: Long, title: String, ok: Boolean, msg: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setAutoCancel(true)
        if (ok) {
            builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
        } else {
            builder.setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(msg)
                .addAction(0, getString(R.string.btn_retry), retryPendingIntent(id))
        }
        nm.notify(notifId(id), builder.build())
    }

    private fun cancelPendingIntent(id: Long): PendingIntent {
        val intent = Intent(this, DownloadService::class.java)
            .setAction(ACTION_CANCEL)
            .putExtra("id", id)
        return PendingIntent.getService(
            this, (1000 + id).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun retryPendingIntent(id: Long): PendingIntent {
        val intent = Intent(this, DownloadService::class.java)
            .setAction(ACTION_RETRY)
            .putExtra("id", id)
        return PendingIntent.getService(
            this, (2000 + id).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notifId(id: Long): Int = (5000 + id).toInt()

    private fun mimeFor(file: File): String {
        return when (file.extension.lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "opus" -> "audio/ogg"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            else -> "application/octet-stream"
        }
    }

    private fun saveToDownloads(file: File, mime: String): Uri {
        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw Exception("could not save to Downloads")
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }
}
