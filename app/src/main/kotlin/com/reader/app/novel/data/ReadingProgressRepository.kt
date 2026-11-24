package com.reader.app.novel.data

import android.content.Context
import android.content.SharedPreferences
import com.reader.app.novel.model.ReadState
import org.json.JSONObject

/**
 * 阅读进度仓库：以书籍（文件夹）Uri 为键，记录上次读到的文件与章节。
 * 用 SharedPreferences + JSON 存储，与书架仓库保持同样风格。
 */
class ReadingProgressRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 读取某本书的阅读进度，没有记录或数据损坏时返回 null。 */
    fun load(bookUri: String): ReadState? {
        val raw = prefs.getString(bookUri, null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            ReadState(
                fileUri = obj.getString("fileUri"),
                chapterIndex = obj.getInt("chapterIndex"),
                chapterTitle = obj.optString("chapterTitle"),
                anchorIndex = obj.optInt("anchorIndex", 0),
                anchorOffset = obj.optInt("anchorOffset", 0)
            )
        }.getOrNull()
    }

    /** 保存某本书的阅读进度。 */
    fun save(bookUri: String, state: ReadState) {
        val obj = JSONObject()
            .put("fileUri", state.fileUri)
            .put("chapterIndex", state.chapterIndex)
            .put("chapterTitle", state.chapterTitle)
            .put("anchorIndex", state.anchorIndex)
            .put("anchorOffset", state.anchorOffset)
        prefs.edit().putString(bookUri, obj.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "read_progress"
    }
}
