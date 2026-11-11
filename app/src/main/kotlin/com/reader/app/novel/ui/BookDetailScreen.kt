package com.reader.app.novel.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.app.novel.data.ReadingProgressRepository
import com.reader.app.novel.data.listBookFiles
import com.reader.app.novel.data.parseEpubDetail
import com.reader.app.novel.model.Book
import com.reader.app.novel.model.BookFile
import com.reader.app.novel.model.EpubDetail
import com.reader.app.novel.model.FileKind
import com.reader.app.novel.model.ReadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 书籍详情页：封面、章节目录、上次读到哪、开始阅读按钮。
 * 点击书架上的书后进入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    book: Book,
    onBack: () -> Unit,
    onStartReading: (BookFile, Int) -> Unit
) {
    val context = LocalContext.current
    val progressRepo = remember(context) { ReadingProgressRepository(context) }
    val files = remember(book.uri) { listBookFiles(context, book) }
    val readState = remember(book.uri) { progressRepo.load(book.uri) }

    var selectedIndex by remember(book.uri) { mutableStateOf(initialFileIndex(files, readState)) }
    val file = files.getOrNull(selectedIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Text("←", fontSize = 22.sp) } }
            )
        }
    ) { innerPadding ->
        when {
            file == null -> EmptyHint(
                text = "该文件夹内没有找到 txt 或 epub 文件",
                modifier = Modifier.padding(innerPadding)
            )
            else -> BookDetailContent(
                book = book,
                files = files,
                file = file,
                readState = readState?.takeIf { it.fileUri == file.uri },
                onSelectFile = { selectedIndex = files.indexOf(it) },
                onOpenChapter = { index -> onStartReading(file, index) },
                onStartReading = {
                    val idx = readState?.takeIf { it.fileUri == file.uri }?.chapterIndex ?: 0
                    onStartReading(file, idx)
                },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun BookDetailContent(
    book: Book,
    files: List<BookFile>,
    file: BookFile,
    readState: ReadState?,
    onSelectFile: (BookFile) -> Unit,
    onOpenChapter: (Int) -> Unit,
    onStartReading: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val detail by produceState<EpubDetail?>(initialValue = null, file.uri) {
        value = when (file.kind) {
            FileKind.EPUB -> withContext(Dispatchers.IO) { parseEpubDetail(context, file.uri) }
            FileKind.TXT -> EpubDetail(title = "", cover = null, chapterTitles = emptyList())
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { CoverHeader(book, file, detail) }

        if (files.size > 1) {
            item { FileSelector(files, file, onSelectFile) }
        }

        item { LastReadCard(readState, onStartReading) }

        item { Text("目录", style = MaterialTheme.typography.titleMedium) }

        val chapters = detail?.chapterTitles.orEmpty()
        when {
            detail == null -> item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            chapters.isEmpty() -> item {
                Text(
                    text = "暂无目录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> itemsIndexed(chapters) { index, title ->
                ChapterRow(index = index, title = title, onClick = { onOpenChapter(index) })
            }
        }
    }
}

@Composable
private fun CoverHeader(book: Book, file: BookFile, detail: EpubDetail?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CoverImage(detail?.cover, Modifier.size(width = 110.dp, height = 150.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.headlineSmall)
            val epubTitle = detail?.title
            if (!epubTitle.isNullOrBlank() && epubTitle != book.title) {
                Text(
                    text = epubTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                text = "文件：${file.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun CoverImage(bytes: ByteArray?, modifier: Modifier) {
    var bitmap by remember(bytes) { mutableStateOf<ImageBitmap?>(null) }
    var settled by remember(bytes) { mutableStateOf(false) }

    LaunchedEffect(bytes) {
        val b = bytes
        if (b == null) {
            settled = true
        } else {
            val decoded = withContext(Dispatchers.IO) {
                BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
            }
            bitmap = decoded
            settled = true
        }
    }

    val b = bitmap
    when {
        b != null -> Image(
            bitmap = b,
            contentDescription = "封面",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(MaterialTheme.shapes.medium)
        )
        settled -> PlaceholderCover(modifier)
        else -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@Composable
private fun PlaceholderCover(modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text("📖", fontSize = 40.sp)
    }
}

@Composable
private fun FileSelector(files: List<BookFile>, current: BookFile, onSelect: (BookFile) -> Unit) {
    Column {
        Text("本书文件", style = MaterialTheme.typography.titleSmall)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            items(files) { file ->
                FileChip(file, selected = file.uri == current.uri, onClick = { onSelect(file) })
            }
        }
    }
}

@Composable
private fun FileChip(file: BookFile, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LastReadCard(readState: ReadState?, onStartReading: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("上次读到", style = MaterialTheme.typography.titleSmall)
            if (readState != null) {
                Text(
                    text = "第 ${readState.chapterIndex + 1} 章 · ${readState.chapterTitle}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Text(
                    text = "尚未开始阅读",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onStartReading, modifier = Modifier.fillMaxWidth()) {
                Text(if (readState == null) "开始阅读" else "继续阅读")
            }
        }
    }
}

@Composable
private fun ChapterRow(index: Int, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EmptyHint(text: String, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 详情页初始选中的文件：上次读的文件 > 第一个 epub > 第一个文件。 */
private fun initialFileIndex(files: List<BookFile>, readState: ReadState?): Int {
    val fromProgress = readState?.fileUri?.let { uri -> files.indexOfFirst { it.uri == uri } } ?: -1
    if (fromProgress >= 0) return fromProgress
    val firstEpub = files.indexOfFirst { it.kind == FileKind.EPUB }
    if (firstEpub >= 0) return firstEpub
    return 0
}
