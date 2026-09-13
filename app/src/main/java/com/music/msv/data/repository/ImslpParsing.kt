package com.music.msv.data.repository

/**
 * IMSLP 页面 HTML / 链接解析（纯函数，无 Android 依赖，可单测）。
 *
 * 站点改版时的唯一改动点集中在此：等待页 data-id 选择器、门禁关键词、相对地址解析规则。
 * 一旦 IMSLP 改版，只需修这一个文件，并在此文件对应的单测里补一条用例固化新格式。
 */
internal object ImslpParsing {

    const val BASE = "https://imslp.org"

    /** 非作品命名空间前缀（分类/讨论/文件等，不进作品结果列表） */
    private val NON_WORK_PREFIXES =
        listOf("Category:", "Talk:", "File:", "User:", "Template:", "IMSLP:", "Portal:", "Help:", "Wishlist")

    /** 是否为非作品命名空间标题 */
    fun isNonWork(title: String): Boolean = NON_WORK_PREFIXES.any { title.startsWith(it) }

    /**
     * 搜索简介清洗：MediaWiki snippet 是 wiki 模板原文（如 "|Performer Categories=…" 参数行、{{模板}}、
     * [[链接|文本]]、HTML 实体），直接展示会出现无效字符。过滤模板参数行/标记并解码实体；
     * 清洗后为空则返回空串（UI 侧 isNotEmpty 判断自动不渲染灰字区）。
     */
    fun cleanSnippet(raw: String): String = raw
        .replace(Regex("<[^>]+>"), "")                                  // 去 searchmatch 高亮等标签
        .lines()
        .filter { it.isNotBlank() && !it.trimStart().startsWith("|") }   // 丢弃模板参数行（|Field=Value 无效信息）
        .joinToString(" ")
        .replace(Regex("\\{\\{[^{}]*\\}\\}"), "")                        // {{模板}}
        .replace(Regex("\\[\\[([^]|]*\\|)?([^]]*)\\]\\]"), "$2")         // [[链接|文本]]→文本
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace(Regex("&#0?39;"), "'").replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ").trim()

    /** 相对地址解析为绝对地址（data-id/meta refresh 目标可能是相对/协议相对形式） */
    fun resolveUrl(u: String): String = when {
        u.startsWith("http") -> u
        u.startsWith("//") -> "https:$u"
        u.startsWith("/") -> "$BASE$u"
        else -> u
    }

    /**
     * 非 PDF HTML 响应分类：等待页取 data-id（真实下载地址，倒计时纯前端）；门禁页返回 null 交 WebView 人工验证。
     */
    fun nextUrlFromHtml(body: String): String? {
        if (body.contains("Bot Check") || body.contains("Start Verification")) return null
        // 等待页：#sm_dl_wait 的 data-id 即真实下载地址
        Regex("""data-id=["']([^"']+)["']""").findAll(body)
            .map { it.groupValues[1] }
            .firstOrNull { it.contains(".pdf", ignoreCase = true) || it.startsWith("http") || it.startsWith("//") }
            ?.let { return resolveUrl(it) }
        // 兜底：meta refresh / location.href
        Regex("""content=["']\d+\s*;\s*url=([^"']+)["']""", RegexOption.IGNORE_CASE).find(body)
            ?.let { return resolveUrl(it.groupValues[1]) }
        Regex("""location(?:\.href)?\s*=\s*["']([^"']+)["']""").find(body)
            ?.let { return resolveUrl(it.groupValues[1]) }
        return null
    }
}
