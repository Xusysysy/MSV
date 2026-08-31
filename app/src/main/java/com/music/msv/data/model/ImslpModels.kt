package com.music.msv.data.model

/** IMSLP 搜索结果条目（作品页或作曲家分类） */
data class ImslpSearchResult(
    val title: String,
    val pageid: Long,
    val isComposer: Boolean,
    val snippet: String
)

/** IMSLP 作品页内一个可下载的 PDF 版本 */
data class ImslpPdfFile(
    val filename: String
)

/** IMSLP 作品页详情：曲名/作曲家/介绍字段/可下载版本列表 */
data class ImslpWorkDetail(
    val title: String,
    val composer: String,
    val info: List<Pair<String, String>>,
    val pdfs: List<ImslpPdfFile>
)
