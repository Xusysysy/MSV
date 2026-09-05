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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
sealed interface ImslpStep {
    data object Search : ImslpStep
    data class Results(val works: List<ImslpSearchResult>, val composers: List<ImslpSearchResult>) : ImslpStep
    data class Browse(val url: String) : ImslpStep
}

/** 展示名：去掉 PMLP 编号前缀 */
private fun pdfDisplayName(filename: String): String =
    filename.replace(Regex("^PMLP\\d+[-_]?"), "").replace('_', ' ')

/**
 * IMSLP 弹窗状态 holder：由 ViewerViewModel 持有（存活至应用进程结束 = 冷启动才重置）。
 * 误触弹窗边缘关闭后重开、屏幕旋转，状态与浏览位置均不丢失：
 * - currentBrowseUrl 由 onPageFinished 实时记录，重开时 WebView 重新加载最后浏览页（会话 cookie 全局保留）
 */
class ImslpDialogState {
    var showDialog by mutableStateOf(false)
    var step by mutableStateOf<ImslpStep>(ImslpStep.Search)
    var lastResults by mutableStateOf<ImslpStep.Results?>(null)
    var query by mutableStateOf("")
    var busy by mutableStateOf(false)
    var statusText by mutableStateOf("")
    var downloadJob: Job? = null
    var webViewRef: WebView? = null
    // 应用内下载状态：downloadingUrl 非空 = 下载进行中；gateWait = 门禁页已加载待用户验证
    var downloadingUrl by mutableStateOf<String?>(null)
    var downloadProgress by mutableStateOf(0)
    var gateWait by mutableStateOf(false)
    var gatePassed by mutableStateOf(false)
    var gateFileUrl by mutableStateOf<String?>(null)
    var gateRetry by mutableStateOf(0)
    // 官方页加载进度（0=刚开始/未加载，100=完成；1~99 期间显示进度条）
    var pageProgress by mutableStateOf(100)
    // 最后浏览的官方页 URL（onPageFinished 实时更新；重开弹窗据此恢复）
    var currentBrowseUrl by mutableStateOf<String?>(null)
    // Results 步是否来自 Browse 内搜索（返回时回浏览页而非搜索页）
    var resultsFromBrowse by mutableStateOf(false)
    // 搜索历史（持久化于 DataStore，由 ViewModel 收集写入）
    var searchHistory by mutableStateOf<List<String>>(emptyList())
}

/**
 * IMSLP 页面注入脚本（onPageFinished 执行，幂等）：
 * ① 蜜罐守卫：门禁页隐藏 .pld 输入框被输入法/自动填充写入会中止 mtcaptcha 加载——持续清空；
 * ② 等待跳过：等待弹窗/页 #sm_dl_wait 的 data-id 即真实下载地址（倒计时纯前端），读出立即导航
 *    （被 shouldOverrideUrlLoading 拦截为应用内下载，12~15 秒等待完全跳过），并隐藏站点倒计时弹窗；
 * ③ View 预览兜底：官方预览组件脚本托管于 pchnote.appspot.com（部分网络不可达），点击 View 2 秒后
 *    仍未渲染时，用可达域 www.peachnote.com 的图片接口自绘可翻页预览（官方组件渲染出来则自动移除）。
 */
private const val IMSLP_PAGE_JS = """(function(){
    if (window.__msvInjected) return;
    window.__msvInjected = 1;
    document.addEventListener('click', function(e){
        var el = e.target;
        while (el && el !== document.body) {
            var txt = el.textContent ? el.textContent.trim() : '';
            if (txt.indexOf('View') === 0 && txt.length < 12) {
                var entry = el.closest ? el.closest('.we_file, .we_file_first') : null;
                if (entry) { entry.__msvViewClicked = Date.now(); }
                break;
            }
            el = el.parentNode;
        }
    }, true);
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
        var entries = document.querySelectorAll('.we_file, .we_file_first');
        for (var k = 0; k < entries.length; k++) {
            (function(entry){
                var t = entry.__msvViewClicked;
                if (!t) return;
                var dt = Date.now() - t;
                if (dt < 2000) return;
                if (dt > 20000) {
                    entry.__msvViewClicked = 0;
                    var stale = entry.querySelector('.msv-view-fallback');
                    if (stale) stale.remove();
                    return;
                }
                var pn = document.getElementById('peachnoteScoreViewerRoot');
                var nv = document.getElementById('nativePDFViewer');
                var pnOk = pn && entry.contains(pn) && pn.offsetHeight > 0;
                var nvOk = nv && entry.contains(nv) && nv.offsetHeight > 0;
                var fb = entry.querySelector('.msv-view-fallback');
                if (pnOk || nvOk) { if (fb) fb.remove(); return; }
                if (fb) return;
                var bt = entry.querySelector('[id^="fileButton_"]');
                if (!bt) return;
                var fid = bt.id.replace('fileButton_', '');
                if (!/^\d+$/.test(fid)) return;
                var wImg = Math.max(300, Math.min((entry.clientWidth || 700) - 24, 1400));
                var base = 'https://www.peachnote.com/rest/api/v1/image?sid=IMSLP' + fid + '&w=' + wImg + '&page=';
                var panel = document.createElement('div');
                panel.className = 'msv-view-fallback';
                panel.style.cssText = 'margin:10px auto;padding:6px;border:1px solid #bbb;background:#fafafa;text-align:center;';
                var img = document.createElement('img');
                img.style.cssText = 'width:100%;height:auto;display:block;background:#fff;';
                var page = 1;
                var info = document.createElement('span');
                info.style.cssText = 'margin:0 8px;font-size:13px;color:#333;';
                var mkBtn = function(txt){
                    var b = document.createElement('button');
                    b.textContent = txt;
                    b.style.cssText = 'margin:0 4px;padding:4px 12px;border:1px solid #888;border-radius:4px;background:#fff;cursor:pointer;font-size:13px;';
                    return b;
                };
                var prev = mkBtn('上一页'), next = mkBtn('下一页'), close = mkBtn('关闭预览');
                var loadPage = function(p){
                    page = p;
                    info.textContent = '第 ' + page + ' 页';
                    img.onerror = function(){
                        if (page > 1) { loadPage(page - 1); info.textContent = info.textContent + '（已到末页）'; }
                        else { info.textContent = '预览暂不可用'; }
                    };
                    img.src = base + page;
                };
                prev.onclick = function(){ if (page > 1) loadPage(page - 1); };
                next.onclick = function(){ loadPage(page + 1); };
                close.onclick = function(){ panel.remove(); entry.__msvViewClicked = 0; };
                panel.appendChild(img);
                var bar = document.createElement('div');
                bar.style.cssText = 'margin-top:6px;';
                bar.appendChild(prev); bar.appendChild(info); bar.appendChild(next); bar.appendChild(close);
                panel.appendChild(bar);
                entry.appendChild(panel);
                loadPage(1);
            })(entries[k]);
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
 * IMSLP 乐谱搜索/下载弹窗（状态由 ImslpDialogState 承载，跨弹窗开关/旋转保持，冷启动重置）。
 * 弹窗尺寸响应式：搜索步小窗（搜索框+历史），结果步按结果数分档，浏览步保持 0.94×0.88。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ImslpDialog(
    state: ImslpDialogState,
    isDark: Boolean,
    onImported: (Uri) -> Unit,
    onSearchCommit: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
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
    var browseQuery by remember { mutableStateOf("") }
    var browseSearchExpanded by remember { mutableStateOf(false) }

    // 响应式尺寸：搜索步小窗，结果步按结果数分档，浏览步保持 0.94×0.88（带过渡动画）
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val targetW = if (state.step is ImslpStep.Search) screenW * 0.70f else screenW * 0.94f
    // 状态行（搜索中…/加载中…）动态计入小窗高度，避免出现时把内容推出弹窗外
    val statusH = if (state.statusText.isNotEmpty()) 24.dp else 0.dp
    val targetH = when (val s = state.step) {
        is ImslpStep.Search -> {
            // 标题+返回+搜索框 ≈200dp，随历史条数增高，封顶 0.5 屏高
            val historyH = 30.dp * state.searchHistory.size
            minOf(screenH * 0.5f, 200.dp + statusH + historyH)
        }
        is ImslpStep.Results -> {
            val n = s.works.size + s.composers.size
            when {
                n <= 2 -> screenH * 0.35f
                n <= 8 -> screenH * 0.60f
                else -> screenH * 0.88f
            }
        }
        is ImslpStep.Browse -> screenH * 0.88f
    }
    val dialogW by animateDpAsState(targetW, tween(300, easing = FastOutSlowInEasing), label = "imslpW")
    val dialogH by animateDpAsState(targetH, tween(300, easing = FastOutSlowInEasing), label = "imslpH")

    fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun goBack() {
        state.downloadJob?.cancel()
        if (state.downloadingUrl != null || state.gateWait) {
            state.downloadingUrl = null
            state.gateWait = false
            state.gatePassed = false
        }
        state.statusText = ""
        state.step = when (val s = state.step) {
            is ImslpStep.Search -> ImslpStep.Search
            is ImslpStep.Results -> {
                if (state.resultsFromBrowse) {
                    state.resultsFromBrowse = false
                    val url = state.currentBrowseUrl
                    if (url != null) ImslpStep.Browse(url) else ImslpStep.Search
                } else {
                    ImslpStep.Search
                }
            }
            is ImslpStep.Browse -> {
                val wv = state.webViewRef
                if (wv != null && wv.canGoBack()) {
                    wv.goBack()
                    s
                } else {
                    state.resultsFromBrowse = false
                    state.lastResults ?: ImslpStep.Search
                }
            }
        }
    }

    fun doSearch(q: String, fromBrowse: Boolean = false) {
        if (q.isBlank() || state.busy) return
        state.busy = true
        state.statusText = "搜索中…"
        scope.launch {
            val (works, composers) = repo.search(q.trim())
            state.busy = false
            val rs = ImslpStep.Results(works, composers)
            state.lastResults = rs
            state.statusText = if (works.isEmpty() && composers.isEmpty()) "未找到相关结果，试试作曲家姓氏" else ""
            state.resultsFromBrowse = fromBrowse
            state.step = rs
            if (works.isNotEmpty() || composers.isNotEmpty()) onSearchCommit(q.trim())
        }
    }

    fun openComposer(c: ImslpSearchResult) {
        state.currentBrowseUrl = "https://imslp.org/wiki/Category:" + urlEncode(c.title)
        state.statusText = ""
        state.step = ImslpStep.Browse(state.currentBrowseUrl!!)
    }

    fun openWork(w: ImslpSearchResult) {
        state.currentBrowseUrl = "https://imslp.org/wiki/" + urlEncode(w.title)
        state.statusText = ""
        state.step = ImslpStep.Browse(state.currentBrowseUrl!!)
    }

    /** 应用内下载官方页拦截到的 PDF 直链；isAutoRetry=true 为门禁验证通过后的自动重试（不重置重试计数） */
    fun startInterceptedDownload(fileUrl: String, ua: String, isAutoRetry: Boolean = false) {
        if (state.downloadingUrl != null) return
        val name = runCatching { URLDecoder.decode(fileUrl.substringAfterLast('/'), "UTF-8") }
            .getOrElse { fileUrl.substringAfterLast('/') }
            .substringBefore('?').substringBefore('#')
        if (!name.endsWith(".pdf", ignoreCase = true)) return
        val dest = fileRepo.docsDirFile(name)
        state.downloadingUrl = fileUrl
        state.downloadProgress = 0
        state.gateWait = false
        state.gatePassed = false
        if (!isAutoRetry) state.gateRetry = 0
        state.statusText = "正在下载：${pdfDisplayName(name)}（返回可取消）"
        state.downloadJob = scope.launch {
            when (val r = repo.downloadUrl(fileUrl, ua, dest) { pct -> state.downloadProgress = pct }) {
                is ImslpRepository.DownloadResult.Success -> {
                    state.downloadingUrl = null
                    onImported(Uri.fromFile(dest))
                }
                is ImslpRepository.DownloadResult.BotCheck -> {
                    state.downloadingUrl = null
                    state.gateFileUrl = fileUrl
                    state.gateWait = true
                    state.statusText = "IMSLP 需要人工验证，请在页面中完成 \"Start Verification\"，通过后将自动继续下载"
                    state.webViewRef?.loadUrl(fileUrl) // 门禁页（mtcaptcha）在官方 WebView 上下文中渲染
                }
                is ImslpRepository.DownloadResult.Error -> {
                    state.downloadingUrl = null
                    state.statusText = "下载失败：${r.message}"
                }
            }
        }
    }

    Dialog(onDismissRequest = { state.showDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier
                .width(dialogW)
                .height(dialogH)
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
                        .clickable { state.showDialog = false }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(8.dp))

            // 返回行（非首步显示）
            if (state.step !is ImslpStep.Search) {
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
            if (state.statusText.isNotEmpty()) {
                Text(state.statusText, color = muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
            }

            when (val s = state.step) {
                is ImslpStep.Search -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = { state.query = it },
                            placeholder = { Text("搜索作品或作曲家…", fontSize = 13.sp, color = muted) },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { doSearch(state.query) }),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = text),
                            modifier = Modifier.weight(1f).height(50.dp)
                        )
                        Box(
                            Modifier
                                .clip(ButtonShape)
                                .background(accent)
                                .clickable { doSearch(state.query) }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text("搜索", color = onAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // 搜索历史（持久化；点击直接搜索）
                    if (state.searchHistory.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("最近搜索", color = muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "清空", color = accent, fontSize = 11.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onClearHistory() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Column(
                            Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            state.searchHistory.forEach { h ->
                                Text(
                                    h, color = text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            state.query = h
                                            doSearch(h)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }

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
                    // WebView 顶部搜索框：默认收起为小胶囊（不占空间），点击展开输入行（过渡动画）；
                    // 提交后切回应用内结果网格（返回时回到浏览页）
                    val focusRequester = remember { FocusRequester() }
                    AnimatedVisibility(
                        visible = !browseSearchExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Row(Modifier.fillMaxWidth()) {
                            Box(
                                Modifier
                                    .clip(ButtonShape)
                                    .background(itemBg)
                                    .border(1.dp, itemBorder, ButtonShape)
                                    .clickable { browseSearchExpanded = true }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("🔍 在 IMSLP 搜索…", color = muted, fontSize = 12.sp)
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = browseSearchExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = browseQuery,
                                onValueChange = { browseQuery = it },
                                placeholder = { Text("在 IMSLP 搜索…", fontSize = 12.sp, color = muted) },
                                singleLine = true,
                                shape = RoundedCornerShape(20.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    browseSearchExpanded = false
                                    doSearch(browseQuery, fromBrowse = true)
                                }),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = text),
                                modifier = Modifier.weight(1f).height(50.dp).focusRequester(focusRequester)
                            )
                            Box(
                                Modifier
                                    .clip(ButtonShape)
                                    .background(accent)
                                    .clickable {
                                        browseSearchExpanded = false
                                        doSearch(browseQuery, fromBrowse = true)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("搜索", color = onAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    LaunchedEffect(browseSearchExpanded) {
                        if (browseSearchExpanded) focusRequester.requestFocus()
                    }
                    Spacer(Modifier.height(6.dp))

                    // 页面加载进度（官方页资源较重，给出即时反馈）
                    if (state.pageProgress in 1..99) {
                        LinearProgressIndicator(
                            progress = { state.pageProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = accent
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    // 应用内下载进度（保持官方页挂载：验证/重试不丢浏览上下文）
                    if (state.downloadingUrl != null) {
                        LinearProgressIndicator(
                            progress = { state.downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = accent
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("${state.downloadProgress}% · 下载完成后自动导入谱架并打开", color = muted, fontSize = 11.sp)
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
                                            state.pageProgress = 0
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
                                            state.currentBrowseUrl = url // 实时记录：误触关闭/旋转后重开恢复到最后浏览页
                                            view.evaluateJavascript(IMSLP_PAGE_JS, null)
                                        }
                                    }
                                    // 弹窗类验证组件可能在新建窗口打开：统一接管到本 WebView
                                    webChromeClient = object : android.webkit.WebChromeClient() {
                                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                                            state.pageProgress = newProgress
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
                                    state.webViewRef = this
                                    // 弹窗重开恢复：优先最后浏览页（会话 cookie 全局保留，登录/验证状态不丢）
                                    loadUrl(state.currentBrowseUrl ?: s.url)
                                }
                            },
                            onRelease = { wv ->
                                if (state.webViewRef === wv) state.webViewRef = null
                                (wv.parent as? android.view.ViewGroup)?.removeAllViews()
                                wv.stopLoading()
                                wv.destroy()
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // 页面未加载完成时显示"加载中"覆盖层（含广告拦截/慢网场景），完成后自动消失
                        if (state.pageProgress in 0..99) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(if (isDark) Color(0xF00F121C) else Color(0xF2FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = accent)
                                    Spacer(Modifier.height(8.dp))
                                    Text("加载中…", color = muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // 门禁页轮询：用户完成 mtcaptcha 后页面出现 "Bot Check Passed" → 自动重试下载
                    LaunchedEffect(Unit) {
                        while (isActive) {
                            delay(1200)
                            if (!state.gateWait) continue
                            val wv = state.webViewRef ?: continue
                            wv.evaluateJavascript("(document.body?document.body.innerText:'')") { v ->
                                if (v != null && (v.contains("Bot Check Passed", ignoreCase = true) || v.contains("passed the bot test", ignoreCase = true))) {
                                    state.gatePassed = true
                                }
                            }
                        }
                    }
                    LaunchedEffect(state.gatePassed) {
                        if (!state.gatePassed) return@LaunchedEffect
                        state.gatePassed = false
                        val url = state.gateFileUrl ?: return@LaunchedEffect
                        state.gateWait = false
                        state.gateRetry++
                        if (state.gateRetry > 2) {
                            state.statusText = "验证已通过但下载仍被拦截，请在官方页面稍后重试"
                            state.gateFileUrl = null
                            return@LaunchedEffect
                        }
                        val ua = state.webViewRef?.settings?.userAgentString ?: ImslpRepository.USER_AGENT
                        state.statusText = "验证已通过，正在继续下载…"
                        startInterceptedDownload(url, ua, isAutoRetry = true)
                    }
                }
            }
        }
    }
}
