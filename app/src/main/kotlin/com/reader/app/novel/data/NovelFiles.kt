package com.reader.app.novel.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.reader.app.novel.model.Book
import com.reader.app.novel.model.BookFile
import com.reader.app.novel.model.FileKind

/**
 * 列出书籍文件夹内的 txt / epub 文件，按文件名排序。
 * 只保留文件本身，忽略子文件夹与不支持的格式。
 */
fun listBookFiles(context: Context, book: Book): List<BookFile> {
    val tree = DocumentFile.fromTreeUri(context, Uri.parse(book.uri)) ?: return emptyList()
    return tree.listFiles()
        .filter { it.isFile }
        .mapNotNull { doc ->
            val name = doc.name ?: return@mapNotNull null
            val kind = when {
                name.endsWith(".txt", ignoreCase = true) -> FileKind.TXT
                name.endsWith(".epub", ignoreCase = true) -> FileKind.EPUB
                else -> return@mapNotNull null
            }
            BookFile(name = name, uri = doc.uri.toString(), kind = kind)
        }
        .sortedBy { it.name.lowercase() }
}

/** 读取 txt 文件全文（UTF-8）。 */
fun readTextFile(context: Context, fileUri: String): String {
    val stream = context.contentResolver.openInputStream(Uri.parse(fileUri)) ?: return ""
    return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
