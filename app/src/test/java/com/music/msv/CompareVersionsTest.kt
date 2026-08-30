package com.music.msv

import com.music.msv.data.repository.compareVersions
import org.junit.Assert.assertEquals
import org.junit.Test

class CompareVersionsTest {

    @Test
    fun test_newer_patch_segment_returns_negative() {
        assertEquals(-1, compareVersions("2.0", "2.0.1").coerceIn(-1, 1))
    }

    @Test
    fun test_equal_versions_return_zero() {
        assertEquals(0, compareVersions("2.0", "2.0"))
    }

    @Test
    fun test_numeric_comparison_not_lexicographic() {
        // 2.10 > 2.2，字符串比较会得出错误结论
        assertEquals(-1, compareVersions("2.2", "2.10").coerceIn(-1, 1))
    }

    @Test
    fun test_v_prefix_is_tolerated() {
        assertEquals(0, compareVersions("v2.1", "2.1.0"))
    }

    @Test
    fun test_older_remote_does_not_trigger_update() {
        assertEquals(1, compareVersions("2.0", "1.9").coerceIn(-1, 1))
    }

    @Test
    fun test_zero_padding_equal() {
        assertEquals(0, compareVersions("2.0.0", "2.0"))
    }

    @Test
    fun test_major_bump_detected() {
        assertEquals(-1, compareVersions("2.1", "3.0").coerceIn(-1, 1))
    }
}
