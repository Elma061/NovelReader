package com.reader.app.novel.data

import android.content.Context
import android.content.SharedPreferences
import com.reader.app.novel.model.Book
import org.json.JSONArray
import org.json.JSONObject

/**
 * 书架数据仓库：负责书籍列表的持久化读写。
 *
 * 目前使用 SharedPreferences + JSON 存储，实现简单、无额外依赖；
 * 后续书籍规模变大时可替换为 Room 或 DataStore。
 */
class BookRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 读取已保存的书籍列表，读不到或数据损坏时返回空列表。 */
    fun loadBooks(): List<Book> {
        val raw = prefs.getString(KEY_BOOKS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        Book(
                            title = obj.getString("title"),
                            uri = obj.getString("uri"),
                            author = obj.optString("author"),
                            category = obj.optString("category")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /** 覆盖式保存整个书籍列表。 */
    fun saveBooks(books: List<Book>) {
        val array = JSONArray()
        books.forEach { book ->
            array.put(
                JSONObject()
                    .put("title", book.title)
                    .put("uri", book.uri)
                    .put("author", book.author)
                    .put("category", book.category)
            )
        }
        prefs.edit().putString(KEY_BOOKS, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "book_shelf"
        const val KEY_BOOKS = "books"
    }
}
