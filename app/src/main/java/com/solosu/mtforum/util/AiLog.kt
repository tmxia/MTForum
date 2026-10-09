package com.solosu.mtforum.util

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.text.TextUtils

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.List
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors






object AiLog {

    private const val MAX_ENTRIES = 300
    private const val MAX_FILE_BYTES = 512 * 1024L
    private val ENTRIES: MutableList<String> = ArrayList()
    private val FMT = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

    
    private val IO: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var logFile: File? = null

    @Volatile
    private var mediaFile: File? = null

    @Volatile
    private var appContext: Context? = null

    
    @JvmStatic
    fun attach(c: Context) {
        try {
            appContext = c.applicationContext
            val dir = c.getExternalFilesDir(null)
            if (dir != null) {
                if (!dir.exists()) dir.mkdirs()
                logFile = File(dir, "ai_run.log")
            }
            
            
            
            if (MEDIA_MIRROR_ENABLED) {
                try {
                    val media = File(
                        Environment.getExternalStorageDirectory(),
                        "Android/media/" + c.packageName
                    )
                    if (!media.exists()) media.mkdirs()
                    if (media.exists()) mediaFile = File(media, "ai_run.log")
                } catch (ignore: Throwable) {
                    
                }
            }
            
            try {
                clearMirror(c.contentResolver)
            } catch (ignore: Throwable) {
            }
        } catch (ignore: Throwable) {
            
        }
    }

    
    private fun clearMirror(cr: ContentResolver) {
        try {
            val selection = MediaStore.Downloads.DISPLAY_NAME + "=?"
            cr.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, selection,
                arrayOf(MIRROR_NAME)
            )
        } catch (ignore: Throwable) {
        }
    }

    
    @JvmStatic
    fun filePath(): String {
        val f = logFile
        return if (f == null) "" else f.absolutePath
    }

    @JvmStatic
    fun i(tag: String?, msg: String?) {
        add(tag, msg)
    }

    @JvmStatic
    fun e(tag: String?, msg: String?) {
        add(tag, msg)
    }

    private fun add(tag: String?, msg: String?) {
        val line = FMT.format(Date()) + " [" + (if (tag == null) "-" else tag) + "] " +
                (if (msg == null) "" else msg)
        synchronized(ENTRIES) {
            ENTRIES.add(line)
            while (ENTRIES.size > MAX_ENTRIES) ENTRIES.removeAt(0)
        }
        val f = logFile
        val mf = mediaFile
        val ctx = appContext
        try {
            IO.execute {
                if (f != null) writeLine(f, line)
                if (mf != null) writeLine(mf, line)
                if (ctx != null && MIRROR_ENABLED) writeToPublicDownload(ctx, line)
            }
        } catch (ignore: Throwable) {
            
        }
    }

    


    private const val MIRROR_ENABLED = false

    



    private const val MEDIA_MIRROR_ENABLED = false

    
    private const val MIRROR_NAME = "mtforum_ai_run.log"
    private const val MIRROR_DIR = "MtForumLog"

    




    private fun writeToPublicDownload(ctx: Context, line: String) {
        try {
            val cr = ctx.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val id = findMirrorId(cr, collection)
            val fileUri: Uri
            if (id < 0) {
                val cv = ContentValues()
                cv.put(MediaStore.Downloads.DISPLAY_NAME, MIRROR_NAME)
                cv.put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + MIRROR_DIR
                )
                cv.put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                val uri = cr.insert(collection, cv)
                if (uri == null) return
                fileUri = uri
            } else {
                fileUri = ContentUris.withAppendedId(collection, id)
            }
            
            
            var all = readAll(cr, fileUri) + line + "\n"
            
            val lines = all.split("\n".toRegex(), 0).toTypedArray()
            if (lines.size > 400) {
                val sb = StringBuilder()
                for (i in lines.size - 300 until lines.size) {
                    sb.append(lines[i]).append('\n')
                }
                all = sb.toString()
            }
            var os: OutputStream? = null
            try {
                os = cr.openOutputStream(fileUri, "wt")
            } catch (t: Throwable) {
                
            }
            if (os == null) {
                try {
                    os = cr.openOutputStream(fileUri, "w")
                } catch (t: Throwable) {
                    return
                }
            }
            val out = os ?: return
            out.write(all.toByteArray(charset("UTF-8")))
            out.flush()
            out.close()
        } catch (ignore: Throwable) {
            
        }
    }

    
    private fun readAll(cr: ContentResolver, uri: Uri): String {
        var `is`: java.io.InputStream? = null
        try {
            `is` = cr.openInputStream(uri)
            if (`is` == null) return ""
            val bos = java.io.ByteArrayOutputStream()
            val buf = ByteArray(4096)
            var n: Int
            n = `is`.read(buf)
            while (n > 0) {
                bos.write(buf, 0, n)
                n = `is`.read(buf)
            }
            return String(bos.toByteArray(), charset("UTF-8"))
        } catch (t: Throwable) {
            return ""
        } finally {
            if (`is` != null) {
                try {
                    `is`.close()
                } catch (ignore: Throwable) {
                }
            }
        }
    }

    
    private fun findMirrorId(cr: ContentResolver, collection: Uri): Long {
        var c: Cursor? = null
        try {
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = MediaStore.Downloads.DISPLAY_NAME + "=?" +
                    " AND " + MediaStore.Downloads.RELATIVE_PATH + " LIKE ?"
            val path = Environment.DIRECTORY_DOWNLOADS + "/" + MIRROR_DIR + "%"
            c = cr.query(
                collection, projection, selection,
                arrayOf(MIRROR_NAME, path), null
            )
            if (c != null && c.moveToFirst()) {
                return c.getLong(0)
            }
        } catch (ignore: Throwable) {
        } finally {
            if (c != null) {
                try {
                    c.close()
                } catch (ignore: Throwable) {
                }
            }
        }
        return -1
    }

    private fun writeLine(f: File, line: String) {
        var fos: FileOutputStream? = null
        try {
            if (f.length() > MAX_FILE_BYTES) {
                
                if (!f.delete()) {
                    
                    return
                }
            }
            fos = FileOutputStream(f, true)
            fos.write((line + "\n").toByteArray(charset("UTF-8")))
            fos.flush()
        } catch (ignore: Throwable) {
            
        } finally {
            if (fos != null) {
                try {
                    fos.close()
                } catch (ignore: Throwable) {
                }
            }
        }
    }

    
    @JvmStatic
    fun dump(): String {
        val sb = StringBuilder()
        synchronized(ENTRIES) {
            if (ENTRIES.isEmpty()) return "暂无日志"
            for (i in ENTRIES.size - 1 downTo 0) {
                sb.append(ENTRIES[i]).append('\n')
            }
        }
        return sb.toString()
    }

    @JvmStatic
    fun size(): Int {
        synchronized(ENTRIES) {
            return ENTRIES.size
        }
    }

    @JvmStatic
    fun clear() {
        synchronized(ENTRIES) {
            ENTRIES.clear()
        }
    }

    @JvmStatic
    fun isEmpty(): Boolean {
        synchronized(ENTRIES) {
            return ENTRIES.isEmpty()
        }
    }

    
    @JvmStatic
    fun clip(s: String?, max: Int): String {
        if (TextUtils.isEmpty(s)) return ""
        val t = s!!.replace("\\s+".toRegex(), " ").trim()
        return if (t.length > max) t.substring(0, max) + "…" else t
    }
}
