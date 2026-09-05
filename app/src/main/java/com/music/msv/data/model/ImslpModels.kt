package com.music.msv.data.model

/** IMSLP 搜索结果条目（作品页或作曲家分类） */
data class ImslpSearchResult(
    val title: String,
    val pageid: Long,
    val isComposer: Boolean,
    val snippet: String
)
