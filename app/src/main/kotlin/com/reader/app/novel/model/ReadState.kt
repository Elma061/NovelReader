package com.reader.app.novel.model

/**
 * 一本书的阅读进度：上次读到的文件、章节与章节内的内容锚点。
 * 锚点记录「顶部可见的内容块序号 + 块内偏移」。块序号对应正文段落/插图，
 * 与屏幕尺寸、字号无关，换设备后仍能定位到同一段内容。
 * 以书籍（文件夹）Uri 为键保存。
 */
data class ReadState(
    val fileUri: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val anchorIndex: Int = 0,
    val anchorOffset: Int = 0
)
