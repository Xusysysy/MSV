package com.music.msv.ui.components

import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.music.msv.data.model.ImslpSearchResult
import kotlinx.coroutines.Job

/** 步骤状态机：搜索 → 结果 → 官方页浏览（浏览中内嵌应用内下载与人工验证） */
sealed interface ImslpStep {
    data object Search : ImslpStep
    data class Results(val works: List<ImslpSearchResult>, val composers: List<ImslpSearchResult>) : ImslpStep
    data class Browse(val url: String) : ImslpStep
}

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
    // 官方页主文档加载失败描述（null=正常；显示错误覆盖层 + 重试）
    var pageError by mutableStateOf<String?>(null)
    // 最后浏览的官方页 URL（onPageFinished 实时更新；重开弹窗据此恢复）
    var currentBrowseUrl by mutableStateOf<String?>(null)
    // Results 步是否来自 Browse 内搜索（返回时回浏览页而非搜索页）
    var resultsFromBrowse by mutableStateOf(false)
    // 搜索历史（持久化于 DataStore，由 ViewModel 收集写入）
    var searchHistory by mutableStateOf<List<String>>(emptyList())
    // IMSLP 免责声明：首次使用（未确认）时弹窗强调版权法规；默认 true 避免确认状态回读前的闪烁
    var disclaimerAccepted by mutableStateOf(true)
    var showDisclaimer by mutableStateOf(false)
}
