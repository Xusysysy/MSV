package com.music.msv.facer

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun FaceRecognitionOverlay(visible: Boolean, onDismiss: () -> Unit, onToggle: (Boolean) -> Unit, manager: FaceRecognitionManager, isDark: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    val ctx = LocalContext.current; val cfg = LocalConfiguration.current; val isLand = cfg.screenWidthDp > cfg.screenHeightDp
    var hasPerm by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val pL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPerm = it; if (!it) Toast.makeText(ctx, "需要相机权限", Toast.LENGTH_SHORT).show() }
    LaunchedEffect(visible) { if (visible && !hasPerm) pL.launch(Manifest.permission.CAMERA) }
    val state by manager.stateFlow.collectAsState()

    val bg = if (isDark) Color(0xF0121628) else Color(0xF8FFFFFF); val card = if (isDark) Color(0xFF1A1E2E) else Color(0x141A2230)
    val b = if (isDark) Color(0x24FFFFFF) else Color(0x1A1A2230); val t = if (isDark) Color.White else Color(0xFF1B2230)
    val t2 = if (isDark) Color(0xCCFFFFFF) else Color(0xCC1B2230); val ac = if (isDark) Color(0xFF8CC8FF) else Color(0xFF2F6AD9)
    val dn = if (isDark) Color(0xFFFF9AA8) else Color(0xFFD9455D); val gr = Color(0xFF4ADE80)
    val sc = state.scores

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }, contentAlignment = Alignment.Center) {
        val maxH = cfg.screenHeightDp.dp * 0.9f
        if (isLand) Row(Modifier.fillMaxWidth(0.92f).heightIn(max = maxH).clip(RoundedCornerShape(16.dp)).background(bg).border(1.dp, b, RoundedCornerShape(16.dp)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}.padding(10.dp), Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), Arrangement.spacedBy(6.dp)) {
                Header(state, manager, gr, card, b, t, t2, onToggle)
                Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)).border(1.dp, b, RoundedCornerShape(12.dp)).background(Color(0xFF0A0D18)))
                AR(sc, ac, dn, card, b, t)
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), Arrangement.spacedBy(6.dp)) {
                Modes(state, manager, card, b, ac)
                SliderCard("眨眼阈值", state.thresholds.blink, 0.10f..0.95f, card, b, t, ac) { manager.updateState { s -> s.copy(thresholds = s.thresholds.copy(blink = it)) } }
                SliderCard("撅嘴阈值", state.thresholds.pucker, 0.05f..0.90f, card, b, t, ac) { manager.updateState { s -> s.copy(thresholds = s.thresholds.copy(pucker = it)) } }
                SliderCard("翻谱阈值", state.actionThreshold, 0.05f..0.95f, card, b, t, ac) { manager.updateState { s -> s.copy(actionThreshold = it) } }
                SliderCard("左撅嘴偏置", state.thresholds.puckerBiasL, 0.02f..0.60f, card, b, t, ac) { manager.updateState { s -> s.copy(thresholds = s.thresholds.copy(puckerBiasL = it)) } }
                SliderCard("右撅嘴偏置", state.thresholds.puckerBiasR, 0.02f..0.60f, card, b, t, ac) { manager.updateState { s -> s.copy(thresholds = s.thresholds.copy(puckerBiasR = it)) } }
                MirrorToggle(state, manager, card, b, t, t2, ac)
                Debug(manager, hasPerm, state, card, b, t, t2, ac)
            }
        }
        else Column(Modifier.fillMaxWidth(0.9f).heightIn(max = maxH).clip(RoundedCornerShape(16.dp)).background(bg).border(1.dp, b, RoundedCornerShape(16.dp)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}.verticalScroll(rememberScrollState()).padding(12.dp), Arrangement.spacedBy(8.dp)) {
            Header(state, manager, gr, card, b, t, t2, onToggle)
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)).border(1.dp, b, RoundedCornerShape(12.dp)).background(Color(0xFF0A0D18)))
            AR(sc, ac, dn, card, b, t)
            Modes(state, manager, card, b, ac)
            SliderCard("眨眼阈值", state.thresholds.blink, 0.10f..0.95f, card, b, t, ac) { manager.updateState { s -> s.copy(thresholds = s.thresholds.copy(blink = it)) } }
            SliderCard("撅嘴阈值", state.thresholds.pucker, 0.05f..0.90f, card, b, t, ac) { manager.updateState { s -> s.copy(thresholds = s.thresholds.copy(pucker = it)) } }
            SliderCard("翻谱阈值", state.actionThreshold, 0.05f..0.95f, card, b, t, ac) { manager.updateState { s -> s.copy(actionThreshold = it) } }
            SliderCard("左撅嘴偏置", state.thresholds.puckerBiasL, 0.02f..0.60f, card, b, t, ac) { manager.updateState { s -> s.copy(thresholds = s.thresholds.copy(puckerBiasL = it)) } }
            SliderCard("右撅嘴偏置", state.thresholds.puckerBiasR, 0.02f..0.60f, card, b, t, ac) { manager.updateState { s -> s.copy(thresholds = s.thresholds.copy(puckerBiasR = it)) } }
            MirrorToggle(state, manager, card, b, t, t2, ac)
            Debug(manager, hasPerm, state, card, b, t, t2, ac)
        }
    }
}

@Composable private fun Header(state: FaceRecognitionManager.FaceState, manager: FaceRecognitionManager, gr: Color, card: Color, b: Color, t: Color, t2: Color, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text("面部识别", color = t, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (state.running) gr.copy(alpha = 0.2f) else card).border(1.dp, if (state.running) gr else b, RoundedCornerShape(8.dp)).clickable {
            val nr = !state.running
            if (nr) { if (!manager.isReady()) manager.init(); manager.updateState { it.copy(running = true, enabled = true) } }
            else { manager.updateState { it.copy(running = false, enabled = false) } }
            onToggle(nr)
        }.padding(horizontal = 10.dp, vertical = 4.dp)) { Text(if (state.running) "● LIVE ${state.fps}fps" else "○ OFF", color = if (state.running) gr else t2, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun AR(sc: FaceRecognitionManager.GestureScores, ac: Color, dn: Color, card: Color, b: Color, t: Color) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
        Card(Modifier.weight(1f), "😜", "左Wink", sc.lWink, ac, card, b, t); Card(Modifier.weight(1f), "😉", "右Wink", sc.rWink, ac, card, b, t)
        Card(Modifier.weight(1f), "😗", "左撅嘴", sc.lPucker, dn, card, b, t); Card(Modifier.weight(1f), "😙", "右撅嘴", sc.rPucker, dn, card, b, t)
    }
}

@Composable private fun Card(mod: Modifier, e: String, l: String, score: Float, ac: Color, c: Color, b: Color, t: Color) {
    val on = score > 0.01f
    Column(mod.clip(RoundedCornerShape(10.dp)).background(if (on) ac.copy(alpha = 0.15f) else c).border(1.dp, if (on) ac.copy(alpha = 0.5f) else b, RoundedCornerShape(10.dp)).padding(vertical = 6.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(e, fontSize = 16.sp); Spacer(Modifier.height(1.dp)); Text(l, color = t, fontSize = 9.sp); Text("${(score * 100).toInt()}%", color = if (on) ac else Color(0xFF888888), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun Modes(state: FaceRecognitionManager.FaceState, manager: FaceRecognitionManager, card: Color, b: Color, ac: Color) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(card).border(1.dp, b, RoundedCornerShape(10.dp)).padding(8.dp), Arrangement.spacedBy(4.dp)) {
        Text("触发模式", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
            MBtn(Modifier.weight(1f), "Wink", state.triggerMode == FaceRecognitionManager.TriggerMode.WINK) { if (!manager.isReady()) manager.init(); manager.updateState { it.copy(enabled = true, running = true, triggerMode = FaceRecognitionManager.TriggerMode.WINK) } }
            MBtn(Modifier.weight(1f), "撅嘴", state.triggerMode == FaceRecognitionManager.TriggerMode.PUCKER) { if (!manager.isReady()) manager.init(); manager.updateState { it.copy(enabled = true, running = true, triggerMode = FaceRecognitionManager.TriggerMode.PUCKER) } }
            MBtn(Modifier.weight(1f), "两者", state.triggerMode == FaceRecognitionManager.TriggerMode.BOTH) { if (!manager.isReady()) manager.init(); manager.updateState { it.copy(enabled = true, running = true, triggerMode = FaceRecognitionManager.TriggerMode.BOTH) } }
        }
    }
}

@Composable private fun SliderCard(label: String, value: Float, range: ClosedFloatingPointRange<Float>, card: Color, b: Color, t: Color, ac: Color, onChange: (Float) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(card).border(1.dp, b, RoundedCornerShape(10.dp)).padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(label, color = t, fontSize = 11.sp); Text(String.format("%.1f", value), color = ac, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        Slider(value = value, onValueChange = { v = it; onChange(it) }, valueRange = range, modifier = Modifier.fillMaxWidth(), colors = SliderDefaults.colors(thumbColor = ac, activeTrackColor = ac, inactiveTrackColor = b))
    }
}

@Composable private fun MirrorToggle(state: FaceRecognitionManager.FaceState, manager: FaceRecognitionManager, card: Color, b: Color, t: Color, t2: Color, ac: Color) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(card).border(1.dp, b, RoundedCornerShape(10.dp)).padding(8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text("镜像翻转", color = t, fontSize = 11.sp)
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (state.mirrored) ac.copy(alpha = 0.2f) else card).border(1.dp, if (state.mirrored) ac else b, RoundedCornerShape(8.dp)).clickable { manager.updateState { it.copy(mirrored = !it.mirrored) } }.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(if (state.mirrored) "镜像" else "原始", color = if (state.mirrored) ac else t2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun Debug(manager: FaceRecognitionManager, hasPerm: Boolean, state: FaceRecognitionManager.FaceState, card: Color, b: Color, t: Color, t2: Color, ac: Color) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(card).border(1.dp, b, RoundedCornerShape(10.dp)).padding(8.dp)) {
        Text("诊断", color = t, fontSize = 11.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp))
        Text("模型:${if (manager.isReady()) "✓" else "✗"} 相机:${if (hasPerm) "✓" else "✗"} 运行:${if (state.running) "✓" else "○"} FPS:${state.fps} 关键点:${state.landmarks?.size ?: 0}", color = t2, fontSize = 10.sp)
        Text(state.status, color = ac, fontSize = 10.sp)
    }
}

@Composable private fun MBtn(mod: Modifier, text: String, sel: Boolean, onClick: () -> Unit) {
    Box(mod.clip(RoundedCornerShape(8.dp)).background(if (sel) Color(0xFF8CC8FF).copy(alpha = 0.2f) else Color(0xFF1A1E2E)).border(1.dp, if (sel) Color(0xFF8CC8FF) else Color(0x28FFFFFF), RoundedCornerShape(8.dp)).clickable { onClick() }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (sel) Color(0xFF8CC8FF) else Color(0xFF888888), fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
    }
}
