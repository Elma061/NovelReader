package com.reader.app.novel.model

/** epub 章节中的内容块：文本或插图。 */
sealed interface EpubBlock {
    data class Text(val content: String) : EpubBlock
    data class Image(val data: ByteArray) : EpubBlock
}

/** epub 中的一个章节，内容按文档顺序存为文本 / 插图块。 */
data class EpubChapter(
    val title: String,
    val blocks: List<EpubBlock>
)

/** 解析后的 epub 书籍。 */
data class EpubBook(
    val title: String,
    val chapters: List<EpubChapter>
)
