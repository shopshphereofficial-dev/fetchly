package com.fetchly.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Db private constructor(context: Context) :
    SQLiteOpenHelper(context, "fetchly.db", null, 1) {

    companion object {
        @Volatile
        private var instance: Db? = null

        fun get(context: Context): Db =
            instance ?: synchronized(this) {
                instance ?: Db(context.applicationContext).also { instance = it }
            }

        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_PREPARING = "PREPARING"
        const val STATUS_DOWNLOADING = "DOWNLOADING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELLED = "CANCELLED"
    }

    data class DownloadRecord(
        val id: Long,
        val url: String,
        val title: String?,
        val status: String,
        val progress: Int,
        val size: Long,
        val speed: String?,
        val mime: String?,
        val uri: String?,
        val path: String?,
        val mode: String?,
        val height: Int,
        val createdAt: Long
    )

    data class Bookmark(val id: Long, val title: String, val url: String, val createdAt: Long)
    data class HistoryEntry(val id: Long, val title: String, val url: String, val createdAt: Long)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE downloads(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "url TEXT NOT NULL," +
                "title TEXT," +
                "status TEXT NOT NULL," +
                "progress INTEGER DEFAULT 0," +
                "size INTEGER DEFAULT 0," +
                "speed TEXT," +
                "mime TEXT," +
                "uri TEXT," +
                "path TEXT," +
                "mode TEXT," +
                "height INTEGER DEFAULT 0," +
                "created_at INTEGER," +
                "updated_at INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE bookmarks(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT, url TEXT UNIQUE, created_at INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE browser_history(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT, url TEXT, created_at INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE search_history(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "query TEXT, created_at INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE playback(" +
                "uri TEXT PRIMARY KEY, position INTEGER, duration INTEGER)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // database version 1 so far
    }

    @Synchronized
    fun insertDownload(url: String, title: String?, mode: String, height: Int): Long {
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("url", url)
            put("title", title)
            put("status", STATUS_QUEUED)
            put("mode", mode)
            put("height", height)
            put("created_at", now)
            put("updated_at", now)
        }
        return writableDatabase.insert("downloads", null, cv)
    }

    @Synchronized
    fun updateDownload(id: Long, values: ContentValues) {
        values.put("updated_at", System.currentTimeMillis())
        writableDatabase.update("downloads", values, "_id=?", arrayOf(id.toString()))
    }

    @Synchronized
    fun deleteDownload(id: Long) {
        writableDatabase.delete("downloads", "_id=?", arrayOf(id.toString()))
    }

    @Synchronized
    fun allDownloads(): List<DownloadRecord> {
        val out = mutableListOf<DownloadRecord>()
        val c = readableDatabase.query(
            "downloads", null, null, null, null, null, "created_at DESC"
        )
        while (c.moveToNext()) out.add(cursorToRecord(c))
        c.close()
        return out
    }

    @Synchronized
    fun download(id: Long): DownloadRecord? {
        val c = readableDatabase.query(
            "downloads", null, "_id=?", arrayOf(id.toString()), null, null, null
        )
        val rec = if (c.moveToFirst()) cursorToRecord(c) else null
        c.close()
        return rec
    }

    @Synchronized
    fun nextQueued(): Long? {
        val c = readableDatabase.query(
            "downloads", arrayOf("_id"), "status=?",
            arrayOf(STATUS_QUEUED), null, null, "created_at ASC", "1"
        )
        val id = if (c.moveToFirst()) c.getLong(0) else null
        c.close()
        return id
    }

    @Synchronized
    fun completedAudio(): List<DownloadRecord> {
        return allDownloads().filter {
            it.status == STATUS_COMPLETED && (it.mime ?: "").startsWith("audio")
        }
    }

    @Synchronized
    fun recentCompleted(limit: Int): List<DownloadRecord> {
        return allDownloads().filter { it.status == STATUS_COMPLETED }.take(limit)
    }

    @Synchronized
    fun totalCompletedSize(): Long {
        var total = 0L
        val c = readableDatabase.query(
            "downloads", arrayOf("size"), "status=?", arrayOf(STATUS_COMPLETED),
            null, null, null
        )
        while (c.moveToNext()) total += c.getLong(0)
        c.close()
        return total
    }

    private fun cursorToRecord(c: android.database.Cursor): DownloadRecord {
        return DownloadRecord(
            c.getLong(0),
            c.getString(1),
            c.getString(2),
            c.getString(3),
            c.getInt(4),
            c.getLong(5),
            c.getString(6),
            c.getString(7),
            c.getString(8),
            c.getString(9),
            c.getString(10),
            c.getInt(11),
            c.getLong(12)
        )
    }

    @Synchronized
    fun addBookmark(title: String, url: String): Boolean {
        val cv = ContentValues().apply {
            put("title", title)
            put("url", url)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insertWithOnConflict(
            "bookmarks", null, cv, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    @Synchronized
    fun bookmarks(): List<Bookmark> {
        val out = mutableListOf<Bookmark>()
        val c = readableDatabase.query(
            "bookmarks", null, null, null, null, null, "created_at DESC"
        )
        while (c.moveToNext()) {
            out.add(Bookmark(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getLong(3)))
        }
        c.close()
        return out
    }

    @Synchronized
    fun clearBookmarks() {
        writableDatabase.delete("bookmarks", null, null)
    }

    @Synchronized
    fun addBrowserHistory(title: String, url: String) {
        val cv = ContentValues().apply {
            put("title", title)
            put("url", url)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("browser_history", null, cv)
    }

    @Synchronized
    fun browserHistory(): List<HistoryEntry> {
        val out = mutableListOf<HistoryEntry>()
        val c = readableDatabase.query(
            "browser_history", null, null, null, null, null, "created_at DESC", "200"
        )
        while (c.moveToNext()) {
            out.add(HistoryEntry(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getLong(3)))
        }
        c.close()
        return out
    }

    @Synchronized
    fun clearBrowserHistory() {
        writableDatabase.delete("browser_history", null, null)
    }

    @Synchronized
    fun addSearchHistory(query: String) {
        val cv = ContentValues().apply {
            put("query", query)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("search_history", null, cv)
    }

    @Synchronized
    fun clearSearchHistory() {
        writableDatabase.delete("search_history", null, null)
    }

    @Synchronized
    fun playbackPosition(uri: String): Int {
        val c = readableDatabase.query(
            "playback", arrayOf("position"), "uri=?", arrayOf(uri), null, null, null
        )
        val pos = if (c.moveToFirst()) c.getInt(0) else 0
        c.close()
        return pos
    }

    @Synchronized
    fun savePlayback(uri: String, position: Int, duration: Int) {
        val cv = ContentValues().apply {
            put("uri", uri)
            put("position", position)
            put("duration", duration)
        }
        writableDatabase.insertWithOnConflict(
            "playback", null, cv, SQLiteDatabase.CONFLICT_REPLACE
        )
    }
}
