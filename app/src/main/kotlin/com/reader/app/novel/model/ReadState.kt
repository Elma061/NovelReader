package com.reader.app.novel.model

/**
 * 一本书的阅读进度：上次读到的文件、章节序号与标题。
 * 以书籍（文件夹）Uri 为键保存。
 */
data class ReadState(
    val fileUri: String,
    val chapterIndex: Int,
    val chapterTitle: String
)
