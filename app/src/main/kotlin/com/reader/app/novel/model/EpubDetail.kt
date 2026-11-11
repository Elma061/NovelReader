package com.reader.app.novel.model

/**
 * epub 概要信息：书名、封面图片、章节标题列表。
 * 供书籍详情页 / 目录展示，不载入正文全文与全部插图。
 */
data class EpubDetail(
    val title: String,
    val cover: ByteArray?,
    val chapterTitles: List<String>
)
