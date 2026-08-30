# STRUCTURE.md — MSV Android

## Directory Map

```
app/src/main/java/com/music/msv/
├── MainActivity.kt              ← Entry point, edge-to-edge, Intent routing
├── ui/
│   ├── theme/
│   │   ├── Color.kt             ← Glassmorphism color tokens (dark + light)
│   │   ├── Theme.kt             ← MSVTheme composable
│   │   ├── Type.kt              ← Type scale
│   │   └── Shape.kt             ← Rounded shapes
│   ├── components/
│   │   ├── Stage.kt             ← Main viewport + gestures + page rendering
│   │   ├── TopBar.kt            ← Top control bar (shelf, page nav, thumbnails, face, reset, settings)
│   │   ├── Footer.kt            ← Status footer
│   │   ├── EmptyView.kt         ← Idle landing page with shelf button
│   │   ├── ThumbnailPanel.kt    ← Slide-in thumbnail grid (right)
│   │   ├── ShelfPanel.kt        ← Slide-in shelf panel (left), lists saved scores with thumbnails
│   │   ├── SettingsPanel.kt     ← Slide-in settings panel (right): version, copyright, check-update
│   │   └── LoadingOverlay.kt    ← Spinner overlay
│   └── screen/
│       └── ViewerScreen.kt      ← Root orchestrator, wires ViewModel → components
├── viewmodel/
│   └── ViewerViewModel.kt       ← Central state holder, MVVM, preload logic, face wiring, OTA update
├── data/
│   ├── model/
│   │   └── ViewerState.kt       ← UI state data class + Mode sealed class + ViewerEvent sealed class
│   ├── repository/
│   │   ├── FileRepository.kt    ← SAF file access, local copy, MIME detection
│   │   ├── SessionRepository.kt ← DataStore persistence (session + per-file page map)
│   │   └── UpdateRepository.kt  ← OTA: release query (Gitee+GitHub), version compare, APK download/install
│   └── pdf/
│       └── PdfPageRenderer.kt   ← Android PdfRenderer wrapper, LRU bitmap cache
├── facer/
│   ├── FaceCamera.kt            ← CameraX front-camera feed + landmark overlay canvas
│   ├── FaceLog.kt               ← Dual-write logger (logcat + msv_face_log.txt)
│   ├── FaceRecognitionManager.kt ← MediaPipe FaceLandmarker pipeline + gesture state machine
│   ├── FaceRecognitionOverlay.kt ← Face settings fullscreen overlay (portrait/landscape)
│   └── FaceRecognitionRepository.kt ← DataStore persistence of face prefs ("face_prefs")
```

## Key Decisions

| Decision | Choice | Reason |
|---|---|---|
| UI framework | Jetpack Compose + Material 3 | Project scaffold, modern, idiomatic |
| Architecture | MVVM (ViewModel + StateFlow) | Standard for Compose, lifecycle-safe |
| PDF rendering | Android PdfRenderer API | Built-in, hardware-accelerated, no extra dep |
| Image loading | Coil 3 Compose | Modern, Compose-native |
| Persistence | DataStore Preferences | Lightweight key-value, coroutine-native |
| File access | Storage Access Framework | Standard Android file picking |
| Animations | Compose Animation APIs | Sufficient for all required transitions |
| Face page-flip | MediaPipe FaceLandmarker 0.10.18 (IMAGE mode) + CameraX 1.3.4 | Wink/pucker gestures flip pages; model asset `app/src/main/assets/face_landmarker.task` |
| OTA update | HttpURLConnection + org.json + DownloadManager + FileProvider (zero third-party deps) | Gitee-priority release check, DownloadManager in-app download, FileProvider install; release APK uses debug signing (overwrite-install prerequisite); tag == versionName invariant |
| minSdk | 26 (Android 8.0) | User preference |

## Navigation

Single-screen app — no Navigation component. State-based content switching via `ViewerViewModel.uiState`.

---

## Per-File Breakdown

### 1. MainActivity.kt (L1-L52)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L18 |
| `MainActivity` | class : ComponentActivity | L20-L52 |
| `shareIntentState` | private val MutableState<Intent?> | L22 |
| `onCreate(savedInstanceState)` | override fun — edge-to-edge, status bar hidden, `FaceLog.init(this)` | L24-L45 |
| `onNewIntent(intent)` | override fun | L47-L51 |

---

### 2. ui/theme/Color.kt (L1-L71)

| Element | Type | Lines |
|---|---|---|
| Package + import | — | L1-L3 |
| **Dark theme constants** | val Color | L7-L37 |
| **Light theme constants** | val Color | L41-L71 |

31 dark constants (L7-L37): DarkAppBg, DarkShellBg, DarkText, DarkMuted, DarkAccent, DarkDanger, DarkLine, DarkControlBg, DarkControlBgHover, DarkControlBorder, DarkControlBorderHover, DarkDivider, DarkTopbarBg, DarkTopbarBorder, DarkStageBg, DarkOverlayBg, DarkFooterBg, DarkShadeBg, DarkPanelBg, DarkPanelBorder, DarkThumbnailItemBg, DarkThumbnailItemBorder, DarkThumbnailItemHoverBg, DarkThumbnailItemHoverBorder, DarkThumbnailItemActiveBg, DarkThumbnailItemActiveBorder, DarkThumbnailThumbBg, DarkSpinnerTrack, DarkSurfaceVariant, DarkOnSurface, DarkOnSurfaceVariant

31 light constants (L41-L71): LightAppBg ... LightOnSurfaceVariant

---

### 3. ui/theme/Theme.kt (L1-L73)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L15 |
| `DarkColorScheme` | private val darkColorScheme() | L17-L32 |
| `LightColorScheme` | private val lightColorScheme() | L34-L49 |
| `MSVTheme` | @Composable fun | L51-L73 |
| — `darkTheme: Boolean` | param (default: isSystemInDarkTheme) | L53 |
| — `forceDark: Boolean?` | param (default: null) | L54 |
| — `content: @Composable () -> Unit` | param | L55 |

---

### 4. ui/theme/Type.kt (L1-L52)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L7 |
| `AppTypography` | val Typography | L9-L52 |
| — bodyLarge | L10-L15 |
| — bodyMedium | L16-L21 |
| — bodySmall | L22-L27 |
| — labelSmall | L28-L33 |
| — titleLarge | L34-L39 |
| — titleMedium | L40-L45 |
| — titleSmall | L46-L51 |

---

### 5. ui/theme/Shape.kt (L1-L14)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L4 |
| `ShellShape` | val RoundedCornerShape(28.dp) | L6 |
| `ShellFullscreenShape` | val RoundedCornerShape(0.dp) | L7 |
| `TopbarShape` | val RoundedCornerShape(20.dp) | L8 |
| `ButtonShape` | val RoundedCornerShape(50) | L9 |
| `PageDisplayShape` | val RoundedCornerShape(50) | L10 |
| `PanelShape` | val RoundedCornerShape(22.dp, 0.dp, 22.dp, 0.dp) | L11 |
| `ThumbnailItemShape` | val RoundedCornerShape(12.dp) | L12 |
| `ThumbnailThumbShape` | val RoundedCornerShape(8.dp) | L13 |
| `FooterShape` | val RoundedCornerShape(50) | L14 |

---

### 6. ui/screen/ViewerScreen.kt (L1-L349)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L52 |
| `ViewerScreen(viewModel: ViewerViewModel)` | @Composable fun | L54-L349 |
| `state` | collectAsState | L56 |
| `filePickerLauncher` | rememberLauncherForActivityResult | L59-L65 |
| `openFilePicker` | local val lambda | L67-L69 |
| `showPageDialog` / `pageInput` / `showResetDialog` | mutableStateOf | L71-L73 |
| `isDark` / `appBg` | derived vals | L75-L76 |
| `isViewing` | derived val | L78 |
| Root Box | composable | L80-L348 |
| — Inner Box (shell/viewing conditional) | composable | L87-L268 |
| — — `Mode.Idle` → EmptyView (with onShelfClick) | composable | L101-L106 |
| — — `else` → Stage (+pendingFlip, onFlipDone) + LoadingOverlay | composable | L107-L135 |
| — — TopBar (AnimatedVisibility, slide+top, +onSettingsClick) | composable | L138-L166 |
| — — BottomFooter (AnimatedVisibility, slide+bottom) | composable | L168-L180 |
| — — Thumbnail backdrop click Box | composable | L182-L191 |
| — — ThumbnailPanel (AnimatedVisibility, slide+right) | composable | L193-L207 |
| — — Settings backdrop click Box | composable | L209-L218 |
| — — SettingsPanel (AnimatedVisibility, slide+right) | composable | L220-L238 |
| — — Shelf backdrop click Box | composable | L240-L249 |
| — — ShelfPanel (AnimatedVisibility, slide+left) | composable | L251-L267 |
| — Page jump AlertDialog | composable | L270-L298 |
| — Reset/Reload AlertDialog | composable | L300-L318 |
| — Update AlertDialog ("发现新版本": 立即更新/以后再说) | composable | L320-L338 |
| — FaceRecognitionOverlay wiring (visible/onDismiss/onToggle/manager) | composable | L340-L347 |

---

### 7. ui/components/Stage.kt (L1-L429)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L45 |
| `invertColorMatrix` | private val ColorMatrix — dark-theme page inversion | L47-L54 |
| `Stage` | @Composable fun | L56-L428 |
| Parameters (22): | isDark, pageUris, currentPage, pageCount, pageWidth, pageHeight, zoom, panOffsetX, panOffsetY, isSpreadMode, pendingFlip(default=null), onCenterTap, onDoubleTap, onZoomChange, onPanChange, onNextPage, onPrevPage, onFlipDone(default={}), onViewportSizeChanged, onSpreadModeChanged, onPreloadAround(default={}), modifier(default=Modifier) | L57-L80 |
| `bg` / `context` | derived vals（context 供 remember(uri) 固化 ImageRequest 使用） | L81-L82 |
| `stageWidth` / `stageHeight` | mutableStateOf(0) | L83-L84 |
| `currentZoom` | mutableFloatStateOf(zoom) | L85 |
| `transition` / `scope` | Animatable(0f) / rememberCoroutineScope | L86-L87 |
| `flipJob` / `flipDir` | Job? + 翻页动画方向（1=前向 -1=后向 0=无动画/拖拽），区分动画期与拖拽期定位 | L88-L90 |
| `dragOffset` | mutableFloatStateOf(0f) | L91 |
| `isZoomed` / `pw` | derived vals | L93-L94 |
| `displaySize` | derived Pair? — fit-to-viewport display dimensions (null until measured) | L96-L110 |
| `autoSpreadMode` | derived Boolean — landscape + narrow pages | L112-L115 |
| LaunchedEffect(autoSpreadMode) | fires onSpreadModeChanged | L117-L119 |
| `currentIsZoomed` etc. | 8× rememberUpdatedState + displayW + flipUnit + touchSlop + pendingFlip | L121-L130 |
| LaunchedEffect(pageWidth, pageHeight) | resets transition to 0 | L132-L134 |
| `doFlip(dir, fromOffset, easing)` | local fun — **触发瞬间 onNextPage/onPrevPage 推进页码**（页码/渲染/预加载以目标页为准），spring 动画只负责视觉；finally: snapTo(0) + flipDir=0 + onFlipDone | L136-L166 |
| `doBounce(dir)` | local fun — boundary bounce animation（不改 flipDir） | L168-L178 |
| LaunchedEffect(currentPendingFlip) | face-triggered pending flip → doFlip | L180-L186 |
| `pagesToShow` | derived val — visible page window (±5 single, ±6 spread), sorted descending | L188-L192 |
| Root Box | composable (gestures + rendering) | L194-L427 |
| — tap/drag awaitEachGesture pointerInput | 1/3 tap zones (flip/bounce/center tap), drag follow with boundary reversal | L203-L282 |
| — transform pointerInput (isZoomed guard) | pinch zoom + pan | L284-L291 |
| — Spread branch | gap fill Box、pages loop（L316 refPage：动画期以翻页前页码为基准衔接滑动）、gradient edge masks；AsyncImage 用 remember(uri) 固化请求 | L296-L374 |
| — Single branch | pages loop；base when（L387-L393）/pageOffsetX when（L395-L401）：flipDir 区分动画期（旧当前页滑出/新当前页滑入）与拖拽期（当前页跟手），后续页停靠 stageWidth 防透闪；AsyncImage remember(uri) 固化请求 | L377-L427 |

---

### 8. ui/components/TopBar.kt (L1-L189)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L29 |
| `TopBar` | @Composable fun | L31-L189 |
| Parameters (16): | isDark, fileName(unused, kept for API compat), currentPage, pageCount, showPageNav, onShelfClick, onPageJumpClick, onThumbnailsClick, onResetClick, onThemeLongClick, faceEnabled(default=false), faceActive(default=false), onFaceClick(default={}), onFaceLongClick(default={}), onSettingsClick(default={}), modifier | L33-L49 |
| Local colors | bg, border, text, muted, divider, ctrlBg, ctrlBorder, accent | L51-L58 |
| Main Row | composable | L60-L188 |
| — Shelf button Row | icon + text, clickable (calls onShelfClick) | L71-L83 |
| — Page nav (if showPageNav) | page number display (click → jump dialog) + divider | L85-L109 |
| — Weight Spacer | L112 |
| — Action buttons (if showPageNav) | thumbnail, face, reset, settings | L114-L186 |
| — — Thumbnail button ▦ | clickable | L116-L126 |
| — — Face button 👁 | click → ToggleFace; long-press → ShowFaceOverlay; red highlight when faceActive | L128-L157 |
| — — Reset button ↺ | click → reset dialog; long-press → toggle theme | L159-L173 |
| — — Settings button ⚙ | click → ToggleSettings | L175-L186 |

---

### 9. ui/components/Footer.kt (L1-L39)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L15 |
| `BottomFooter` | @Composable fun | L17-L39 |
| Parameters (3): | isDark, statusMessage, modifier | L19-L21 |
| Local colors | bg, border, text | L23-L25 |
| Text composable | status message display | L27-L38 |

---

### 10. ui/components/EmptyView.kt (L1-L89)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L29 |
| `EmptyView` | @Composable fun | L31-L89 |
| Parameters (3): | isDark(default=true), onShelfClick, modifier | L33-L35 |
| Local colors | muted, accent, text, bg, border | L37-L41 |
| Root Box | centered Column | L43-L88 |
| — Icon Text ("🎼") | L54-L57 |
| — Title Text ("乐谱查看器") | L58-L63 |
| — Description Text | L64-L69 |
| — Upload button Row | L71-L86 |

---

### 11. ui/components/ThumbnailPanel.kt (L1-L162)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L42 |
| `invertColorMatrix` | private val ColorMatrix | L44-L51 |
| `ThumbnailPanel` | @Composable fun | L53-L162 |
| Parameters (7): | isDark, pageCount, currentPage, getThumbnailUri, onPageSelected, onClose, modifier | L54-L62 |
| Local colors | panelBg, panelBorder, itemBg, itemBorder, itemActiveBg, itemActiveBorder, muted | L63-L69 |
| Column root | composable | L71-L161 |
| — Close button Box | L78-L95 |
| — gridState + LaunchedEffect scroll-to-current | L98-L106 |
| — LazyVerticalGrid (2 cols, state=gridState) | L108-L160 |
| — — itemsIndexed (pageIndex → thumbnail item) | L116-L159 |
| — — — Thumbnail AsyncImage | L136-L149 |
| — — — Page number Text | L150-L157 |

---

### 11b. ui/components/ShelfPanel.kt (L1-L255)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L56 |
| `ShelfPanel` | @Composable fun | L58-L255 |
| Parameters (9): | isDark, shelfFiles, shelfSortBy, onFileSelected, onImportClick, onClose, onRename, onToggleSort, modifier | L60-L68 |
| Local colors + rename state | panelBg, itemBg, itemBorder, muted, accent, text, renameTarget, renameText | L70-L78 |
| Column root | composable | L80-L254 |
| — Close button Box | L82-L95 |
| — Row: Import button + Sort toggle | L97-L144 |
| — — Import button ("+ 导入乐谱") | L103-L115 |
| — — Sort toggle (↓/A) | L116-L129 |
| — Empty state Text | L134-L140 |
| — LazyVerticalGrid (2 cols) | L142-L209 |
| — — itemsIndexed (combinedClickable: click→open, longClick→rename) | L144-L208 |
| — — — Thumbnail (AsyncImage or 🎼 fallback) | L159-L177 |
| — — — File name Text | L178-L190 |
| — Rename AlertDialog | L212-L253 |

---

### 11c. ui/components/SettingsPanel.kt (L1-L203)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L36 |
| `SettingsPanel(...)` | @Composable fun | L38-L201 |
| Parameters (11): | isDark, versionName, versionCode, updateStatus, updateInfo, updateMessage, onCheckUpdate, onDownloadUpdate, onInstallUpdate, onClose, modifier | L40-L52 |
| Local colors | panelBg, panelBorder, text, muted, divider, ctrlBg, ctrlBorder, accent, onAccent | L53-L61 |
| Root Column (300dp right panel, 22dp 圆角) | composable | L63-L200 |
| — Close button Box | 同 ThumbnailPanel 样式 | L70-L87 |
| — Content Column | composable | L89-L172 |
| — — Title + version line + divider | L94-L98 |
| — — Check-update button（Checking/Downloading 时禁用，文案"检查中…"/"下载中…"） | L100-L122 |
| — — `when(updateStatus)` 状态区 | Idle/Checking 空态 · UpToDate/Error/Downloading 文案 · Available 版本+notes+**查看更新日志展开**（showFullLog，L140-L155）+立即更新按钮 · Downloaded 立即安装按钮 | L124-L171 |
| — Footer Spacer(weight) + 版权行 "© 2026 Xusysysy · MSV 乐谱查看器" | L174-L183 |
| `StatusText` | private @Composable | L185-L188 |
| `OutlineButton` | private @Composable — accent 描边按钮 | L190-L203 |

---

### 12. ui/components/LoadingOverlay.kt (L1-L53)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L19 |
| `LoadingOverlay` | @Composable fun | L21-L53 |
| Parameters (3): | isDark(default=true), visible(default=true), modifier | L22-L26 |
| Early return | if (!visible) return | L27 |
| Local colors | bg, accent, track | L29-L31 |
| Box overlay | pointerInput consumes all events + CircularProgressIndicator centered | L33-L52 |

---

### 13. viewmodel/ViewerViewModel.kt (L1-L907)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L38 |
| `ViewerViewModel(application)` | class : AndroidViewModel | L40-L906 |
| companion | FLIP_WINDOW_MS(2000) / FAST_FLIP_COUNT(4) / FLIP_SETTLE_DELAY_MS(1000) — 快速翻动判定参数 | L42-L46 |
| `fileRepo` | private val FileRepository | L48 |
| `sessionRepo` | private val SessionRepository | L49 |
| `pdfRenderer` | private val PdfPageRenderer | L50 |
| `faceManager` | public val FaceRecognitionManager | L51 |
| `faceRepo` | private val FaceRecognitionRepository | L52 |
| `updateRepo` | private val UpdateRepository | L53 |
| `_uiState` | private val MutableStateFlow | L55 |
| `uiState` | val StateFlow (public) | L56 |
| `imageUris` | private var List<Uri> | L58 |
| `pdfUri` | private var Uri? | L59 |
| `loadJob` / `preloadJob` | private var Job? | L60-L61 |
| `thumbnailCache` | private val ConcurrentHashMap<Int, Uri> | L62 |
| `pageMap` | private var Map<String, Int> — per-file last-read page (KEY_PAGE_MAP) | L63 |
| `pageScales` | private val ConcurrentHashMap<Int, Float> — 每页渲染比例（1f 全分辨率 / 0.6f / 0.4f 压缩层），翻页升级判断依据 | L65-L66 |
| `flipTimestamps` / `settleJob` | ArrayDeque<Long> 翻页时间戳 + 停止翻动后恢复正常分辨率的延时任务 | L68-L70 |
| `singleRenderJob` | private var Job? — renderPageToCacheComputeSize 的当前页渲染任务（切换谱子时取消） | L72 |
| `currentDownloadId` | private var Long — 当前 OTA 下载任务 id | L73 |
| `downloadReceiver` | private val BroadcastReceiver — ACTION_DOWNLOAD_COMPLETE → onDownloadComplete | L75-L81 |
| `init` block | 版本字段填充 + 注册下载接收器 + pageMap collector + face prefs load + faceState 同步 + onGesture→faceFlip + restoreSession() + 冷启动静默检查更新 | L83-L115 |
| `handleShareIntent(intent)` | public fun | L117-L146 |
| `onEvent(event)` | public fun (event dispatch, spread-aware page step, +5 update events) | L148-L184 |
| `handleFilesSelected(uris)` | private fun — imports file, then calls loadShelfFiles() if shelf is visible | L186-L212 |
| `openPdf(uri, name, restorePage=0)` | private fun — 入口先 cancelPageRendering() 停掉旧谱渲染 | L214-L256 |
| `openImages(uris, name, initialPage=0)` | private fun — 图片用原图全部标记 1f；入口先 cancelPageRendering() | L259-L285 |
| `cancelPageRendering()` | private fun — 取消 preloadJob/singleRenderJob/settleJob + 清空 flipTimestamps/pageScales | L288-L297 |
| `goToPage(page)` | private fun — 页码变更统一入口 → onFlipHappened | L299-L308 |
| `onFlipHappened(center)` | private fun — 记录翻页时间戳；快速模式下压缩渲染 + 重置 1s settle（settle 强制全分辨率逻辑按当前页执行） | L310-L320 |
| `isFastFlipping()` | private fun — 2s 窗口内翻页 ≥4 次判定快速连续翻动 | L322-L326 |
| `renderPageToCacheComputeSize(pageIndex, ratio)` | private fun — tracked by singleRenderJob，渲染后标记 scale=1f | L328-L347 |
| `preloadAround(center, forceFullRes=false)` | public fun — 快速翻动模式：**当前页同步优先渲染**，窗口内其余缺失页并行 0.6x Q85（跳过升级保证速度）；正常模式：center/next Q95、±1 Q90 缺失**或 pageScales≠1f** 时全尺寸渲染升级，2-3 页 Q85@0.6x、≥4 页 Q80@0.4x 仅缺失时渲染；淘汰同步清理 pageScales | L349-L462 |
| `docCacheKey` | private val getter — pdfUri path hash (cache file namespacing) | L464-L465 |
| `renderPage(pageIndex, pageW, pageH, zoom, quality=95)` | private fun — JPEG into cacheDir，文件名含 `{pageW}x{pageH}`（尺寸变化→URI 变化→Coil 缓存失效） | L467-L478 |
| `updateViewportSize(width, height)` | private fun | L480-L492 |
| `setZoom(zoom)` | private fun | L494-L496 |
| `panBy(dx, dy)` | private fun | L498-L502 |
| `toggleUI()` | private fun | L504-L506 |
| `toggleThumbnails()` | private fun — 打开时关闭设置面板 | L508-L512 |
| `toggleShelf()` | private fun — 打开时关闭设置面板 | L514-L518 |
| `toggleSettings()` | private fun — 打开时关闭缩略图/谱架（三面板互斥） | L520-L529 |
| `toggleShelfSort()` | private fun — toggles NAME↔DATE, reloads shelf | L531-L536 |
| `setSpreadMode(spread)` | private fun — on enable, aligns current page to even index | L538-L545 |
| `loadShelfFiles()` | private fun — sorts by shelfSortBy, maps to ShelfFile, generates PDF thumbnails per file | L547-L576 |
| `generatePdfThumbnail(fileUri)` | private fun — renders page 0 at 200px PNG, caches by path hash | L578-L602 |
| `renameShelfFile(oldUri, newName)` | private fun — renames file, refreshes shelf, reopens at same page | L604-L624 |
| `openShelfFile(uri)` | private fun — closes shelf, restores page via pageMap[name] | L626-L639 |
| `toggleTheme()` | private fun | L641-L643 |
| `toggleFace()` | private fun — syncs faceManager running/enabled with uiState.faceEnabled | L645-L654 |
| `faceFlip(dir)` | private fun — sets uiState.pendingFlip (consumed by Stage) | L656-L659 |
| `flipDone()` | private fun — clears pendingFlip | L661-L664 |
| `showFaceOverlay()` | private fun | L666-L669 |
| `hideFaceOverlay()` | private fun — hides + persists face prefs via faceRepo.save | L671-L677 |
| `registerDownloadReceiver()` | private fun — ContextCompat.registerReceiver RECEIVER_NOT_EXPORTED | L679-L687 |
| `checkUpdate(manual)` | private fun — Gitee/GitHub 并行查询，仅取比当前新的候选（平手 Gitee 优先）；manual 失败显示 Error，自动静默回 Idle | L689-L712 |
| `downloadUpdate()` | private fun — 幂等（Downloading 中忽略）→ removeStale → enqueue → Downloading + 关弹窗 | L714-L728 |
| `onDownloadComplete(id)` | private fun — STATUS_SUCCESSFUL → Downloaded + **自动调起 installUpdate()**；否则 Error | L730-L741 |
| `installUpdate()` | private fun — 未授权 → 跳 unknown sources 授权页；已授权 → FileProvider 安装 Intent | L743-L756 |
| `startActivity(intent)` | private fun — runCatching 包装 | L758-L760 |
| `dismissUpdateDialog()` | private fun — 仅关弹窗（保留 Available 状态） | L762-L764 |
| `resetZoom()` | private fun | L766-L768 |
| `reset()` | private fun — 重建 ViewerState 保留版本字段 + cancelPageRendering() | L770-L784 |
| `reload()` | private fun | L786-L801 |
| `saveSession()` | private fun | L803-L823 |
| `getThumbnailUri(pageIndex)` | public fun | L825-L826 |
| `preloadPage(pageIndex)` | public fun（当前无调用方） | L828-L847 |
| `preloadThumbnails()` | private fun — PNG thumbs keyed by docCacheKey | L849-L873 |
| `restoreSession()` | private fun — accessibility check, restores mode/page/uris | L875-L900 |
| `onCleared()` | override fun — 注销下载接收器 + close pdfRenderer | L902-L906 |

---

### 14. data/model/ViewerState.kt (L1-L94)

| Element | Type | Lines |
|---|---|---|
| Package + import | — | L1-L3 |
| `ViewerState` | data class (31 fields) | L5-L39 |
| Fields: | mode, currentPage, pageCount, zoom, panOffsetX, panOffsetY, showUI, showThumbnails, showShelf, isDarkTheme, statusMessage, isLoading, fileName, pageUris, pageWidth, pageHeight, viewportWidth, viewportHeight, thumbnailsLoading, shelfFiles(emptyList()), shelfSortBy(ShelfSort.DATE), isSpreadMode(false), faceEnabled(false), faceActive(false), showFaceOverlay(false), pendingFlip(null), showSettings(false), appVersionName(""), appVersionCode(0), updateStatus(Idle), updateInfo(null), updateMessage(""), showUpdateDialog(false) | L6-L38 |
| `ShelfSort` | enum (NAME, DATE) | L41 |
| `Mode` | sealed class | L43-L47 |
| — `Idle` | data object | L44 |
| — `Image` | data object | L45 |
| — `Pdf` | data object | L46 |
| `ViewerEvent` | sealed class | L49-L77 |
| — `FilesSelected(uris)` | data class | L50 |
| — `GoToPage(page)` | data class | L51 |
| — `NextPage` | data object | L52 |
| — `PrevPage` | data object | L53 |
| — `SetZoom(zoom)` | data class | L54 |
| — `PanBy(dx, dy)` | data class | L55 |
| — `UpdateViewportSize(width, height)` | data class | L56 |
| — `ToggleUI` | data object | L57 |
| — `ToggleThumbnails` | data object | L58 |
| — `ToggleTheme` | data object | L59 |
| — `ResetZoom` | data object | L60 |
| — `Reset` | data object | L61 |
| — `Reload` | data object | L62 |
| — `ToggleShelf` | data object | L63 |
| — `OpenShelfFile(uri)` | data class | L64 |
| — `RenameShelfFile(uri, newName)` | data class | L65 |
| — `ToggleShelfSort` | data object | L66 |
| — `SetSpreadMode(spread)` | data class | L67 |
| — `ToggleFace` | data object | L68 |
| — `ShowFaceOverlay` | data object | L69 |
| — `HideFaceOverlay` | data object | L70 |
| — `FlipDone` | data object | L71 |
| — `ToggleSettings` | data object | L72 |
| — `CheckUpdate` | data object | L73 |
| — `DownloadUpdate` | data object | L74 |
| — `InstallUpdate` | data object | L75 |
| — `DismissUpdateDialog` | data object | L76 |
| `ShelfFile` | data class (3 fields) | L79-L83 |
| Fields: | name(String), uri(Uri), thumbnailUri(Uri?) | L80-L82 |
| `UpdateInfo` | data class (5 fields) — tag, notes(摘要), fullNotes(完整更新日志), apkUrl, source | L85-L91 |
| `UpdateStatus` | enum (Idle, Checking, UpToDate, Available, Downloading, Downloaded, Error) | L92-L94 |

---

### 15. data/repository/FileRepository.kt (L1-L77)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L7 |
| `FileRepository(context)` | class | L9-L77 |
| `docsDir` | private val property (getter) | L11-L12 |
| `getFileName(uri): String` | fun | L14-L27 |
| `isPdf(fileName): Boolean` | fun | L29-L30 |
| `isImage(fileName): Boolean` | fun | L32-L37 |
| `takePersistablePermission(uri)` | fun | L39-L46 |
| `takePersistablePermissions(uris)` | fun | L48-L50 |
| `copyToLocal(uri, fileName): Uri?` | fun | L52-L63 |
| `getLocalFile(fileName): Uri?` | fun | L65-L68 |
| `listLocalFiles(): List<JFile>` | fun — lists all files in docsDir sorted by last modified desc | L70-L73 |
| `openInputStream(uri)` | fun | L75 |

---

### 16. data/repository/SessionRepository.kt (L1-L69)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L10 |
| `Context.dataStore` | extension property ("session") | L12 |
| `SessionRepository(context)` | class | L14-L69 |
| Companion object — keys | KEY_MODE, KEY_CURRENT_PAGE, KEY_URIS, KEY_FILE_NAME, KEY_PAGE_MAP | L16-L22 |
| `SessionData` | nested data class (4 fields) | L24-L29 |
| `sessionFlow: Flow<SessionData?>` | val | L31-L38 |
| `saveSession(mode, currentPage, uris, fileName)` | suspend fun — also records fileName→currentPage into KEY_PAGE_MAP JSON | L40-L56 |
| `getPageMap(): Flow<Map<String, Int>>` | fun — parses KEY_PAGE_MAP JSON (per-file last-read page) | L58-L64 |
| `clearSession()` | suspend fun | L66-L68 |

---

### 17. data/pdf/PdfPageRenderer.kt (L1-L125)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L11 |
| `PdfPageRenderer(context)` | class | L13-L125 |
| `renderer: PdfRenderer?` | private var | L15 |
| `currentUri: Uri?` | private var | L16 |
| `pageCount: Int` | private var | L17 |
| `cache` | private val LruCache (max 16) | L19-L21 |
| `open(uri): Int` | fun | L23-L45 |
| `renderPage(pageIndex, viewportW, viewportH, zoom=1f): Bitmap?` | fun — renders to temp bitmap, composites onto white Canvas via SRC_OVER to force opacity | L47-L70 |
| `renderThumbnail(pageIndex, maxDim=200): Bitmap?` | fun — same temp+composite pattern for full opacity | L72-L95 |
| `pageWidth: Int` | val (getter from pdf page 0) | L97-L103 |
| `pageHeight: Int` | val (getter from pdf page 0) | L105-L111 |
| `close()` | fun | L113-L119 |
| `getPageCount(): Int` | fun | L121 |

---

### 18. facer/FaceCamera.kt (L1-L107)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L42 |
| `F` / `E` / `L` / `B` | private val IntArray — landmark index pairs: face oval / eyes / lips / brows | L44-L47 |
| `FaceCamera(manager, visible, modifier)` | @Composable fun | L49-L107 |
| Permission state + request launcher | camera permission, Toast on deny | L55-L58 |
| LaunchedEffect permission request | L60 |
| Early returns | no permission L62; model init failure L64-L67 | L62-L67 |
| `exec` | single-thread analyzer executor + DisposableEffect shutdown | L69-L70 |
| `state` collect + alpha animation | fade-in when visible | L72-L75 |
| `camModifier` | visible → fillMaxWidth 4:3; hidden → 1dp (keeps analyzer alive) | L77-L81 |
| Box + AndroidView(PreviewView) | binds Preview + ImageAnalysis(320x240, KEEP_ONLY_LATEST) to front camera | L83-L97 |
| Landmarks Canvas overlay | draws F/E/B/L polylines, x mirrored, only when visible + landmarks present | L99-L105 |

---

### 19. facer/FaceLog.kt (L1-L69)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L11 |
| `FaceLog` | object | L13-L69 |
| `LOG_FILE` / `writer` / `sdf` | const "msv_face_log.txt" / PrintWriter? / timestamp format | L14-L16 |
| `logFile: File?` | @Volatile val (private set) | L18-L20 |
| `init(ctx)` | fun — creates log in externalFilesDir, deletes if >5MB | L22-L31 |
| `d/i/w/e(tag, msg)` | fun — dual write: logcat + file (e appends stacktrace) | L33-L58 |
| `write(line)` | @Synchronized private fun | L60-L66 |
| `getLogPath()` | fun | L68 |

---

### 20. facer/FaceRecognitionManager.kt (L1-L184)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L24 |
| `FaceRecognitionManager(context)` | class | L26-L184 |
| Companion `TAG` | const "MSV_FACE" | L27 |
| `FaceState` | data class — running, enabled, triggerMode, thresholds, mirrored, actionThreshold, actionActive, fps, scores, landmarks, status | L31-L37 |
| `Thresholds` | data class — blink, pucker, puckerBiasL, puckerBiasR | L38 |
| `GestureScores` | data class — lWink, rWink, lPucker, rPucker | L39 |
| `TriggerMode` | enum (WINK, PUCKER, BOTH) | L40 |
| `Gesture` | enum (LEFT_WINK, RIGHT_WINK, LEFT_PUCKER, RIGHT_PUCKER, NONE) | L41 |
| `_state` / `stateFlow` | MutableStateFlow / StateFlow<FaceState> | L43-L44 |
| `onGesture` | public var ((Gesture) -> Unit)? — invoked on main handler | L45 |
| `landmarker` + smoothing/hysteresis fields | private | L47-L50 |
| `init(): Boolean` | fun — loads face_landmarker.task, RunningMode.IMAGE, blendshapes on, 1 face | L52-L65 |
| `updateState / currentState / isReady` | fun | L67-L69 |
| `process(ip: ImageProxy)` | fun — decode → detect → blendshapes → gesture (800ms cooldown) → mainHandler callback | L72-L98 |
| `processBlendshapes(cats): Gesture` | private fun — EMA smooth + hysteresis + left/right bias, updates scores + actionActive | L100-L117 |
| `score / ema / hyst` | private fun — lookup, EMA smoothing, hysteresis state | L119-L127 |
| `decode(ip): Bitmap` | private fun — YUV→NV21→ARGB_8888 direct conversion, rotation + optional mirror | L128-L159 |
| `convertYuvToBitmap(nv21, w, h, out)` | private fun — BT.601 YUV→RGB pixel loop | L161-L181 |
| `close()` | fun — closes landmarker, clears smoothing state | L182 |

---

### 21. facer/FaceRecognitionOverlay.kt (L1-L177)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L48 |
| `FaceRecognitionOverlay(visible, faceEnabled, onDismiss, onToggle, manager, isDark, modifier)` | @Composable fun | L50-L109 |
| Early return | if (!visible && !faceEnabled) return | L52 |
| isLand + camera permission + request | L54-L57 |
| Hidden mode | 1dp Box + FaceCamera(visible=false) — keeps pipeline running while overlay closed | L60-L64 |
| Local colors | bg, card, b, t, t2, ac, dn, gr | L66-L70 |
| Root Box scrim | click → onDismiss | L72 |
| — Landscape Row layout | 2 columns: preview+scores / controls | L74-L92 |
| — Portrait Column layout | scrollable single column | L93-L107 |
| `Header` | private @Composable — title + LIVE/OFF pill (fps), toggles running | L111-L121 |
| `AR` | private @Composable — 4 gesture score cards row | L123-L128 |
| `Card` | private @Composable — single emoji+score card | L130-L135 |
| `Modes` | private @Composable — trigger mode buttons (Wink/撅嘴/两者) | L137-L146 |
| `SliderCard` | private @Composable — labeled slider (blink/pucker/action/bias L/R) | L148-L154 |
| `MirrorToggle` | private @Composable — 镜像/原始 toggle | L156-L163 |
| `Debug` | private @Composable — diagnostics line + status | L165-L171 |
| `MBtn` | private @Composable — mode select button | L173-L177 |

---

### 22. facer/FaceRecognitionRepository.kt (L1-L73)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L10 |
| `Context.faceStore` | extension property ("face_prefs" DataStore) | L12 |
| `FaceRecognitionRepository(context)` | class | L14-L73 |
| `FacePrefs` | data class (6 fields) — mirrored, blinkThreshold, puckerThreshold, puckerBiasL, puckerBiasR, actionThreshold | L16-L23 |
| Companion — keys | K_MIRROR, K_BLINK, K_PUCKER, K_BIAS_L, K_BIAS_R, K_ACTION | L25-L32 |
| `prefsFlow: Flow<FacePrefs>` | val | L34-L43 |
| `save(manager)` | suspend fun — persists FaceState → DataStore | L45-L55 |
| `load(manager)` | suspend fun — restores DataStore → FaceState | L57-L72 |

---

### 23. data/repository/UpdateRepository.kt (L1-L192)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L17 |
| `compareVersions(a, b)` | top-level fun — 逐段数值版本比较（短段补 0、容错 v 前缀）；单测 `app/src/test/java/com/music/msv/CompareVersionsTest.kt` | L23-L35 |
| `UpdateRepository(context)` | class | L38-L192 |
| Companion constants | GITEE_LATEST / GITHUB_LATEST / TIMEOUT_MS(8000) / APK_PREFIX("MSV-ScoreViewer") / UPDATE_DIR("update") / APK_MIME | L39-L47 |
| `installedVersionName` / `installedVersionCode` | val getter — PackageManager（API33 PackageInfoFlags 分支） | L49-L66 |
| `fetchGiteeLatest` / `fetchGitHubLatest` | fun — → fetchLatest | L69-L71 |
| `fetchLatest(url, isGitee)` | private fun — HttpURLConnection GET → JSONObject；assets 按 `.apk`+前缀过滤（排除源码包）；notes=摘要 fullNotes=完整 body；失败返回 null | L73-L101 |
| `summarizeNotes(body, maxLen=300)` | fun — release body 摘要（去标题前缀/拼行/截断） | L102-L118 |
| `removeStaleDownloads(apkUrl)` | fun — 清理同 URL 的 RUNNING/PENDING/PAUSED 下载任务 | L120-L134 |
| `apkFileName(tag)` / `updateApkFile(tag)` | fun — `update/msv-update-{tag}.apk`（外部专属目录） | L136-L141 |
| `enqueueDownload(apkUrl, tag)` | fun — DownloadManager（系统通知栏进度、删残留文件、禁止漫游） | L143-L155 |
| `queryDownloadStatus(id)` | fun | L157-L165 |
| `cleanupDownloadedApk(installedVersion)` | fun — 升级成功后清理已装版本的历史安装包 | L167-L174 |
| `isInstallPermissionGranted()` | fun — canRequestPackageInstalls | L176 |
| `unknownSourcesIntent()` | fun — ACTION_MANAGE_UNKNOWN_APP_SOURCES 授权引导 | L178-L182 |
| `installIntent(tag)` | fun — FileProvider(`${applicationId}.fileprovider`) URI + ACTION_VIEW 安装 Intent | L184-L192 |

---

### 24. release/publish.ps1 — OTA 双平台发布脚本 (L1-L157)

| Element | Type | Lines |
|---|---|---|
| Header + param | -NoteFile / -SkipBuild / -NoUpload / -SkipTag；TLS1.2 强制；**必须保持 UTF-8 BOM 编码** | L1-L11 |
| 1. 版本解析 | versionName/versionCode ← app/build.gradle.kts 正则；tag = versionName；APK 名 MSV-ScoreViewer-v{ver}-release.apk | L23-L31 |
| 2. 发布说明 | 缺省 `release/releasenote{versionCode}.md`；用 `[IO.File]::ReadAllText` 读取（Get-Content 扩展属性会破坏 ConvertTo-Json 序列化）；缺失降级为仅版本号 | L33-L40 |
| 3. 构建 | assembleRelease → 复制 APK 到 release/ | L42-L53 |
| 4. tag + 推送 | -SkipTag 可跳过；git tag（已存在即报错，永不 force）→ push origin/gitee | L55-L70 |
| -NoUpload 出口 | 只打 tag 不发 release | L71 |
| 5. GitHub | gh release view 查重（EAP 临时降级 Continue 规避 PS5.1 stderr 终止错误）→ create(--notes-file) / upload --clobber；gh 未在 PATH 时回退完整路径 | L72-L93 |
| 6. Gitee | token=$env:MSV_GITEE_TOKEN（缺失→打印手动指引降级）；GET releases/tags/{tag} 查重 → POST releases 创建（含必填 target_commitish，UTF8 JSON 防中文乱码）→ POST releases/{id}/attach_files multipart（System.Net.Http，**字段名 file 单数**；PS5.1 无 -Form） | L95-L148 |
| 7. 汇总 | 双平台结果 URL + 任一失败 exit 1（幂等可重跑补传） | L150-L157 |
