package com.reader.app.novel.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.app.novel.data.ReadingProgressRepository
import com.reader.app.novel.data.parseEpub
import com.reader.app.novel.model.Book
import com.reader.app.novel.model.BookFile
import com.reader.app.novel.model.EpubBlock
import com.reader.app.novel.model.EpubBook
import com.reader.app.novel.model.FileKind
import com.reader.app.novel.model.ReadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 阅读器：epub 完整解析并分章阅读（含插图）；txt 先占位，待后续实现。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    file: BookFile,
    book: Book,
    initialChapterIndex: Int,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←", fontSize = 22.sp) }
                }
            )
        }
    ) { innerPadding ->
        when (file.kind) {
            FileKind.EPUB -> EpubContent(file, book, initialChapterIndex, Modifier.padding(innerPadding))
            FileKind.TXT -> TxtPlaceholder(Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun EpubContent(file: BookFile, book: Book, initialChapterIndex: Int, modifier: Modifier) {
    val context = LocalContext.current
    val epub by produceState<EpubBook?>(initialValue = null, file.uri) {
        value = withContext(Dispatchers.IO) { parseEpub(context, file.uri) }
    }

    val loaded = epub
    when {
        loaded == null -> CenteredBox(modifier) { CircularProgressIndicator() }
        loaded.chapters.isEmpty() -> CenteredBox(modifier) {
            Text(
                text = "无法解析该 epub 文件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else -> EpubChapterView(loaded, file, book, initialChapterIndex, modifier)
    }
}

@Composable
private fun EpubChapterView(
    epub: EpubBook,
    file: BookFile,
    book: Book,
    initialChapterIndex: Int,
    modifier: Modifier
) {
    val context = LocalContext.current
    val progressRepo = remember(context) { ReadingProgressRepository(context) }
    var chapterIndex by remember(epub) {
        mutableStateOf(initialChapterIndex.coerceIn(0, epub.chapters.lastIndex))
    }
    val chapter = epub.chapters[chapterIndex]
    val scroll = rememberScrollState()
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(chapterIndex) {
        scroll.scrollTo(0)
        progressRepo.save(book.uri, ReadState(file.uri, chapterIndex, chapter.title))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            Box {
                TextButton(onClick = { menuExpanded = true }) { Text("目录") }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    epub.chapters.forEachIndexed { index, ch ->
                        DropdownMenuItem(
                            text = { Text(ch.title) },
                            onClick = {
                                chapterIndex = index
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
        ) {
            chapter.blocks.forEach { block ->
                when (block) {
                    is EpubBlock.Text -> Text(
                        text = block.content,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    is EpubBlock.Image -> EpubImage(
                        bytes = block.data,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { if (chapterIndex > 0) chapterIndex-- },
                enabled = chapterIndex > 0
            ) { Text("上一章") }
            Text("${chapterIndex + 1} / ${epub.chapters.size}")
            TextButton(
                onClick = { if (chapterIndex < epub.chapters.lastIndex) chapterIndex++ },
                enabled = chapterIndex < epub.chapters.lastIndex
            ) { Text("下一章") }
        }
    }
}

/** 把插图字节解码为位图，按宽度铺满、按原始比例显示；解码失败则跳过。 */
@Composable
private fun EpubImage(bytes: ByteArray, modifier: Modifier) {
    var bitmap by remember(bytes) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(bytes) { mutableStateOf(false) }

    LaunchedEffect(bytes) {
        val decoded = withContext(Dispatchers.IO) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
        if (decoded == null) failed = true else bitmap = decoded
    }

    val b = bitmap
    when {
        b != null -> Image(
            bitmap = b,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(b.width.toFloat() / b.height.toFloat())
        )
        failed -> Unit
        else -> Box(
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun TxtPlaceholder(modifier: Modifier) {
    CenteredBox(modifier) {
        Text(
            text = "TXT 阅读功能开发中，敬请期待",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CenteredBox(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        content()
    }
}
