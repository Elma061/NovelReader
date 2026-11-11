package com.reader.app.novel.model

/**
 * 小说书籍模型。
 *
 * @param title    书籍标题，默认取用户所选文件夹的名称，后续可编辑
 * @param uri      绑定的本地文件夹地址（SAF Uri 字符串），用于以后读取该书目录下的章节文件
 * @param author   作者，暂留空，后续可从书籍元数据解析
 * @param category 分类，暂留空
 */
data class Book(
    val title: String,
    val uri: String,
    val author: String = "",
    val category: String = ""
)
