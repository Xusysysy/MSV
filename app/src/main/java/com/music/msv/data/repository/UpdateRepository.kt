package com.music.msv.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.music.msv.data.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * 逐段数值版本比较：a < b → 负数，相等 → 0，a > b → 正数。
 * 短段补 0（2.0 == 2.0.0），容错 v/V 前缀，非数字段取前导数字。
 */
fun compareVersions(a: String, b: String): Int {
    fun parse(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V")
            .split('.')
            .map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
    val pa = parse(a)
    val pb = parse(b)
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x.compareTo(y)
    }
    return 0
}

class UpdateRepository(private val context: Context) {

    companion object {
        private const val GITEE_REPO_API = "https://gitee.com/api/v5/repos/lin-xiaochuan/msv"
        private const val GITHUB_REPO_API = "https://api.github.com/repos/Xusysysy/MSV"
        private const val GITEE_LATEST = "$GITEE_REPO_API/releases/latest"
        private const val GITHUB_LATEST = "$GITHUB_REPO_API/releases/latest"
        private const val TIMEOUT_MS = 8000
        private const val APK_PREFIX = "MSV-ScoreViewer"
        private const val UPDATE_DIR = "update"
        private const val APK_MIME = "application/vnd.android.package-archive"
    }

    val installedVersionName: String
        get() = packageInfo().versionName ?: ""

    val installedVersionCode: Int
        get() = PackageInfoCompat.getLongVersionCode(packageInfo()).toInt()

    private fun packageInfo(): android.content.pm.PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName, PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }

    // ── 更新检查（必须在 Dispatchers.IO 协程内调用）──

    fun fetchGiteeLatest(): UpdateInfo? = fetchLatest(GITEE_LATEST, isGitee = true)

    fun fetchGitHubLatest(): UpdateInfo? = fetchLatest(GITHUB_LATEST, isGitee = false)

    private fun fetchLatest(url: String, isGitee: Boolean): UpdateInfo? {
        val body = httpGetString(url, isGitee) ?: return null
        return try {
            val json = JSONObject(body)
            // 两平台 assets 均混有自动生成的源码包，必须按 .apk 前缀过滤
            val apk = json.optJSONArray("assets")
                ?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it) } }
                ?.firstOrNull {
                    val name = it.optString("name")
                    name.endsWith(".apk") && name.startsWith(APK_PREFIX)
                } ?: return null
            UpdateInfo(
                tag = json.optString("tag_name"),
                notes = summarizeNotes(json.optString("body")),
                fullNotes = json.optString("body"),
                apkUrl = apk.optString("browser_download_url"),
                source = if (isGitee) "Gitee" else "GitHub"
            )
        } catch (_: Exception) {
            null
        }
    }

    /** 按已安装版本 tag 查询对应 release 的完整更新日志，Gitee 优先 */
    fun fetchTagNotes(tag: String): String? =
        fetchTagBody("$GITEE_REPO_API/releases/tags/$tag", isGitee = true)
            ?: fetchTagBody("$GITHUB_REPO_API/releases/tags/$tag", isGitee = false)

    private fun fetchTagBody(url: String, isGitee: Boolean): String? =
        httpGetString(url, isGitee)?.let {
            try {
                JSONObject(it).optString("body").ifEmpty { null }
            } catch (_: Exception) {
                null
            }
        }

    private fun httpGetString(url: String, isGitee: Boolean): String? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MSV-Updater")
            if (!isGitee) setRequestProperty("Accept", "application/vnd.github+json")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        body
    } catch (_: Exception) {
        null
    }

    /** release body 摘要：去标题前缀、拼行、超长截断 */
    fun summarizeNotes(body: String, maxLen: Int = 300): String {
        val lines = body.lines().map { it.removePrefix("# ").trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return "暂无更新说明"
        val sb = StringBuilder()
        for (line in lines) {
            if (sb.length + line.length + 1 > maxLen) {
                sb.append("…")
                break
            }
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(line)
        }
        return sb.toString()
    }

    // ── 下载（系统 DownloadManager，进度由通知栏展示）──

    /** 应用内流式下载更新包（替代系统 DownloadManager，进度经 onProgress 回调；支持协程取消），成功返回已下载文件 */
    suspend fun downloadApk(url: String, tag: String, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        val file = updateApkFile(tag)
        file.delete()
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            conn.connect()
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(8192)
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        coroutineContext.ensureActive() // 协作式取消：下载中断开即停止
                        val n = input.read(buf)
                        if (n == -1) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                        }
                    }
                }
            }
            if (file.exists() && file.length() > 0) file else null
        } catch (e: kotlinx.coroutines.CancellationException) {
            file.delete()
            throw e
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    fun apkFileName(tag: String) = "msv-update-$tag.apk"

    fun updateApkFile(tag: String): File {
        val dir = File(context.getExternalFilesDir(null), UPDATE_DIR).apply { mkdirs() }
        return File(dir, apkFileName(tag))
    }

    /** 升级成功后清理已安装版本对应的历史安装包（不误删未安装的新版包） */
    fun cleanupDownloadedApk(installedVersion: String) {
        val dir = context.getExternalFilesDir(null)?.let { File(it, UPDATE_DIR) } ?: return
        dir.listFiles()?.forEach { f ->
            if (f.name == apkFileName(installedVersion)) f.delete()
        }
    }

    // ── 安装 ──

    fun isInstallPermissionGranted(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(tag: String): Intent? {
        val file = updateApkFile(tag)
        if (!file.exists() || file.length() == 0L) return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
