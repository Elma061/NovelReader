package com.reader.app.novel.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.app.novel.data.BookRepository
import com.reader.app.novel.data.listBookFiles
import com.reader.app.novel.data.listEpubFiles
import com.reader.app.novel.data.parseEpubCover
import com.reader.app.novel.model.Book
import com.reader.app.novel.model.FileKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 书架：把书渲染成等距投影的 3D 立体书（顶部白色书页盖 + 书脊面 + 斜切封面面）。
 * 书按 pitch 紧紧排开，沿弧形轨道横向滑动；居中的书「抽出 + 抬升」（提取波）。
 * 点击把封面与起始位置交给导航层做共享过渡；长按重命名。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookShelfScreen(onOpenBook: (Book, ImageBitmap?, Rect) -> Unit = { _, _, _ -> }) {
    val context = LocalContext.current
    val repository = remember { BookRepository(context) }

    // 书架数据：首次组合时从本地恢复，后续增删改都写回仓库
    val books = remember {
        mutableStateListOf<Book>().apply { addAll(repository.loadBooks()) }
    }

    // 重命名弹窗状态
    var editingIndex by remember { mutableStateOf(-1) }
    var editTitle by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            // 在主线程先快照已添加的 uri，再把耗时的目录扫描放到 IO 线程，避免阻塞主线程造成 ANR。
            val existing = books.mapTo(mutableSetOf()) { it.uri }
            val uriString = uri.toString()
            scope.launch {
                val scan = withContext(Dispatchers.IO) {
                    runCatching {
                        listEpubFiles(context, uriString).mapNotNull { epub ->
                            if (epub.uri in existing) null
                            else {
                                existing.add(epub.uri)
                                Book(title = epub.name.substringBeforeLast('.'), uri = epub.uri)
                            }
                        }
                    }
                }
                val newBooks = scan.getOrElse {
                    Toast.makeText(context, "添加书籍失败：${it.message}", Toast.LENGTH_SHORT).show()
                    emptyList()
                }
                if (newBooks.isNotEmpty()) {
                    books.addAll(newBooks)
                    repository.saveBooks(books.toList())
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("小说阅读器") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { pickFolderLauncher.launch(null) }) {
                Text("添加书籍")
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            if (books.isEmpty()) {
                EmptyShelf(Modifier.fillMaxSize())
            } else {
                BookCarousel(
                    books = books,
                    onOpenBook = onOpenBook,
                    onRename = { index ->
                        editingIndex = index
                        editTitle = books[index].title
                    }
                )
            }
        }
    }

    // 重命名弹窗
    if (editingIndex >= 0 && editingIndex < books.size) {
        AlertDialog(
            onDismissRequest = { editingIndex = -1 },
            title = { Text("重命名书籍") },
            text = {
                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    singleLine = true,
                    label = { Text("书名") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newTitle = editTitle.trim()
                        if (newTitle.isNotEmpty()) {
                            books[editingIndex] = books[editingIndex].copy(title = newTitle)
                            repository.saveBooks(books.toList())
                        }
                        editingIndex = -1
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editingIndex = -1 }) { Text("取消") }
            }
        )
    }
}

/** 等距投影书架：单块 Canvas 绘制所有书体，横向滚动驱动「提取波」。 */
@Composable
private fun BookCarousel(
    books: List<Book>,
    onOpenBook: (Book, ImageBitmap?, Rect) -> Unit,
    onRename: (Int) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    // 预载所有封面
    val covers by produceState<Map<String, ImageBitmap?>>(emptyMap(), books.map { it.uri }) {
        value = runCatching {
            withContext(Dispatchers.IO) {
                books.associate { it.uri to loadCover(context, it) }
            }
        }.getOrElse { emptyMap() }
    }

    val currentCovers by rememberUpdatedState(covers)
    val currentBooks by rememberUpdatedState(books)
    val currentOnOpen by rememberUpdatedState(onOpenBook)
    val currentOnRename by rememberUpdatedState(onRename)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportW = with(density) { maxWidth.toPx() }
        val m = remember(viewportW, density.density) { ShelfMetrics(viewportW, density.density) }
        val count = books.size
        val contentWidth = viewportW + (count - 1).coerceAtLeast(0) * m.pitch
        val contentWidthDp = with(density) { contentWidth.toDp() }
        val rowHeightDp = with(density) { m.rowHeight.toDp() }

        // 用 books.toList() 作为 key：books 是原地增删的 SnapshotStateList，引用不变，
        // 若直接以 books 为 key，remember 不会在增删书时重算，导致 titleLayouts 长度与 books 不一致而越界。
        val titleLayouts = remember(books.toList(), m, density.density, density.fontScale) {
            val textSize = 10f * density.density * density.fontScale
            books.map { book ->
                buildSpineLayout(
                    text = spineTitleText(book.title),
                    maxWidthPx = m.spine.roundToInt(),
                    textSizePx = textSize
                )
            }
        }

        var shelfOrigin by remember { mutableStateOf(Offset.Zero) }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeightDp)
                    .horizontalScroll(scrollState)
                    .onGloballyPositioned { shelfOrigin = it.boundsInRoot().topLeft }
            ) {
                Canvas(
                    Modifier
                        .width(contentWidthDp)
                        .fillMaxHeight()
                        .pointerInput(m, count) {
                            detectTapGestures(
                                onTap = { pos ->
                                    val idx = hitTestBook(
                                        pos.x, pos.y, scrollState.value.toFloat(), m, count
                                    )
                                    if (idx != null) {
                                        val book = currentBooks[idx]
                                        val cover = currentCovers[book.uri]
                                        val bounds = coverBounds(
                                            idx, scrollState.value.toFloat(), m, shelfOrigin
                                        )
                                        currentOnOpen(book, cover, bounds)
                                    }
                                },
                                onLongPress = { pos ->
                                    val idx = hitTestBook(
                                        pos.x, pos.y, scrollState.value.toFloat(), m, count
                                    )
                                    if (idx != null) currentOnRename(idx)
                                }
                            )
                        }
                ) {
                    val scroll = scrollState.value.toFloat()
                    val focus = scroll / m.pitch
                    val halfSpan = (m.viewportW / 2f + m.footprint) / m.pitch + 2f
                    val first = (focus - halfSpan).toInt().coerceAtLeast(0)
                    val last = (focus + halfSpan).toInt().coerceAtMost(count - 1)

                    // 先画所有地面阴影（阴影都在书体之后）
                    for (i in first..last) {
                        val lane = i - focus
                        val w = m.weight(abs(lane))
                        val o = m.origin(w)
                        drawFloorShadow(m.startX + i * m.pitch + o.x, m.railY(lane) + o.y, m)
                    }
                    // 再按 index 递增绘制书体（固定的遮挡顺序）
                    for (i in first..last) {
                        val lane = i - focus
                        val w = m.weight(abs(lane))
                        val o = m.origin(w)
                        drawVolume(
                            cover = covers[books[i].uri],
                            titleLayout = titleLayouts.getOrNull(i),
                            sx = m.startX + i * m.pitch + o.x,
                            sy = m.railY(lane) + o.y,
                            m = m
                        )
                    }
                }
            }
        }
    }
}

/**
 * 书架几何（完全沿用 Wenku8_Flutter 的 archive_shelf_geometry.dart 公式）。
 * 单位已按 density 换算成物理像素，直接用于 Canvas。
 */
private class ShelfMetrics(widthPx: Float, val density: Float) {
    val viewportW = widthPx
    val depthX = 0.66f
    val depthY = -0.30f
    val spine = (widthPx * 0.043f).coerceIn(14f * density, 18f * density)
    val pitch = spine + 4f * density
    val cover = (widthPx * 0.32f).coerceIn(100f * density, 128f * density)
    val height = (cover - 20f * density) * 166f / 118f + 54f * density
    val footprint = spine + cover * depthX
    val pullDistance = cover * 0.84f
    val baseY = -cover * depthY + height * 0.60f + 12f * density
    val rowHeight = baseY + height + 60f * density
    val startX = widthPx / 2f - footprint / 2f + pullDistance * depthX

    /** 弧形轨道的纵向偏移（距离以“书槽”为单位）。 */
    fun railY(distance: Float): Float {
        val d = distance.coerceIn(-7f, 7f)
        return d * 2.5f * density + d * d * 0.45f * density
    }

    /** 提取波权重：cos 衰减，半径 1.48 本书。 */
    fun weight(distance: Float): Float {
        val radius = 1.48f
        if (distance >= radius) return 0f
        val v = cos(distance / radius * PI.toFloat() / 2f)
        return v * v
    }

    /** 书体左上角（书脊前上角）相对槽位的「抽出 + 抬升」偏移。 */
    fun origin(weight: Float): Offset {
        val pull = pullDistance * weight * (1.24f - 0.24f * weight)
        val lift = weight * weight * (3f - 2f * weight)
        return Offset(-pull * depthX, baseY - pull * depthY - height * 0.60f * lift)
    }
}

/** 地面阴影：书体正下方一块向后的平行四边形足迹。 */
private fun DrawScope.drawFloorShadow(sx: Float, sy: Float, m: ShelfMetrics) {
    val base = Offset(sx, sy + m.height)
    val front = Offset(m.spine, 1.4f * m.density)
    val depth = Offset(m.cover * m.depthX, m.cover * m.depthY)
    val tail = Offset(-10f * m.density, 12f * m.density)
    val path = Path().apply {
        moveTo(base.x, base.y)
        lineTo(base.x + front.x, base.y + front.y)
        lineTo(base.x + front.x + depth.x, base.y + front.y + depth.y)
        lineTo(base.x + depth.x, base.y + depth.y)
        lineTo(base.x + depth.x + tail.x, base.y + depth.y + tail.y)
        lineTo(base.x + tail.x, base.y + tail.y)
        close()
    }
    val brush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF8A806B).copy(alpha = 0.14f),
            Color(0xFF8A806B).copy(alpha = 0f)
        ),
        start = base,
        end = Offset(base.x + depth.x + tail.x, base.y + depth.y + tail.y)
    )
    drawPath(path, brush)
}

/** 一本书的完整立体：顶部白色书页盖 + 书脊面 + 斜切封面面。 */
private fun DrawScope.drawVolume(
    cover: ImageBitmap?,
    titleLayout: StaticLayout?,
    sx: Float,
    sy: Float,
    m: ShelfMetrics
) {
    val slope = 0.10f

    // 顶部书页盖（平行四边形，白色）
    val topPath = Path().apply {
        moveTo(sx, sy)
        lineTo(sx + m.spine, sy + m.spine * slope)
        lineTo(sx + m.spine + m.cover * m.depthX, sy + m.spine * slope + m.cover * m.depthY)
        lineTo(sx + m.cover * m.depthX, sy + m.cover * m.depthY)
        close()
    }
    val topBrush = Brush.linearGradient(
        colors = listOf(Color(0xFFFCFBF8), Color(0xFFE9E4D8)),
        start = Offset(sx, sy),
        end = Offset(sx + m.cover * m.depthX, sy + m.cover * m.depthY)
    )
    drawPath(topPath, topBrush)
    drawPath(topPath, Color(0xFF9A927F).copy(alpha = 0.24f), style = Stroke(width = 0.8f))

    // 书脊面（前左）
    val spinePath = Path().apply {
        moveTo(sx, sy)
        lineTo(sx + m.spine, sy + m.spine * slope)
        lineTo(sx + m.spine, sy + m.spine * slope + m.height)
        lineTo(sx, sy + m.height)
        close()
    }
    val spineBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFD9D5CB),
            Color.White,
            Color(0xFFF5F3EE),
            Color(0xFFD3CFC5)
        ),
        start = Offset(sx, sy),
        end = Offset(sx + m.spine, sy + m.spine * slope)
    )
    drawPath(spinePath, spineBrush)

    // 书脊书名（CJK 逐字竖排，用原生 StaticLayout 绘制，保证字形可靠渲染）
    titleLayout?.let { layout ->
        val textH = layout.height.toFloat()
        val native = drawContext.canvas.nativeCanvas
        native.save()
        native.translate(sx, sy + (m.height - textH) / 2f)
        layout.draw(native)
        native.restore()
    }

    // 封面面（前右，斜切投影）
    val coverX = sx + m.spine
    val coverY = sy + m.spine * slope
    val img = cover
    if (img != null) {
        val (srcOffset, srcSize) = cropSrc(img, m.cover, m.height)
        val shear = Matrix().apply {
            reset()
            values[0] = m.depthX
            values[1] = m.depthY
        }
        val paint = Paint().apply { filterQuality = FilterQuality.Medium }
        drawContext.canvas.save()
        drawContext.canvas.translate(coverX, coverY)
        drawContext.canvas.concat(shear)
        drawContext.canvas.drawImageRect(
            image = img,
            srcOffset = srcOffset,
            srcSize = srcSize,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(m.cover.roundToInt(), m.height.roundToInt()),
            paint = paint
        )
        drawContext.canvas.restore()
    } else {
        val coverPath = Path().apply {
            moveTo(coverX, coverY)
            lineTo(coverX + m.cover * m.depthX, coverY + m.cover * m.depthY)
            lineTo(coverX + m.cover * m.depthX, coverY + m.cover * m.depthY + m.height)
            lineTo(coverX, coverY + m.height)
            close()
        }
        drawPath(coverPath, Color(0xFFF2F0E9))
    }
}

/** 把源位图按目标宽高比居中裁切，返回源矩形。 */
private fun cropSrc(bmp: ImageBitmap, dstW: Float, dstH: Float): Pair<IntOffset, IntSize> {
    val dstAspect = dstW / dstH
    val srcAspect = bmp.width.toFloat() / bmp.height.toFloat()
    return if (srcAspect > dstAspect) {
        val cropW = (bmp.height * dstAspect).toInt().coerceAtLeast(1)
        val x = (bmp.width - cropW) / 2
        Pair(IntOffset(x, 0), IntSize(cropW, bmp.height))
    } else {
        val cropH = (bmp.width / dstAspect).toInt().coerceAtLeast(1)
        val y = (bmp.height - cropH) / 2
        Pair(IntOffset(0, y), IntSize(bmp.width, cropH))
    }
}

/** 命中检测：从后往前（上层优先）找命中的书。 */
private fun hitTestBook(x: Float, y: Float, scroll: Float, m: ShelfMetrics, count: Int): Int? {
    val focus = scroll / m.pitch
    val first = (focus - 4f).toInt().coerceAtLeast(0)
    val last = (focus + 4f).toInt().coerceAtMost(count - 1)
    for (i in last downTo first) {
        val lane = i - focus
        val w = m.weight(abs(lane))
        val o = m.origin(w)
        val sx = m.startX + i * m.pitch + o.x
        val sy = m.railY(lane) + o.y
        if (hitsVolume(x, y, sx, sy, m)) return i
    }
    return null
}

private fun hitsVolume(px: Float, py: Float, sx: Float, sy: Float, m: ShelfMetrics): Boolean {
    // 封面面（斜切平行四边形，用逆变换还原到局部坐标）
    val cx = sx + m.spine
    val cy = sy + m.spine * 0.10f
    val lx = (px - cx) / m.depthX
    val ly = (py - cy) - m.depthY * lx
    if (lx in 0f..m.cover && ly in 0f..m.height) return true

    // 书脊面
    val slx = px - sx
    val sly = (py - sy) - 0.10f * slx
    if (slx in 0f..m.spine && sly in 0f..m.height) return true

    // 顶部书页盖
    return pointInQuad(
        px, py,
        listOf(
            Offset(sx, sy),
            Offset(sx + m.spine, sy + m.spine * 0.10f),
            Offset(sx + m.spine + m.cover * m.depthX, sy + m.spine * 0.10f + m.cover * m.depthY),
            Offset(sx + m.cover * m.depthX, sy + m.cover * m.depthY)
        )
    )
}

private fun pointInQuad(px: Float, py: Float, p: List<Offset>): Boolean {
    if (p.size != 4) return false
    var sign = 0
    for (i in 0 until 4) {
        val a = p[i]
        val b = p[(i + 1) % 4]
        val cross = (b.x - a.x) * (py - a.y) - (b.y - a.y) * (px - a.x)
        if (cross == 0f) continue
        val s = if (cross > 0) 1 else -1
        if (sign == 0) sign = s else if (sign != s) return false
    }
    return true
}

/** 封面面（斜切平行四边形）在根坐标系下的外接矩形，供共享过渡使用。 */
private fun coverBounds(i: Int, scroll: Float, m: ShelfMetrics, shelfOrigin: Offset): Rect {
    val focus = scroll / m.pitch
    val lane = i - focus
    val w = m.weight(abs(lane))
    val o = m.origin(w)
    val sx = m.startX + i * m.pitch + o.x
    val sy = m.railY(lane) + o.y
    val x0 = sx + m.spine
    val y0 = sy + m.spine * 0.10f
    val left = x0 - scroll + shelfOrigin.x
    val right = x0 + m.cover * m.depthX - scroll + shelfOrigin.x
    val top = y0 + m.cover * m.depthY + shelfOrigin.y
    val bottom = y0 + m.height + shelfOrigin.y
    return Rect(left, top, right, bottom)
}

/** 书脊标题：CJK 逐字成行、拉丁/数字词整词成行（最多 7 行，超出加省略号）。 */
private fun spineTitleText(title: String): String {
    val runs = spineRuns(title)
    val body = runs.take(7).joinToString("\n") { run ->
        if (run.length > 12) run.take(11) + "…" else run
    }
    return if (runs.size > 7) "$body\n…" else body
}

private fun spineRuns(title: String): List<String> {
    val runs = mutableListOf<String>()
    var latin = StringBuilder()
    fun flush() {
        if (latin.isNotBlank()) runs.add(latin.toString().trim())
        latin = StringBuilder()
    }
    for (ch in title) {
        if (ch.code in 0x20..0x7E) {
            latin.append(ch)
        } else {
            flush()
            runs.add(ch.toString())
        }
    }
    flush()
    return runs
}

/** 构建书脊标题的原生竖排布局（CJK 逐字、拉丁整词成行），返回可绘制对象。 */
private fun buildSpineLayout(text: String, maxWidthPx: Int, textSizePx: Float): StaticLayout? {
    if (text.isEmpty() || maxWidthPx <= 0) return null
    val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = SpineTitleColor.toArgb()
        this.textSize = textSizePx
    }
    return StaticLayout.Builder
        .obtain(text, 0, text.length, paint, maxWidthPx)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setMaxLines(8)
        .build()
}

/** 空书架提示。 */
@Composable
private fun EmptyShelf(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📚", fontSize = 56.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "书架还是空的\n点击右下角「添加书籍」选择一个小说文件夹",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 读取书的封面（取第一个 epub，无 epub 则取第一个文件），解码为位图。 */
private fun loadCover(context: Context, book: Book): ImageBitmap? {
    val files = listBookFiles(context, book)
    val epub = files.firstOrNull { it.kind == FileKind.EPUB } ?: files.firstOrNull() ?: return null
    val bytes = parseEpubCover(context, epub.uri) ?: return null
    return decodeCover(bytes)
}

/** 解码封面并按需降采样，避免大图占用过多内存。 */
private fun decodeCover(bytes: ByteArray, maxDim: Int = 720): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
}.getOrNull()

private val SpineTitleColor = Color(0xFF3F3B33)
