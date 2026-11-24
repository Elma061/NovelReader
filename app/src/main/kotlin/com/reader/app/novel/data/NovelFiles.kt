package com.reader.app.novel.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.reader.app.novel.model.Book
import com.reader.app.novel.model.BookFile
import com.reader.app.novel.model.FileKind

/** 一本书对应一个 epub/txt 文件，返回该文件本身。 */
fun listBookFiles(context: Context, book: Book): List<BookFile> {
    val doc = DocumentFile.fromSingleUri(context, Uri.parse(book.uri)) ?: return emptyList()
    val name = doc.name ?: return emptyList()
    val kind = when {
        name.endsWith(".txt", ignoreCase = true) -> FileKind.TXT
        name.endsWith(".epub", ignoreCase = true) -> FileKind.EPUB
        else -> return emptyList()
    }
    return listOf(BookFile(name = name, uri = doc.uri.toString(), kind = kind))
}

/** 递归扫描目录树中的所有 epub 文件（每个 epub 对应一本书），按文件名排序。 */
fun listEpubFiles(context: Context, treeUri: String): List<BookFile> {
    val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return emptyList()
    val result = mutableListOf<BookFile>()
    // 防御式递归：限制深度避免目录环/超深结构导致栈溢出；listFiles 可能返回 null 需判空
    fun scan(dir: DocumentFile, depth: Int) {
        if (depth > 64) return
        val children = dir.listFiles() ?: return
        for (doc in children) {
            when {
                doc.isDirectory -> scan(doc, depth + 1)
                else -> {
                    val name = doc.name
                    if (name != null && name.endsWith(".epub", ignoreCase = true)) {
                        result.add(BookFile(name = name, uri = doc.uri.toString(), kind = FileKind.EPUB))
                    }
                }
            }
        }
    }
    scan(tree, 0)
    return result.sortedBy { it.name.lowercase() }
}

/** 读取 txt 文件全文（UTF-8）。 */
fun readTextFile(context: Context, fileUri: String): String {
    val stream = context.contentResolver.openInputStream(Uri.parse(fileUri)) ?: return ""
    return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
