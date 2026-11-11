package com.reader.app.novel.ui

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.reader.app.novel.data.BookRepository
import com.reader.app.novel.model.Book

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookShelfScreen(onOpenBook: (Book) -> Unit = {}) {
    val context = LocalContext.current
    val repository = remember { BookRepository(context) }

    // 书架数据：首次组合时从本地恢复，后续增删改都写回仓库
    val books = remember {
        mutableStateListOf<Book>().apply { addAll(repository.loadBooks()) }
    }

    // 重命名弹窗状态：editingIndex 指向正在编辑的书籍，-1 表示弹窗关闭
    var editingIndex by remember { mutableStateOf(-1) }
    var editTitle by remember { mutableStateOf("") }

    // 用户指定文件夹来添加书籍：系统目录选择器返回一个可持久化的 SAF Uri
    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // 持久化读取权限，保证应用重启后仍能访问该文件夹
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val title = DocumentFile.fromTreeUri(context, uri)?.name
                ?: uri.lastPathSegment
                ?: "未命名书籍"
            val book = Book(title = title, uri = uri.toString())
            books.add(book)
            repository.saveBooks(books.toList())
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("小说阅读器") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pickFolderLauncher.launch(null) }
            ) {
                Text("添加书籍")
            }
        }
    ) { innerPadding ->
        if (books.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "书架还是空的\n点击右下角「添加书籍」选择一个小说文件夹",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(books) { index, book ->
                    BookItem(
                        book = book,
                        onOpen = { onOpenBook(book) },
                        onRename = {
                            editingIndex = index
                            editTitle = book.title
                        }
                    )
                }
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
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingIndex = -1 }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun BookItem(book: Book, onOpen: () -> Unit, onRename: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = book.displayPath(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            TextButton(onClick = onRename) {
                Text("重命名")
            }
        }
    }
}

/** 把书籍绑定的 SAF Uri 转成可读的路径，例如 primary:Books/Novel -> Books/Novel。 */
private fun Book.displayPath(): String {
    return runCatching {
        val id = DocumentsContract.getTreeDocumentId(Uri.parse(uri))
        id.substringAfter(':', id)
    }.getOrElse { uri }
}
