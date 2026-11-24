package com.reader.app.novel.model

/**
 * 小说书籍模型。
 *
 * @param title    书籍标题，默认取 epub 文件名（不含扩展名），后续可编辑
 * @param uri      绑定的本地 epub 文件地址（SAF Uri 字符串），用于读取正文与封面
 * @param author   作者，暂留空，后续可从书籍元数据解析
 * @param category 分类，暂留空
 */
data class Book(
    val title: String,
    val uri: String,
    val author: String = "",
    val category: String = ""
)
