package com.music.msv.data.repository

import android.util.Log
import android.webkit.CookieManager
import com.music.msv.data.model.ImslpPdfFile
import com.music.msv.data.model.ImslpSearchResult
import com.music.msv.data.model.ImslpWorkDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * IMSLP（imslp.org）乐谱搜索与下载仓库。
 * 链路均经 imslp_test.py 实测验证：
 * ① 搜索：MediaWiki API list=search(srnamespace=0) + Category 命名空间分类搜索
 * ② 作品页：prop=revisions wikitext 三正则提取 PDF 文件名（|File Name N= / [[File:]] / {{#file:}}）
 * ③ 直链：MediaWiki 标准路径 /images/{md5[0]}/{md5[0:2]}/{文件名}，需免责 cookie；
 *    匿名下载会命中站点的人机验证（Bot Check/mtcaptcha）——由用户在 WebView 内亲自完成验证后携带其会话 cookie 重试，不程序化绕过
 */
class ImslpRepository {

    companion object {
        private const val BASE = "https://imslp.org"
        private const val API = "$BASE/api.php"
        private const val TAG = "MSV_Imslp"
        private const val TIMEOUT_MS = 15000
        private const val READ_TIMEOUT_MS = 60000

        /** 与 WebView 验证环境统一的 UA（人机验证的放行 cookie 通常绑定 UA+IP，两端必须一致） */
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

    /** 搜索：作品（主命名空间全文）+ 作曲家分类（Category 命名空间标题匹配） */
    suspend fun search(query: String): Pair<List<ImslpSearchResult>, List<ImslpSearchResult>> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(query, "UTF-8")
            val works = mutableListOf<ImslpSearchResult>()
            val composers = mutableListOf<ImslpSearchResult>()

            val searchJson = httpGetString("$API?action=query&list=search&srnamespace=0&srwhat=text&srlimit=50&format=json&srsearch=$q")
            if (searchJson != null) {
                try {
                    val arr = JSONObject(searchJson).optJSONObject("query")?.optJSONArray("search")
                    if (arr != null) for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val title = o.optString("title")
                        if (isNonWork(title)) continue
                        val snippet = o.optString("snippet").replace(Regex("<[^>]+>"), "").trim()
                        works.add(ImslpSearchResult(title, o.optLong("pageid"), false, snippet))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "search parse failed", e)
                }
            }

            // 作曲家分类候选：在 Category 命名空间搜分类标题（如 Category:Mozart, Wolfgang Amadeus）
            val catJson = httpGetString("$API?action=query&list=search&srnamespace=14&srlimit=20&format=json&srsearch=$q")
            if (catJson != null) {
                try {
                    val arr = JSONObject(catJson).optJSONObject("query")?.optJSONArray("search")
                    if (arr != null) for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val title = o.optString("title").removePrefix("Category:")
                        if (title.isEmpty()) continue
                        composers.add(ImslpSearchResult(title, o.optLong("pageid"), true, ""))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "composer search parse failed", e)
                }
            }
            Pair(works, composers)
        }

    /** 作曲家分类下的作品列表（categorymembers 过滤非主域条目） */
    suspend fun composerWorks(composer: String): List<ImslpSearchResult> = withContext(Dispatchers.IO) {
        val out = mutableListOf<ImslpSearchResult>()
        val json = httpGetString(
            "$API?action=query&list=categorymembers&cmlimit=500&format=json&cmtitle=" +
                URLEncoder.encode("Category:$composer", "UTF-8")
        )
        if (json != null) {
            try {
                val arr = JSONObject(json).optJSONObject("query")?.optJSONArray("categorymembers")
                if (arr != null) for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val title = o.optString("title")
                    if (isNonWork(title)) continue
                    out.add(ImslpSearchResult(title, o.optLong("pageid"), false, ""))
                }
            } catch (e: Exception) {
                Log.e(TAG, "composerWorks parse failed", e)
            }
        }
        out
    }

    /** 作品页详情：介绍模板字段 + PDF 版本列表（去重保序，第一个通常为主乐谱） */
    suspend fun workDetail(title: String): ImslpWorkDetail? = withContext(Dispatchers.IO) {
        val json = httpGetString(
            "$API?action=query&prop=revisions&rvprop=content&format=json&titles=" +
                URLEncoder.encode(title, "UTF-8")
        ) ?: return@withContext null
        try {
            val pages = JSONObject(json).optJSONObject("query")?.optJSONObject("pages")
                ?: return@withContext null
            var content = ""
            for (key in pages.keys()) {
                val o = pages.optJSONObject(key) ?: continue
                content = o.optJSONArray("revisions")?.optJSONObject(0)?.optString("*") ?: ""
                break
            }
            if (content.isEmpty()) return@withContext null

            // 三正则提取 PDF（去重保序）
            val pdfNames = LinkedHashSet<String>()
            Regex("""\|File Name \d+=\s*([^|\n]+\.pdf)""", RegexOption.IGNORE_CASE)
                .findAll(content).forEach { pdfNames.add(it.groupValues[1].trim()) }
            Regex("""\[\[[Ff]ile:([^\]]+\.pdf)\]\]""")
                .findAll(content).forEach { pdfNames.add(it.groupValues[1].trim()) }
            Regex("""\{\{#file:([^}]+\.pdf)\}\}""", RegexOption.IGNORE_CASE)
                .findAll(content).forEach { pdfNames.add(it.groupValues[1].trim()) }

            // 介绍字段：去除 wiki 模板/链接标记后取值
            fun clean(v: String): String = v
                .replace(Regex("\\{\\{[^}]*\\}\\}"), "")
                .replace(Regex("\\[\\[([^]|]*\\|)?([^]]*)\\]\\]"), "$2")
                .replace(Regex("<[^>]+>"), "")
                .trim()
            fun field(vararg names: String): String? {
                for (n in names) {
                    val m = Regex("""^\|\s*$n\s*=\s*(.+)$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
                        .find(content)
                    val v = m?.groupValues?.get(1)?.let(::clean)
                    if (!v.isNullOrEmpty() && v != "-") return v.take(120)
                }
                return null
            }
            val info = mutableListOf<Pair<String, String>>()
            field("Key")?.let { info.add("调性" to it) }
            field("Year/Date of Composition", "Year/Date of CompositionYear")?.let { info.add("创作年份" to it) }
            field("Number of Movements")?.let { info.add("乐章数" to it) }
            field("First Performance", "First Publication")?.let { info.add("首演/出版" to it) }
            field("Dedication")?.let { info.add("题献" to it) }

            val composer = Regex("""\(([^)]+)\)\s*$""").find(title)?.groupValues?.get(1) ?: ""
            val workTitle = title.substringBeforeLast("(").trim()
            ImslpWorkDetail(workTitle, composer, info, pdfNames.map { ImslpPdfFile(it) })
        } catch (e: Exception) {
            Log.e(TAG, "workDetail parse failed", e)
            null
        }
    }

    /** 直链：与 downloadPdf 共用（WebView 验证时加载同一 URL，门禁挑战与放行 cookie 绑定该地址） */
    fun directUrl(filename: String): String {
        val fname = filename.replace(" ", "_")
        val md5 = MessageDigest.getInstance("MD5").digest(fname.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "$BASE/images/${md5[0]}/${md5.substring(0, 2)}/${URLEncoder.encode(fname, "UTF-8").replace("+", "%20")}"
    }

    /** 下载指定 PDF 到 dest：md5 直链 + 免责/验证 cookie；门禁页时返回 BotCheck 由 UI 引导用户验证 */
    suspend fun downloadPdf(
        filename: String,
        dest: File,
        extraCookies: Map<String, String> = emptyMap(),
        onProgress: (Int) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        downloadUrl(directUrl(filename), UA, dest, extraCookies, onProgress)
    }

    /** 下载任意 URL 到 dest（WebView DownloadListener 捕获的最终地址可带其原生 UA，验证上下文最一致） */
    suspend fun downloadUrl(
        url: String,
        userAgent: String,
        dest: File,
        extraCookies: Map<String, String> = emptyMap(),
        onProgress: (Int) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val cookieHeader = buildString {
                append("imslpdisclaimeraccepted=yes")
                extraCookies.forEach { (k, v) -> append("; $k=$v") }
            }
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Cookie", cookieHeader)
                setRequestProperty("Referer", "$BASE/")
            }
            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return@withContext DownloadResult.Error("HTTP $code")
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
                if (!isPdfMagic) {
                    // 非 PDF（人机验证门禁页等）：转入验证步骤，由用户在 WebView 内亲自完成检查
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
                    if (body.contains("Bot Check")) {
                        Log.i(TAG, "downloadUrl hit Bot Check gate for $url")
                    } else {
                        Log.i(TAG, "downloadUrl got non-PDF 200 ($ctype) for $url — routing to verification")
                    }
                    return@withContext DownloadResult.BotCheck
                }
                val total = conn.contentLengthLong
                dest.outputStream().use { out ->
                    out.write(head, 0, off) // 先写入已读的魔数
                    var done = off.toLong()
                    var lastPct = -1
                    val buf = ByteArray(8192)
                    while (true) {
                        ensureActive() // 协作式取消
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
            }
            conn.disconnect()
            if (dest.exists() && dest.length() > 0) DownloadResult.Success(dest)
            else DownloadResult.Error("下载内容为空")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "downloadUrl failed: $url", e)
            DownloadResult.Error(e.message ?: "网络异常")
        }
    }

    /** 读取 WebView 验证后的 IMSLP 会话 cookie（用户在人机验证通过后调用） */
    fun verifyCookies(): Map<String, String> = try {
        val raw = CookieManager.getInstance().getCookie("https://imslp.org/") ?: ""
        raw.split(";").mapNotNull { p ->
            val i = p.indexOf('=')
            if (i > 0) p.substring(0, i).trim() to p.substring(i + 1).trim() else null
        }.toMap()
    } catch (e: Exception) {
        Log.e(TAG, "verifyCookies failed", e)
        emptyMap()
    }
}
