package com.reader.app.novel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.reader.app.novel.data.BookRepository
import com.reader.app.novel.model.Book
import com.reader.app.novel.model.BookFile
import com.reader.app.novel.model.FileKind
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/** 应用内导航目的地。 */
sealed interface Screen {
    data object Shelf : Screen
    data class BookDetail(val book: Book) : Screen
    data class Reader(
        val file: BookFile,
        val book: Book,
        val initialChapterIndex: Int,
        val initialAnchorIndex: Int = 0,
        val initialAnchorOffset: Int = 0
    ) : Screen
}

/** 封面共享过渡的状态：起始位置（书架）与目标位置（详情页封面，由详情页上报）。 */
private data class CoverFlyState(
    val cover: ImageBitmap?,
    val title: String,
    val start: Rect,
    val target: Rect? = null
)

/** 根导航宿主：用返回栈在书架 / 书籍详情 / 阅读器之间切换。 */
@Composable
fun NovelApp() {
    // 用 rememberSaveable 保存返回栈：旋转屏幕等配置变更重建后能恢复到当前页面
    val backStack = rememberSaveable(saver = ScreenStackSaver) {
        mutableStateListOf<Screen>(Screen.Shelf)
    }
    val current = backStack.last()
    var coverFly by remember { mutableStateOf<CoverFlyState?>(null) }
    val context = LocalContext.current
    val repository = remember { BookRepository(context) }

    BackHandler(enabled = backStack.size > 1) {
        backStack.removeAt(backStack.lastIndex)
        coverFly = null
    }

    Box(Modifier.fillMaxSize()) {
        when (val screen = current) {
            is Screen.Shelf -> BookShelfScreen(
                onOpenBook = { book, cover, bounds ->
                    coverFly = CoverFlyState(cover, book.title, bounds)
                    backStack.add(Screen.BookDetail(book))
                }
            )
            is Screen.BookDetail -> BookDetailScreen(
                book = screen.book,
                onBack = {
                    backStack.removeAt(backStack.lastIndex)
                    coverFly = null
                },
                onStartReading = { file, index, anchorIndex, anchorOffset ->
                    backStack.add(Screen.Reader(file, screen.book, index, anchorIndex, anchorOffset))
                },
                onCoverBounds = { target ->
                    coverFly = coverFly?.copy(target = target)
                },
                onDelete = {
                    repository.deleteBook(screen.book)
                    backStack.removeAt(backStack.lastIndex)
                    coverFly = null
                }
            )
            is Screen.Reader -> ReaderScreen(
                file = screen.file,
                book = screen.book,
                initialChapterIndex = screen.initialChapterIndex,
                initialAnchorIndex = screen.initialAnchorIndex,
                initialAnchorOffset = screen.initialAnchorOffset,
                onBack = { backStack.removeAt(backStack.lastIndex) }
            )
        }

        // 封面共享过渡：待详情页封面位置已知后，从书架位置飞到详情页封面
        coverFly?.let { state ->
            if (state.target != null) {
                CoverFlyOverlay(state) { coverFly = null }
            }
        }
    }
}

/** 把封面从起始矩形插值到目标矩形，落到详情页封面上后淡出。 */
@Composable
private fun CoverFlyOverlay(state: CoverFlyState, onDone: () -> Unit) {
    val target = state.target ?: return
    val density = LocalDensity.current
    val rectProgress = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        rectProgress.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        alpha.animateTo(0f, tween(120))
        onDone()
    }

    val p = rectProgress.value
    val left = lerpF(state.start.left, target.left, p)
    val top = lerpF(state.start.top, target.top, p)
    val right = lerpF(state.start.right, target.right, p)
    val bottom = lerpF(state.start.bottom, target.bottom, p)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(
                    with(density) { (right - left).toDp() },
                    with(density) { (bottom - top).toDp() }
                )
                .alpha(alpha.value)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFEFE9DF))
        ) {
            val cover = state.cover
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = state.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun lerpF(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

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
                    .put("anchorIndex", screen.initialAnchorIndex)
                    .put("anchorOffset", screen.initialAnchorOffset)
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
                result.add(
                    Screen.Reader(
                        file,
                        book,
                        obj.optInt("index"),
                        obj.optInt("anchorIndex"),
                        obj.optInt("anchorOffset")
                    )
                )
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
