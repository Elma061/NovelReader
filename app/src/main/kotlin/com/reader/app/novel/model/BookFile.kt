package com.reader.app.novel.model

/** 文件类型，决定用哪种方式解析内容。 */
enum class FileKind { TXT, EPUB }

/**
 * 书籍文件夹里的一个可读文件。
 *
 * @param name 文件名（含扩展名）
 * @param uri  该文件的 SAF Uri 字符串，用于读取内容
 * @param kind 文件类型
 */
data class BookFile(
    val name: String,
    val uri: String,
    val kind: FileKind
)
