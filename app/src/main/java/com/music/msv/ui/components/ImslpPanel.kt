package com.music.msv.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.msv.data.model.ImslpSearchResult
import com.music.msv.data.repository.FileRepository
import com.music.msv.data.repository.ImslpRepository
import com.music.msv.ui.theme.ButtonShape
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.net.URLEncoder

/** 步骤状态机：搜索 → 结果 → 官方页浏览（浏览中内嵌应用内下载与人工验证） */
private sealed interface ImslpStep {
    data object Search : ImslpStep
    data class Results(val works: List<ImslpSearchResult>, val composers: List<ImslpSearchResult>) : ImslpStep
    data class Browse(val url: String) : ImslpStep
}

/** 展示名：去掉 PMLP 编号前缀 */
private fun pdfDisplayName(filename: String): String =
    filename.replace(Regex("^PMLP\\d+[-_]?"), "").replace('_', ' ')

/**
 * IMSLP 页面注入脚本（onPageFinished 执行，幂等）：
 * ① 蜜罐守卫：门禁页隐藏 .pld 输入框被输入法/自动填充写入会中止 mtcaptcha 加载——持续清空；
 * ② 等待跳过：等待弹窗/页 #sm_dl_wait 的 data-id 即真实下载地址（倒计时纯前端），读出立即导航
 *    （被 shouldOverrideUrlLoading 拦截为应用内下载，12~15 秒等待完全跳过），
 *    并隐藏站点倒计时弹窗——避免"应用内已在下载、页面仍显示 15 秒等待"的困惑。
 */
private const val IMSLP_PAGE_JS = """(function(){
    if (window.__msvInjected) return;
    window.__msvInjected = 1;
    setInterval(function(){
        document.querySelectorAll('.pld').forEach(function(i){ i.value=''; });
        if (!window.__msvWaitSkip) {
            var w = document.getElementById('sm_dl_wait');
            if (w && w.dataset && w.dataset.id) {
                window.__msvWaitSkip = 1;
                var dlg = w.closest('.ui-dialog') || w;
                dlg.style.display = 'none';
                window.location = w.dataset.id;
            }
        }
    }, 500);
})();"""

/**
 * 页面加载提速：拦截广告/统计类第三方资源（IMSLP 页面挂载大量 gtag/Clarity/广告网络请求，
 * 占页面加载与流量大头；均为纯追踪/广告域，不涉及站点功能与 mtcaptcha 验证组件）。
 */
private val IMSLP_AD_HOSTS = listOf(
    "googletagmanager.com", "google-analytics.com", "analytics.google.com",
    "googlesyndication.com", "doubleclick.net", "googleadservices.com", "adservice.google.com",
    "clarity.ms", "hotjar.com", "mouseflow.com", "fullstory.com",
    "scorecardresearch.com", "quantserve.com", "quantcount.com",
    "amazon-adsystem.com", "adnxs.com", "adsrvr.org", "pubmatic.com", "rubiconproject.com",
    "openx.net", "casalemedia.com", "criteo.com", "criteo.net", "taboola.com", "outbrain.com",
    "smartadserver.com", "teads.tv", "sharethrough.com", "connect.facebook.net", "facebook.net",
    "bat.bing.com", "adnigma.io", "adngin.net", "adnium.com"
)

/**
 * IMSLP 乐谱搜索/下载弹窗。
 * 搜索在应用内完成（MediaWiki API）；点击作曲家/作品后内嵌 WebView 打开 IMSLP 官方页面
 * （真实浏览器上下文，人机验证通过率最高）。在官方页点击乐谱 PDF 链接时拦截为应用内下载
 * （自动跳过免责声明与 12 秒等待页）；命中 Bot Check 门禁时在 WebView 内由用户完成验证后自动重试。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ImslpDialog(
    isDark: Boolean,
    onDismiss: () -> Unit,
    onImported: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val panelBg = if (isDark) Color(0xF00F121C) else Color(0xF2FFFFFF)
    val panelBorder = if (isDark) Color(0x24FFFFFF) else Color(0x1A1A2230)
    val itemBg = if (isDark) Color(0x14FFFFFF) else Color(0x141A2230)
    val itemBorder = if (isDark) Color(0x1AFFFFFF) else Color(0x141A2230)
    val muted = if (isDark) Color(0x99FFFFFF) else Color(0x991A2230)
    val accent = if (isDark) Color(0xFF8CC8FF) else Color(0xFF2F6AD9)
    val text = if (isDark) Color.White else Color(0xFF1B2230)
    val onAccent = if (isDark) Color(0xFF0F1220) else Color.White

    val repo = remember { ImslpRepository() }
    val fileRepo = remember { FileRepository(context) }
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf<ImslpStep>(ImslpStep.Search) }
    var lastResults by remember { mutableStateOf<ImslpStep.Results?>(null) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // 应用内下载状态：downloadingUrl 非空 = 下载进行中；gateWait = 门禁页已加载待用户验证
    var downloadingUrl by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(0) }
    var gateWait by remember { mutableStateOf(false) }
    var gatePassed by remember { mutableStateOf(false) }
    var gateFileUrl by remember { mutableStateOf<String?>(null) }
    var gateRetry by remember { mutableStateOf(0) }
    // 官方页加载进度（0=刚开始/未加载，100=完成；1~99 期间显示进度条）
    var pageProgress by remember { mutableStateOf(100) }

    fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun goBack() {
        downloadJob?.cancel()
        if (downloadingUrl != null || gateWait) {
            downloadingUrl = null
            gateWait = false
            gatePassed = false
        }
        statusText = ""
        step = when (val s = step) {
            is ImslpStep.Search -> ImslpStep.Search
            is ImslpStep.Results -> ImslpStep.Search
            is ImslpStep.Browse -> {
                val wv = webViewRef
                if (wv != null && wv.canGoBack()) {
                    wv.goBack()
                    s
                } else {
                    lastResults ?: ImslpStep.Search
                }
            }
        }
    }

    fun doSearch(q: String) {
        if (q.isBlank() || busy) return
        busy = true
        statusText = "搜索中…"
        scope.launch {
            val (works, composers) = repo.search(q.trim())
            busy = false
            val rs = ImslpStep.Results(works, composers)
            lastResults = rs
            statusText = if (works.isEmpty() && composers.isEmpty()) "未找到相关结果，试试作曲家姓氏" else ""
            step = rs
        }
    }

    fun openComposer(c: ImslpSearchResult) {
        statusText = ""
        step = ImslpStep.Browse("https://imslp.org/wiki/Category:" + urlEncode(c.title))
    }

    fun openWork(w: ImslpSearchResult) {
        statusText = ""
        step = ImslpStep.Browse("https://imslp.org/wiki/" + urlEncode(w.title))
    }

    /** 应用内下载官方页拦截到的 PDF 直链；isAutoRetry=true 为门禁验证通过后的自动重试（不重置重试计数） */
    fun startInterceptedDownload(fileUrl: String, ua: String, isAutoRetry: Boolean = false) {
        if (downloadingUrl != null) return
        val name = runCatching { URLDecoder.decode(fileUrl.substringAfterLast('/'), "UTF-8") }
            .getOrElse { fileUrl.substringAfterLast('/') }
            .substringBefore('?').substringBefore('#')
        if (!name.endsWith(".pdf", ignoreCase = true)) return
        val dest = fileRepo.docsDirFile(name)
        downloadingUrl = fileUrl
        downloadProgress = 0
        gateWait = false
        gatePassed = false
        if (!isAutoRetry) gateRetry = 0
        statusText = "正在下载：${pdfDisplayName(name)}（返回可取消）"
        downloadJob = scope.launch {
            when (val r = repo.downloadUrl(fileUrl, ua, dest) { pct -> downloadProgress = pct }) {
                is ImslpRepository.DownloadResult.Success -> {
                    downloadingUrl = null
                    onImported(Uri.fromFile(dest))
                }
                is ImslpRepository.DownloadResult.BotCheck -> {
                    downloadingUrl = null
                    gateFileUrl = fileUrl
                    gateWait = true
                    statusText = "IMSLP 需要人工验证，请在页面中完成 \"Start Verification\"，通过后将自动继续下载"
                    webViewRef?.loadUrl(fileUrl) // 门禁页（mtcaptcha）在官方 WebView 上下文中渲染
                }
                is ImslpRepository.DownloadResult.Error -> {
                    downloadingUrl = null
                    statusText = "下载失败：${r.message}"
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(16.dp))
                .background(panelBg)
                .border(1.dp, panelBorder, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            // 顶栏
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("IMSLP 乐谱库", color = text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "✕", color = muted, fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(8.dp))

            // 返回行（非首步显示）
            if (step !is ImslpStep.Search) {
                Text(
                    "← 返回", color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { goBack() }
                        .padding(vertical = 2.dp)
                )
                Spacer(Modifier.height(6.dp))
            }

            // 状态行
            if (statusText.isNotEmpty()) {
                Text(statusText, color = muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
            }

            when (val s = step) {
                is ImslpStep.Search -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("搜索作品或作曲家…", fontSize = 13.sp, color = muted) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { doSearch(query) }),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = text),
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            Modifier
                                .clip(ButtonShape)
                                .background(accent)
                                .clickable { doSearch(query) }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text("搜索", color = onAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "搜索 IMSLP 公有领域乐谱，支持曲名与作曲家；\n点击结果进入官方页面浏览，在页面中点击乐谱即可应用内下载（自动跳过免责声明与等待）。",
                        color = muted, fontSize = 11.sp, lineHeight = 16.sp
                    )
                }

                is ImslpStep.Results -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (s.composers.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Text("👤 作曲家 (${s.composers.size})", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        items(s.composers, key = { "c" + it.pageid + it.title }) { c ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(itemBg)
                                    .border(1.dp, itemBorder, RoundedCornerShape(10.dp))
                                    .clickable { openComposer(c) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(c.title, color = text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (s.works.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Text("📄 作品 (${s.works.size})", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        items(s.works, key = { "w" + it.pageid + it.title }) { w ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(itemBg)
                                    .border(1.dp, itemBorder, RoundedCornerShape(10.dp))
                                    .clickable { openWork(w) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Column {
                                    Text(w.title, color = text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (w.snippet.isNotEmpty()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            w.snippet.take(160),
                                            color = muted, fontSize = 10.sp, lineHeight = 13.sp,
                                            maxLines = 4, overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                is ImslpStep.Browse -> {
                    // 页面加载进度（官方页资源较重，给出即时反馈）
                    if (pageProgress in 1..99) {
                        LinearProgressIndicator(
                            progress = { pageProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = accent
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    // 应用内下载进度（保持官方页挂载：验证/重试不丢浏览上下文）
                    if (downloadingUrl != null) {
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = accent
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("${downloadProgress}% · 下载完成后自动导入谱架并打开", color = muted, fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                    }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0A0D16))
                            .border(1.dp, itemBorder, RoundedCornerShape(10.dp))
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    // 真实浏览器上下文（UA 不覆盖，用设备默认）：人机验证放行 cookie 绑定 UA+IP，
                                    // 下载请求使用本 WebView 的同一 UA + CookieManager 会话 cookie
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = true
                                    settings.allowContentAccess = true
                                    settings.loadsImagesAutomatically = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                                    settings.setSupportZoom(true)
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    // 免责声明为 confirm() 弹窗 + 该 cookie 门：预置即静默跳过
                                    CookieManager.getInstance().setCookie("https://imslp.org/", "imslpdisclaimeraccepted=yes; Domain=.imslp.org; Path=/")
                                    CookieManager.getInstance().flush()
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            pageProgress = 0
                                        }

                                        // 页面加载提速：广告/统计类第三方资源直接返回空响应，不再发起真实请求
                                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                            val host = request.url.host?.lowercase() ?: return null
                                            val blocked = IMSLP_AD_HOSTS.any { host == it || host.endsWith(".$it") } ||
                                                request.url.toString().contains("adngin", ignoreCase = true)
                                            return if (blocked) {
                                                WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                                            } else null
                                        }

                                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                            val u = request.url
                                            val scheme = u.scheme ?: return false
                                            if (scheme != "http" && scheme != "https") return false
                                            val host = u.host ?: return false
                                            val imslp = host == "imslp.org" || host.endsWith(".imslp.org")
                                            val p = u.path ?: ""
                                            val isPdf = imslp && p.endsWith(".pdf", ignoreCase = true) &&
                                                (p.startsWith("/images/") || p.startsWith("/wiki/Special:Redirect/file/"))
                                            return when {
                                                isPdf -> {
                                                    // 官方页乐谱直链 → 应用内下载（UA 与 WebView 一致，cookie 走 CookieManager）
                                                    startInterceptedDownload(u.toString(), view.settings.userAgentString)
                                                    true
                                                }
                                                !imslp -> {
                                                    // 外部站点跳系统浏览器，避免把用户带离官方页
                                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, u)) }
                                                    true
                                                }
                                                else -> false
                                            }
                                        }

                                        override fun onPageFinished(view: WebView, url: String) {
                                            super.onPageFinished(view, url)
                                            view.evaluateJavascript(IMSLP_PAGE_JS, null)
                                        }
                                    }
                                    // 弹窗类验证组件可能在新建窗口打开：统一接管到本 WebView
                                    webChromeClient = object : android.webkit.WebChromeClient() {
                                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                                            pageProgress = newProgress
                                            super.onProgressChanged(view, newProgress)
                                        }

                                        override fun onCreateWindow(
                                            view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message
                                        ): Boolean {
                                            val transport = resultMsg.obj as android.webkit.WebView.WebViewTransport
                                            transport.webView = view
                                            resultMsg.sendToTarget()
                                            return true
                                        }
                                    }
                                    // 兜底：Content-Disposition 附件响应（shouldOverrideUrlLoading 未覆盖时触发）
                                    setDownloadListener { url, agent, _, _, _ ->
                                        android.util.Log.i("MSV_Imslp", "DownloadListener: $url")
                                        startInterceptedDownload(url, agent)
                                    }
                                    webViewRef = this
                                    loadUrl(s.url)
                                }
                            },
                            onRelease = { wv ->
                                if (webViewRef === wv) webViewRef = null
                                (wv.parent as? android.view.ViewGroup)?.removeAllViews()
                                wv.stopLoading()
                                wv.destroy()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // 门禁页轮询：用户完成 mtcaptcha 后页面出现 "Bot Check Passed" → 自动重试下载
                    LaunchedEffect(Unit) {
                        while (isActive) {
                            delay(1200)
                            if (!gateWait) continue
                            val wv = webViewRef ?: continue
                            wv.evaluateJavascript("(document.body?document.body.innerText:'')") { v ->
                                if (v != null && (v.contains("Bot Check Passed", ignoreCase = true) || v.contains("passed the bot test", ignoreCase = true))) {
                                    gatePassed = true
                                }
                            }
                        }
                    }
                    LaunchedEffect(gatePassed) {
                        if (!gatePassed) return@LaunchedEffect
                        gatePassed = false
                        val url = gateFileUrl ?: return@LaunchedEffect
                        gateWait = false
                        gateRetry++
                        if (gateRetry > 2) {
                            statusText = "验证已通过但下载仍被拦截，请在官方页面稍后重试"
                            gateFileUrl = null
                            return@LaunchedEffect
                        }
                        val ua = webViewRef?.settings?.userAgentString ?: ImslpRepository.USER_AGENT
                        statusText = "验证已通过，正在继续下载…"
                        startInterceptedDownload(url, ua, isAutoRetry = true)
                    }
                }
            }
        }
    }
}
