package com.music.msv.data.repository

import android.util.Log
import android.webkit.CookieManager
import com.music.msv.data.model.ImslpSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.coroutineContext

/**
 * IMSLP（imslp.org）乐谱搜索与下载仓库。
 * 链路经 2026-09 实测验证：
 * ① 搜索：MediaWiki API list=search（作品主命名空间 + Category 命名空间作曲家分类）
 * ② 浏览/验证：作品与作曲家页在应用内 WebView 打开官方页面（真实浏览器上下文，人机验证通过率最高）
 * ③ 下载：拦截官方页 .pdf 直链（/images/{md5[0]}/{md5[0:2]}/{文件名}），携带 WebView 会话 cookie
 *    与其同一 UA 应用内下载；免责声明靠预置 imslpdisclaimeraccepted cookie 跳过（confirm 门）；
 *    等待页为纯前端倒计时，真实下载地址在其 #sm_dl_wait 的 data-id 属性中，读出即下载，无需等待；
 *    命中 Bot Check 门禁时由用户在 WebView 内亲自完成验证后携带其会话 cookie 重试，不程序化绕过
 */
class ImslpRepository {

    companion object {
        private const val BASE = "https://imslp.org"
        private const val API = "$BASE/api.php"
        private const val TAG = "MSV_Imslp"
        private const val TIMEOUT_MS = 15000
        private const val READ_TIMEOUT_MS = 60000

        /** 等待页轮询总预算：覆盖站点强制的匿名下载间隔（实测提示 12~15 秒） */
        private const val WAIT_BUDGET_MS = 16000L

        /** 仅供 api.php 搜索使用；WebView/下载一律用 WebView 实际 UA（验证放行 cookie 绑定 UA+IP，两端必须一致） */
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private val UA = USER_AGENT
        private val NON_WORK_PREFIXES =
            listOf("Category:", "Talk:", "File:", "User:", "Template:", "IMSLP:", "Portal:", "Help:", "Wishlist")
    }

    sealed class DownloadResult {
        data class Success(val file: File) : DownloadResult()
        data object BotCheck : DownloadResult()
        data class Error(val message: String) : DownloadResult()
    }

    private fun httpGetString(url: String): String? {
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Cookie", "imslpdisclaimeraccepted=yes")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            return body
        } catch (e: Exception) {
            Log.e(TAG, "httpGetString failed: $url", e)
            return null
        }
    }

    private fun isNonWork(title: String): Boolean = NON_WORK_PREFIXES.any { title.startsWith(it) }

    /**
     * 搜索简介清洗：MediaWiki snippet 是 wiki 模板原文（如 "|Performer Categories=…" 参数行、{{模板}}、
     * [[链接|文本]]、HTML 实体），直接展示会出现无效字符。过滤模板参数行/标记并解码实体；
     * 清洗后为空则返回空串（UI 侧 isNotEmpty 判断自动不渲染灰字区）。
     */
    private fun cleanSnippet(raw: String): String = raw
        .replace(Regex("<[^>]+>"), "")                                  // 去 searchmatch 高亮等标签
        .lines()
        .filter { it.isNotBlank() && !it.trimStart().startsWith("|") }   // 丢弃模板参数行（|Field=Value 无效信息）
        .joinToString(" ")
        .replace(Regex("\\{\\{[^{}]*\\}\\}"), "")                        // {{模板}}
        .replace(Regex("\\[\\[([^]|]*\\|)?([^]]*)\\]\\]"), "$2")         // [[链接|文本]]→文本
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace(Regex("&#0?39;"), "'").replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ").trim()

    /** 搜索：作品（主命名空间全文）+ 作曲家分类（Category 命名空间标题匹配）；两组请求并行以缩短等待 */
    suspend fun search(query: String): Pair<List<ImslpSearchResult>, List<ImslpSearchResult>> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(query, "UTF-8")
            coroutineScope {
                val works = async {
                    val out = mutableListOf<ImslpSearchResult>()
                    val searchJson = httpGetString("$API?action=query&list=search&srnamespace=0&srwhat=text&srlimit=50&format=json&srsearch=$q")
                    if (searchJson != null) {
                        try {
                            val arr = JSONObject(searchJson).optJSONObject("query")?.optJSONArray("search")
                            if (arr != null) for (i in 0 until arr.length()) {
                                val o = arr.getJSONObject(i)
                                val title = o.optString("title")
                                if (isNonWork(title)) continue
                                val snippet = cleanSnippet(o.optString("snippet"))
                                out.add(ImslpSearchResult(title, o.optLong("pageid"), false, snippet))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "search parse failed", e)
                        }
                    }
                    out
                }
                val composers = async {
                    val out = mutableListOf<ImslpSearchResult>()
                    // 作曲家分类候选：在 Category 命名空间搜分类标题（如 Category:Mozart, Wolfgang Amadeus）
                    val catJson = httpGetString("$API?action=query&list=search&srnamespace=14&srlimit=20&format=json&srsearch=$q")
                    if (catJson != null) {
                        try {
                            val arr = JSONObject(catJson).optJSONObject("query")?.optJSONArray("search")
                            if (arr != null) for (i in 0 until arr.length()) {
                                val o = arr.getJSONObject(i)
                                val title = o.optString("title").removePrefix("Category:")
                                if (title.isEmpty()) continue
                                out.add(ImslpSearchResult(title, o.optLong("pageid"), true, ""))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "composer search parse failed", e)
                        }
                    }
                    out
                }
                Pair(works.await(), composers.await())
            }
        }

    /** 组装 cookie 头：WebView 会话 cookie 全量携带（验证放行 cookie 绑定会话）；免责 cookie 缺失时补上 */
    private fun cookieHeader(url: String): String {
        val jar = try {
            CookieManager.getInstance().getCookie(url)
        } catch (e: Exception) {
            Log.e(TAG, "read cookies failed", e)
            null
        } ?: ""
        return if (jar.contains("imslpdisclaimeraccepted=")) jar
        else if (jar.isEmpty()) "imslpdisclaimeraccepted=yes"
        else "imslpdisclaimeraccepted=yes; $jar"
    }

    private fun openConn(url: String, userAgent: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Cookie", cookieHeader(url))
            setRequestProperty("Referer", "$BASE/")
        }

    /** 相对地址解析为绝对地址（data-id/meta refresh 目标可能是相对/协议相对形式） */
    private fun resolveUrl(u: String): String = when {
        u.startsWith("http") -> u
        u.startsWith("//") -> "https:$u"
        u.startsWith("/") -> "$BASE$u"
        else -> u
    }

    /** 非 PDF HTML 响应分类：等待页取 data-id（真实下载地址，倒计时纯前端）；门禁页返回 null 交 WebView 人工验证 */
    private fun nextUrlFromHtml(body: String): String? {
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

    /** 下载 URL（官方页拦截到的 PDF 直链或等待页 data-id）到 dest；进度回调 + 协作式取消 */
    suspend fun downloadUrl(
        url: String,
        userAgent: String,
        dest: File,
        onProgress: (Int) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        downloadLoop(url, userAgent, dest, onProgress)
    }

    /** 下载循环主体（独立挂起函数 + 显式返回类型，规避 withContext lambda 多返回点的 K2 推断问题） */
    private suspend fun downloadLoop(
        url: String,
        userAgent: String,
        dest: File,
        onProgress: (Int) -> Unit
    ): DownloadResult {
        var current = url
        val seen = mutableSetOf<String>()
        val deadline = System.currentTimeMillis() + WAIT_BUDGET_MS
        var hops = 0
        val job = coroutineContext[Job] // 供内层非挂起 lambda（流式落盘）做协作式取消检查
        try {
            while (true) {
                coroutineContext.ensureActive()
                // 重复请求同一 URL（等待页循环）：按 2s 间隔轮询至总预算，期间站点的匿名等待间隔自然耗尽
                if (!seen.add(current)) {
                    if (System.currentTimeMillis() >= deadline) {
                        return DownloadResult.Error("IMSLP 仍要求下载间隔，请稍后重试")
                    }
                    delay(2000)
                }
                if (++hops > 8) return DownloadResult.BotCheck
                val conn = openConn(current, userAgent)
                val code = conn.responseCode
                if (code != 200) {
                    conn.disconnect()
                    return DownloadResult.Error("HTTP $code")
                }
                val ctype = conn.contentType ?: ""
                conn.inputStream.use { input ->
                    // 魔数校验：读前 5 字节判断是否 %PDF-（Content-Type 可能是 octet-stream 等非标准值）
                    val head = ByteArray(5)
                    var off = 0
                    while (off < 5) {
                        val n = input.read(head, off, 5 - off)
                        if (n == -1) break
                        off += n
                    }
                    val isPdfMagic = off == 5 && String(head, 0, 5, Charsets.US_ASCII) == "%PDF-"
                    if (isPdfMagic) {
                        val total = conn.contentLengthLong
                        dest.outputStream().use { out ->
                            out.write(head, 0, off) // 先写入已读的魔数
                            var done = off.toLong()
                            var lastPct = -1
                            val buf = ByteArray(8192)
                            while (true) {
                                job?.ensureActive() // 协作式取消
                                val n = input.read(buf)
                                if (n == -1) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val pct = (done * 100 / total).toInt().coerceIn(0, 100)
                                    if (pct != lastPct) {
                                        lastPct = pct
                                        onProgress(pct)
                                    }
                                }
                            }
                        }
                        conn.disconnect()
                        return if (dest.exists() && dest.length() > 0) DownloadResult.Success(dest)
                        else DownloadResult.Error("下载内容为空")
                    }
                    // 非 PDF（等待页/门禁页等 HTML）：读头部判定
                    val body = buildString {
                        val buf = ByteArray(4096)
                        var total = 0
                        while (total < 20000) {
                            val n = input.read(buf)
                            if (n == -1) break
                            append(String(buf, 0, n, Charsets.UTF_8))
                            total += n
                        }
                    }
                    conn.disconnect()
                    val gated = body.contains("Bot Check") || body.contains("Start Verification")
                    if (gated) Log.i(TAG, "downloadUrl hit Bot Check gate for $current")
                    else Log.i(TAG, "downloadUrl got non-PDF 200 ($ctype) for $current")
                    val next = nextUrlFromHtml(body)
                    if (next == null) return DownloadResult.BotCheck
                    current = next
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "downloadUrl failed: $url", e)
            return DownloadResult.Error(e.message ?: "网络异常")
        }
        // 兜底返回（K2 不将 while(true)+try/catch 判定为必然非正常出口；实际不可达）
        return DownloadResult.Error("下载中断")
    }
}
