package com.music.msv.ui.components

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import com.music.msv.data.model.ImslpPdfFile
import com.music.msv.data.model.ImslpSearchResult
import com.music.msv.data.model.ImslpWorkDetail
import com.music.msv.data.repository.FileRepository
import com.music.msv.data.repository.ImslpRepository
import com.music.msv.ui.theme.ButtonShape
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 弹窗内步骤状态机：搜索 → 结果 → 作品详情 → 下载中 → 人机验证 */
private sealed interface ImslpStep {
    data object Search : ImslpStep
    data class Results(val works: List<ImslpSearchResult>, val composers: List<ImslpSearchResult>) : ImslpStep
    data class ComposerWorks(val composer: String, val works: List<ImslpSearchResult>) : ImslpStep
    data class Detail(val detail: ImslpWorkDetail) : ImslpStep
    data class Downloading(val detail: ImslpWorkDetail, val name: String, val progress: Int) : ImslpStep
    data class Verify(val detail: ImslpWorkDetail, val pdf: ImslpPdfFile, val dest: java.io.File) : ImslpStep
}

/** 展示名：去掉 PMLP 编号前缀 */
private fun pdfDisplayName(filename: String): String =
    filename.replace(Regex("^PMLP\\d+[-_]?"), "").replace('_', ' ')

/**
 * IMSLP 乐谱搜索/下载弹窗。
 * 下载命中站点人机验证（Bot Check）时，在弹窗内嵌 WebView 由用户亲自完成验证，
 * 随后携带其会话 cookie 重试下载；验证通过后自动导入谱架并打开。
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
    // 人机验证自动重试：成功页是瞬时的（通过后页面会自动重载出新挑战），
    // 轮询 WebView 内容捕获成功瞬间后延时自动续传，最多自动重试 2 次防死循环
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var verifySuccess by remember { mutableStateOf(false) }
    var verifyAutoRetries by remember { mutableStateOf(0) }

    fun goBack() {
        downloadJob?.cancel()
        busy = false
        statusText = ""
        step = when (val s = step) {
            is ImslpStep.Search -> ImslpStep.Search
            is ImslpStep.Results -> ImslpStep.Search
            is ImslpStep.ComposerWorks -> lastResults ?: ImslpStep.Search
            is ImslpStep.Detail -> lastResults ?: ImslpStep.Search
            is ImslpStep.Downloading -> ImslpStep.Detail(s.detail)
            is ImslpStep.Verify -> ImslpStep.Detail(s.detail)
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

    fun openComposer(composer: String) {
        if (busy) return
        busy = true
        statusText = "加载作曲家作品…"
        scope.launch {
            val works = repo.composerWorks(composer)
            busy = false
            statusText = if (works.isEmpty()) "该作曲家分类下暂无作品条目" else ""
            step = ImslpStep.ComposerWorks(composer, works)
        }
    }

    fun openDetail(title: String) {
        if (busy) return
        busy = true
        statusText = "加载作品页…"
        scope.launch {
            val d = repo.workDetail(title)
            busy = false
            when {
                d == null -> statusText = "作品页加载失败，请重试"
                d.pdfs.isEmpty() -> statusText = "该页面未找到可下载的 PDF"
                else -> step = ImslpStep.Detail(d)
            }
        }
    }

    fun launchDownload(detail: ImslpWorkDetail, pdf: ImslpPdfFile, cookies: Map<String, String> = emptyMap()) {
        if (busy) return
        val dest = fileRepo.docsDirFile(pdf.filename)
        busy = true
        statusText = ""
        if (cookies.isEmpty()) {
            // 全新下载尝试：重置验证自动重试计数
            verifyAutoRetries = 0
            verifySuccess = false
        }
        downloadJob = scope.launch {
            when (val r = repo.downloadPdf(pdf.filename, dest, extraCookies = cookies) { pct ->
                step = ImslpStep.Downloading(detail, pdf.filename, pct)
            }) {
                is ImslpRepository.DownloadResult.Success -> {
                    busy = false
                    onImported(Uri.fromFile(dest))
                }
                is ImslpRepository.DownloadResult.BotCheck -> {
                    busy = false
                    statusText = "IMSLP 需要人工验证，请在下方完成检查后重试"
                    step = ImslpStep.Verify(detail, pdf, dest)
                }
                is ImslpRepository.DownloadResult.Error -> {
                    busy = false
                    statusText = "下载失败：${r.message}"
                    step = ImslpStep.Detail(detail)
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
                        "搜索 IMSLP 公有领域乐谱，支持曲名与作曲家；\n下载前可能需要在弹窗内完成 IMSLP 的人机验证。",
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
                                    .clickable { openComposer(c.title) }
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
                                    .clickable { openDetail(w.title) }
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

                is ImslpStep.ComposerWorks -> {
                    Text("${s.composer} 的作品 (${s.works.size})", color = text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(s.works, key = { it.pageid.toString() + it.title }) { w ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(itemBg)
                                    .border(1.dp, itemBorder, RoundedCornerShape(10.dp))
                                    .clickable { openDetail(w.title) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(w.title, color = text, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                is ImslpStep.Detail -> {
                    Text(s.detail.title, color = text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (s.detail.composer.isNotEmpty()) {
                        Text(s.detail.composer, color = muted, fontSize = 12.sp)
                    }
                    if (s.detail.info.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(itemBg)
                                .border(1.dp, itemBorder, RoundedCornerShape(10.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            s.detail.info.forEach { (k, v) ->
                                Row {
                                    Text("$k：", color = muted, fontSize = 11.sp)
                                    Text(v, color = text, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("可下载版本 (${s.detail.pdfs.size})", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(s.detail.pdfs, key = { it.filename }) { pdf ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(itemBg)
                                    .border(1.dp, itemBorder, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    pdfDisplayName(pdf.filename),
                                    color = text, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier
                                        .clip(ButtonShape)
                                        .background(accent)
                                        .clickable { launchDownload(s.detail, pdf) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("下载", color = onAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                is ImslpStep.Downloading -> {
                    Spacer(Modifier.height(8.dp))
                    Text("正在下载：${pdfDisplayName(s.name)}", color = text, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { s.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = accent
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("${s.progress}%", color = muted, fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("下载完成后将自动导入谱架并打开", color = muted, fontSize = 11.sp)
                }

                is ImslpStep.Verify -> {
                    Text("需要人工验证", color = text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "IMSLP 对自动下载有人机验证保护。请在下方页面完成\"Start Verification\"验证（出现 Bot Check Passed 即成功），成功后将自动继续下载。",
                        color = muted, fontSize = 11.sp, lineHeight = 15.sp
                    )
                    Spacer(Modifier.height(6.dp))
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
                                    // 环境与真实浏览器统一：UA 与下载请求一致（验证放行 cookie 绑定 UA+IP）、
                                    // 原生验证组件（mtcaptcha）所需能力全部开启
                                    settings.userAgentString = ImslpRepository.USER_AGENT
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = true
                                    settings.allowContentAccess = true
                                    settings.loadsImagesAutomatically = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    webViewClient = WebViewClient()
                                    // 弹窗类验证组件可能在新建窗口打开：统一接管到本 WebView
                                    webChromeClient = object : android.webkit.WebChromeClient() {
                                        override fun onCreateWindow(
                                            view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message
                                        ): Boolean {
                                            val transport = resultMsg.obj as android.webkit.WebView.WebViewTransport
                                            transport.webView = view
                                            resultMsg.sendToTarget()
                                            return true
                                        }
                                    }
                                    // 直接加载触发门禁的直链（挑战页带 Start Verification 按钮）
                                    loadUrl(repo.directUrl(s.pdf.filename))
                                    webViewRef = this
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    // 验证生效检测：每 3 秒用当前 WebView 会话 cookie 探测直链是否已放行
                    //（成功文案可能在 mtcaptcha 的 iframe 内且成功页为瞬态，顶层页面文本轮询不可靠；探测结果为唯一真值）
                    LaunchedEffect(s.pdf.filename) {
                        while (isActive) {
                            delay(3000)
                            if (!verifySuccess && repo.probeVerified(s.pdf.filename, repo.verifyCookies())) {
                                verifySuccess = true
                                statusText = "验证已生效，正在继续下载…"
                                launchDownload(s.detail, s.pdf, repo.verifyCookies())
                            }
                        }
                    }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(ButtonShape)
                            .background(accent)
                            .clickable {
                                val cookies = repo.verifyCookies()
                                launchDownload(s.detail, s.pdf, cookies)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("我已完成验证，重试下载", color = onAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
