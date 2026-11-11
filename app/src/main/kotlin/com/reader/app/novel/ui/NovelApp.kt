package com.reader.app.novel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.reader.app.novel.model.Book
import com.reader.app.novel.model.BookFile
import com.reader.app.novel.model.FileKind
import org.json.JSONArray
import org.json.JSONObject

/** 应用内导航目的地。 */
sealed interface Screen {
    data object Shelf : Screen
    data class BookDetail(val book: Book) : Screen
    data class Reader(val file: BookFile, val book: Book, val initialChapterIndex: Int) : Screen
}

/** 根导航宿主：用返回栈在书架 / 书籍详情 / 阅读器之间切换。 */
@Composable
fun NovelApp() {
    // 用 rememberSaveable 保存返回栈：旋转屏幕等配置变更重建后能恢复到当前页面
    val backStack = rememberSaveable(saver = ScreenStackSaver) {
        mutableStateListOf<Screen>(Screen.Shelf)
    }
    val current = backStack.last()

    BackHandler(enabled = backStack.size > 1) {
        backStack.removeAt(backStack.lastIndex)
    }

    when (val screen = current) {
        is Screen.Shelf -> BookShelfScreen(
            onOpenBook = { backStack.add(Screen.BookDetail(it)) }
        )
        is Screen.BookDetail -> BookDetailScreen(
            book = screen.book,
            onBack = { backStack.removeAt(backStack.lastIndex) },
            onStartReading = { file, index ->
                backStack.add(Screen.Reader(file, screen.book, index))
            }
        )
        is Screen.Reader -> ReaderScreen(
            file = screen.file,
            book = screen.book,
            initialChapterIndex = screen.initialChapterIndex,
            onBack = { backStack.removeAt(backStack.lastIndex) }
        )
    }
}

// ---------------------------------------------------------------------------
// 返回栈的保存 / 恢复（序列化成 JSON 字符串，跨配置变更存活）
// ---------------------------------------------------------------------------

private val ScreenStackSaver = Saver<SnapshotStateList<Screen>, String>(
    save = { stack -> encodeStack(stack) },
    restore = { encoded ->
        mutableStateListOf<Screen>().apply { addAll(decodeStack(encoded)) }
    }
)

private fun encodeStack(stack: List<Screen>): String {
    val array = JSONArray()
    for (screen in stack) {
        array.put(
            when (screen) {
                is Screen.Shelf -> JSONObject().put("type", "shelf")
                is Screen.BookDetail -> JSONObject()
                    .put("type", "detail")
                    .put("book", bookToJson(screen.book))
                is Screen.Reader -> JSONObject()
                    .put("type", "reader")
                    .put("file", fileToJson(screen.file))
                    .put("book", bookToJson(screen.book))
                    .put("index", screen.initialChapterIndex)
            }
        )
    }
    return array.toString()
}

private fun decodeStack(encoded: String): List<Screen> {
    val array = runCatching { JSONArray(encoded) }.getOrNull() ?: return listOf(Screen.Shelf)
    val result = mutableListOf<Screen>()
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        when (obj.optString("type")) {
            "detail" -> {
                val book = bookFromJson(obj.optJSONObject("book")) ?: continue
                result.add(Screen.BookDetail(book))
            }
            "reader" -> {
                val file = fileFromJson(obj.optJSONObject("file")) ?: continue
                val book = bookFromJson(obj.optJSONObject("book")) ?: continue
                result.add(Screen.Reader(file, book, obj.optInt("index")))
            }
            else -> result.add(Screen.Shelf)
        }
    }
    return result.ifEmpty { listOf(Screen.Shelf) }
}

private fun bookToJson(book: Book): JSONObject = JSONObject()
    .put("title", book.title)
    .put("uri", book.uri)
    .put("author", book.author)
    .put("category", book.category)

private fun bookFromJson(obj: JSONObject?): Book? {
    obj ?: return null
    val uri = obj.optString("uri")
    if (uri.isEmpty()) return null
    return Book(
        title = obj.optString("title"),
        uri = uri,
        author = obj.optString("author"),
        category = obj.optString("category")
    )
}

private fun fileToJson(file: BookFile): JSONObject = JSONObject()
    .put("name", file.name)
    .put("uri", file.uri)
    .put("kind", file.kind.name)

private fun fileFromJson(obj: JSONObject?): BookFile? {
    obj ?: return null
    val uri = obj.optString("uri")
    if (uri.isEmpty()) return null
    val kind = runCatching { FileKind.valueOf(obj.optString("kind")) }.getOrNull() ?: return null
    return BookFile(name = obj.optString("name"), uri = uri, kind = kind)
}
