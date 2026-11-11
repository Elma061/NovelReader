package com.reader.app.novel.data

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.reader.app.novel.model.EpubBlock
import com.reader.app.novel.model.EpubBook
import com.reader.app.novel.model.EpubChapter
import com.reader.app.novel.model.EpubDetail
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 极简 epub 解析器：把 epub（本质是 zip）解出文本与插图，供阅读器展示。
 * 解析流程：META-INF/container.xml -> OPF 路径 -> OPF 里的 manifest 与 spine -> 依序抽取章节 -> 转成文本/插图块。
 */
fun parseEpub(context: Context, fileUri: String): EpubBook {
    val entries = runCatching {
        context.contentResolver.openInputStream(Uri.parse(fileUri))?.use { readZipEntries(it) { name -> !shouldSkip(name) } }
    }.getOrNull() ?: return EpubBook("", emptyList())

    val opfPath = findOpfPath(entries) ?: return EpubBook("", emptyList())
    val opf = findEntry(entries, opfPath)?.toString(Charsets.UTF_8) ?: return EpubBook("", emptyList())
    val info = parseOpf(opf) ?: return EpubBook("", emptyList())

    val baseDir = opfPath.substringBeforeLast('/', "")
    val chapters = info.spine.mapIndexedNotNull { index, idref ->
        val href = info.manifest[idref] ?: return@mapIndexedNotNull null
        val path = resolvePath(baseDir, href)
        val html = findEntry(entries, path)?.toString(Charsets.UTF_8)
            ?: return@mapIndexedNotNull null
        val chapterDir = path.substringBeforeLast('/', "")
        EpubChapter(
            title = chapterTitle(html, idref, index),
            blocks = parseChapterBlocks(html, chapterDir, entries)
        )
    }

    return EpubBook(title = info.title.ifBlank { "" }, chapters = chapters)
}

/** 解析 epub 概要：书名、封面、章节标题（供详情页展示，不载入正文与全部图片）。 */
fun parseEpubDetail(context: Context, fileUri: String): EpubDetail {
    val entries = runCatching {
        context.contentResolver.openInputStream(Uri.parse(fileUri))?.use { readZipEntries(it, ::isTextName) }
    }.getOrNull() ?: return EpubDetail("", null, emptyList())

    val opfPath = findOpfPath(entries) ?: return EpubDetail("", null, emptyList())
    val opf = findEntry(entries, opfPath)?.toString(Charsets.UTF_8) ?: return EpubDetail("", null, emptyList())
    val info = parseOpf(opf) ?: return EpubDetail("", null, emptyList())

    val baseDir = opfPath.substringBeforeLast('/', "")
    val chapterTitles = info.spine.mapIndexedNotNull { index, idref ->
        val href = info.manifest[idref] ?: return@mapIndexedNotNull null
        val path = resolvePath(baseDir, href)
        val html = findEntry(entries, path)?.toString(Charsets.UTF_8) ?: return@mapIndexedNotNull null
        chapterTitle(html, idref, index)
    }

    val coverHref = info.coverImageId?.let { info.manifest[it] }
        ?: info.coverId?.let { info.manifest[it] }
    val cover = coverHref
        ?.let { resolvePath(baseDir, it) }
        ?.let { path ->
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(fileUri))?.use { readSingleEntry(it, path) }
            }.getOrNull()
        }

    return EpubDetail(title = info.title, cover = cover, chapterTitles = chapterTitles)
}

// ---------------------------------------------------------------------------
// ZIP 读取
// ---------------------------------------------------------------------------

/** 读取 zip 内符合 keep 条件的条目。 */
private fun readZipEntries(stream: InputStream, keep: (String) -> Boolean): Map<String, ByteArray> {
    val result = mutableMapOf<String, ByteArray>()
    ZipInputStream(BufferedInputStream(stream)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && keep(entry.name)) {
                result[entry.name] = zip.readBytes()
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    return result
}

/** 只读单个条目（如封面图），避免把整本书的图片都载入内存。 */
private fun readSingleEntry(stream: InputStream, path: String): ByteArray? {
    ZipInputStream(BufferedInputStream(stream)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name.equals(path, ignoreCase = true)) {
                return zip.readBytes()
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    return null
}

/** 需要跳过的条目扩展名（保留 jpg/png/gif/webp 等位图；svg 暂不支持）。 */
private val SKIP_EXTENSIONS = setOf(
    "ttf", "otf", "woff", "woff2", "eot",
    "mp3", "mp4", "wav", "ogg", "aac", "flac", "m4a", "m4b",
    "pdf", "swf", "zip", "rar", "jar", "svg"
)

private fun shouldSkip(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in SKIP_EXTENSIONS
}

/** 目录/概要只需要文本条目，跳过图片等二进制。 */
private val TEXT_EXTENSIONS = setOf("xhtml", "html", "htm", "xml", "ncx", "opf", "css")

private fun isTextName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS

/** 精确查找失败时退回大小写不敏感查找。 */
private fun findEntry(entries: Map<String, ByteArray>, path: String): ByteArray? {
    entries[path]?.let { return it }
    val lower = path.lowercase()
    return entries.entries.firstOrNull { it.key.lowercase() == lower }?.value
}

// ---------------------------------------------------------------------------
// XML 解析
// ---------------------------------------------------------------------------

private val XmlPullParser.local: String
    get() = name.substringAfter(':')

private fun newParser(xml: String): XmlPullParser {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(xml.reader())
    return parser
}

/** 从 container.xml 中取出第一个 rootfile 的 full-path。 */
private fun findOpfPath(entries: Map<String, ByteArray>): String? {
    val container = findEntry(entries, "META-INF/container.xml")?.toString(Charsets.UTF_8)
        ?: return null
    val parser = newParser(container)
    var type = parser.next()
    while (type != XmlPullParser.END_DOCUMENT) {
        if (type == XmlPullParser.START_TAG && parser.local == "rootfile") {
            val fullPath = parser.getAttributeValue(null, "full-path")
            if (!fullPath.isNullOrBlank()) return fullPath
        }
        type = parser.next()
    }
    return null
}

private data class OpfInfo(
    val title: String,
    val manifest: Map<String, String>, // id -> href
    val spine: List<String>,           // 依序的 idref
    val coverId: String?,              // EPUB2: <meta name="cover" content="id">
    val coverImageId: String?          // EPUB3: <item properties="cover-image">
)

/** 解析 OPF：书名、manifest、spine 顺序，以及封面条目。 */
private fun parseOpf(xml: String): OpfInfo? {
    val parser = newParser(xml)
    var title = ""
    val manifest = mutableMapOf<String, String>()
    val spine = mutableListOf<String>()
    var coverId: String? = null
    var coverImageId: String? = null

    var type = parser.next()
    while (type != XmlPullParser.END_DOCUMENT) {
        if (type == XmlPullParser.START_TAG) {
            when (parser.local) {
                "title" -> if (title.isEmpty()) title = parser.nextText().trim()
                "item" -> {
                    val id = parser.getAttributeValue(null, "id")
                    val href = parser.getAttributeValue(null, "href")
                    if (id != null && href != null) manifest[id] = href
                    val properties = parser.getAttributeValue(null, "properties") ?: ""
                    if (id != null && "cover-image" in properties.split(Regex("\\s+"))) {
                        coverImageId = id
                    }
                }
                "itemref" -> parser.getAttributeValue(null, "idref")?.let { spine.add(it) }
                "meta" -> if (parser.getAttributeValue(null, "name") == "cover") {
                    coverId = parser.getAttributeValue(null, "content")
                }
            }
        }
        type = parser.next()
    }

    if (manifest.isEmpty() || spine.isEmpty()) return null
    return OpfInfo(title, manifest, spine, coverId, coverImageId)
}

/** 把相对某个目录的 href 解析成 zip 内的完整路径（去掉锚点/参数、URL 解码、处理 ..）。 */
private fun resolvePath(baseDir: String, href: String): String {
    val cleaned = href.substringBefore('#').substringBefore('?')
    val decoded = Uri.decode(cleaned)
    val segments = baseDir.split('/').filter { it.isNotEmpty() }.toMutableList()
    for (seg in decoded.split('/')) {
        when (seg) {
            "", "." -> Unit
            ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
            else -> segments.add(seg)
        }
    }
    return segments.joinToString("/")
}

// ---------------------------------------------------------------------------
// 章节内容 -> 文本 / 插图块
// ---------------------------------------------------------------------------

/** 把章节 XHTML 按文档顺序拆成文本块与插图块。 */
private fun parseChapterBlocks(
    html: String,
    chapterDir: String,
    entries: Map<String, ByteArray>
): List<EpubBlock> {
    val s = Regex("""<(script|style)[^>]*>.*?</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .replace(html, "")

    val blocks = mutableListOf<EpubBlock>()
    // 插图两种常见写法：<img src="..."> 与 <svg><image xlink:href="..."/></svg>
    val imgRegex = Regex("""<(img|image)\b[^>]*>""", RegexOption.IGNORE_CASE)
    val srcRegex = Regex("""(?:src|xlink:href|href)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    var lastIndex = 0
    for (match in imgRegex.findAll(s)) {
        appendText(blocks, s.substring(lastIndex, match.range.first))

        val src = srcRegex.find(match.value)?.groupValues?.get(1)
        if (!src.isNullOrBlank()) {
            val imgPath = resolvePath(chapterDir, src)
            findEntry(entries, imgPath)?.let { blocks.add(EpubBlock.Image(it)) }
        }
        lastIndex = match.range.last + 1
    }
    appendText(blocks, s.substring(lastIndex))
    return blocks
}

private fun appendText(blocks: MutableList<EpubBlock>, raw: String) {
    val text = htmlToText(raw)
    if (text.isNotBlank()) blocks.add(EpubBlock.Text(text))
}

private fun chapterTitle(html: String, idref: String, index: Int): String {
    Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .find(html)?.groupValues?.get(1)?.trim()?.let { return stripTags(it) }
    Regex("""<h[1-6][^>]*>(.*?)</h[1-6]>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .find(html)?.groupValues?.get(1)?.trim()?.let { return stripTags(it) }
    return idref.ifBlank { "第 ${index + 1} 节" }
}

private fun htmlToText(html: String): String {
    var s = html
    // 换行标签
    s = Regex("""<(br\s*/?|/p|/div|/h[1-6]|/li|/tr)[^>]*>""", RegexOption.IGNORE_CASE)
        .replace(s, "\n")
    s = Regex("""</(blockquote|section|article|table|ul|ol)>""", RegexOption.IGNORE_CASE)
        .replace(s, "\n")
    // 去掉其余标签
    s = Regex("""<[^>]+>""").replace(s, "")
    s = decodeEntities(s)
    // 每段首行缩进两个全角空格（U+3000，约等于两个汉字宽）；去掉 HTML 自带的缩进空白，空行保留
    return s.lines()
        .joinToString("\n") { line ->
            val t = line.trim(' ', '\t')
            if (t.isEmpty()) "" else "　　$t"
        }
        .trim('\n', '\r', '\t', ' ')
}

private fun stripTags(s: String): String = decodeEntities(Regex("""<[^>]+>""").replace(s, ""))

private fun decodeEntities(s: String): String {
    return s
        .replace(Regex("""&#x([0-9a-fA-F]+);""")) { m -> m.groupValues[1].toInt(16).toChar().toString() }
        .replace(Regex("""&#(\d+);""")) { m -> m.groupValues[1].toInt().toChar().toString() }
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
}
