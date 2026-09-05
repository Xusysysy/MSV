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
│   │   ├── ImslpPanel.kt        ← IMSLP 搜索弹窗：搜索 → 结果 → 官方页 WebView 浏览（拦截 PDF 应用内下载）
│   │   └── LoadingOverlay.kt    ← Spinner overlay
│   └── screen/
│       └── ViewerScreen.kt      ← Root orchestrator, wires ViewModel → components
├── viewmodel/
│   └── ViewerViewModel.kt       ← Central state holder, MVVM, preload logic, face wiring, OTA update
├── data/
│   ├── model/
│   │   ├── ViewerState.kt       ← UI state data class + Mode sealed class + ViewerEvent sealed class
│   │   └── ImslpModels.kt       ← IMSLP 搜索结果模型
│   ├── repository/
│   │   ├── FileRepository.kt    ← SAF file access, local copy, MIME detection
│   │   ├── SessionRepository.kt ← DataStore persistence (session + per-file page map)
│   │   ├── UpdateRepository.kt  ← OTA: release query (Gitee+GitHub), version compare, APK download/install
│   │   └── ImslpRepository.kt   ← IMSLP 搜索（MediaWiki API）+ 官方页直链应用内下载（免责/等待/门禁处理）
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

### 6. ui/screen/ViewerScreen.kt (L1-L392)

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
| — Update AlertDialog ("发现新版本": 日志默认折叠可展开滚动/立即更新/以后再说) | composable | L344-L397 |`n| — DownloadFail AlertDialog (双源失败: Gitee 限流原因解释+切换流量引导+重试/知道了) | composable | L400-L421 |
| — FaceRecognitionOverlay wiring (visible/onDismiss/onToggle/manager) | composable | L340-L347 |

---

### 7. ui/components/Stage.kt (L1-L790)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L46 |
| `ScorePageImage` | private @Composable — 单页渲染图层化：旧 URI 垫底 + 新 URI 前层，升级换缓存零空窗防闪烁；**状态 remember(pageIndex) 键控（槽位换页重置，杜绝前一页垫底串位闪现）** | L48-L87 |
| `invertColorMatrix` | private val ColorMatrix — dark-theme page inversion | L89-L96 |
| `Stage` | @Composable fun | L98-L622 |
| Parameters (24): | isDark, pageUris, currentPage, pageCount, pageWidth, pageHeight, zoom, panOffsetX, panOffsetY, **zoomMode**, isSpreadMode, pendingFlip(default=null), onCenterTap, onDoubleTap, onZoomChange, onPanChange, **onZoomModeEnter/Exit(default={})**, onNextPage, onPrevPage, onFlipDone(default={}), onViewportSizeChanged, onSpreadModeChanged, onPreloadAround(default={}), modifier(default=Modifier) | L99-L124 |
| states（bg/context/stageWidth/Height/transition/scope/flipJob/flipDir/lastPage/dragOffset/**lastTapTime/X/Y**/**renderZoom/renderPanX/renderPanY/pinching**/**pinchExited/maxNetDisp**） | L125-L141 |
| derived vals（pw/displaySize/autoSpreadMode/LE(autoSpread)/current* 组含 currentZoomMode/Enter/Exit） | L141-L179 |
| `doFlip(dir, fromOffset, easing)` | local fun — **zoomMode 时禁用**；触发瞬间记录 lastPage 并推进页码，动画只负责视觉；finally 带 `flipJob === selfJob` 守卫（被接替的旧动画不复位 snapTo/flipDir，防快速往返闪回旧页） | L196-L232 |
| `doBounce(dir)` | local fun — boundary bounce animation（不改 flipDir/lastPage） | L235-L245 |
| LaunchedEffect(currentPendingFlip) | face-triggered pending flip → doFlip（zoomMode 时禁用） | L247-L253 |
| `pagesToShow` | derived val — visible page window (±5 single, ±6 spread), sorted descending | L255-L259 |
| Root Box | composable (gestures + rendering) | L261-L621 |
| — VM 缩放/平移同步 LaunchedEffect | zoom/pan 变化 → snapTo（**`!renderZoom.isRunning` 时才覆盖——退出缩放的动画过渡不被吞掉**） | L229-L240 |
| — tap/drag/pinch awaitEachGesture pointerInput | 正常模式：1/3 点按三区/拖拽翻页；**双击窗口 400ms**；**双击手指微动超 touchSlop 的兜底：路径最大净位移 maxNetDisp<24dp 时仍按单击处理（修复双击失效）**；**双指捏合：...捏回 ≤1.02 动画恢复并退出（松手时）/捏合过程中 newZoom≤1.02 自动动画退出（pinchExited 防本次手势重入）**；**缩放以双指起始中点为焦点（局部 curZoom/curPanX/Y 同步追踪做焦点补偿，消除异步 snapTo 滞后误差）**；缩放模式：单指平移（钳制）、双击恢复（**恢复动画 zoom/pan 并行一步到位**）；全部 change 消费；捏合转单指重锚定防跳变 | L319-L650 |
| — 内容 graphicsLayer 容器 | scaleX/Y = renderZoom，translation = renderPan（缩放模式整体缩放/平移，横屏双页为一个整体） | L502-L511 |
| — Spread branch | gap fill Box、pages loop（L534 refPage：动画期 = lastPage 保证两对页滑动衔接）、gradient edge masks；页面用 PageWithPlaceholder（占位图+淡入）渲染 | L512-L388→L512-L587 |
| — Single branch | pages loop；base when（L599-L605）/pageOffsetX when（L607-L613）：动画期以 lastPage 锚定滑出页（消除 currentPage 滞后窗口的前一页闪现）、拖拽期当前页/前页跟手、后续页停靠 stageWidth 防透闪；PageWithPlaceholder（占位图+淡入）渲染 | L589-L620 |

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

### 11. ui/components/ThumbnailPanel.kt (L1-L381)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L55 |
| `invertColorMatrix` | private val ColorMatrix | L57-L63 |
| `bookmarkPresets` | private val List<Int> — 8 个预设 RGB 书签色 | L65-L68 |
| `ThumbnailPanel` | @OptIn(ExperimentalFoundationApi) @Composable fun | L70-L324 |
| Parameters (13): | isDark, pageCount, currentPage, isPdfMode, bookmarks, getThumbnailUri, onPageSelected, onBookmarkClick, onAddBookmark, onDeleteBookmark, onEditBookmark, onClose, modifier | L71-L83 |
| Local colors + states | panelBg…muted/text/danger/menuContainer + addPage/addTitle/addColor/editBm/bmMenuId | L84-L92 |
| Box root（书签栏在下层 0-284，缩略图面板在上层 CenterEnd 220-520） | composable | L94-L315 |
| — Bookmark rail（width 284 = 可见 220 + 面板下 64，End 对齐 → 右端统一延伸至面板之下，verticalScroll 可滚动，无额外背景，spacedBy 6 间隙，底部 Spacer 保证滚到底完整） | 仅 isPdfMode 且书签非空时显示；**书签按页码升序渲染（sortedBy page）** | L96-L137 |
| — — BookmarkStrip ×N | 半透明（color alpha **0.75**）左半圆书签条：click→跳转页码, long-press→菜单（**编辑**/删除红字），文字 13sp 单行（**黑描边层 matchParentSize 与白填充层像素级对齐**），requiredWidthIn(min 220/max 284) 宽度随名长 | L109-L135 |
| — Thumbnails Column (300dp, **align CenterEnd** 面板层) | composable | L139-L315 |
| — — Close button Box | L142-L159 |
| — — gridState + LaunchedEffect scroll-to-current | L161-L170 |
| — — LazyVerticalGrid (2 cols, state=gridState) | L172-L224 |
| — — — itemsIndexed → Column（click→跳页, **long-press→添加书签弹窗**） | L180-L224 |
| — — — — Thumbnail AsyncImage | L192-L206 |
| — — — — Page number Text | L207-L215 |
| `BookmarkDialog` | private @Composable — 添加/编辑共用（命名 ≤50 字 + 8 色 RGB 色板） | L317-L372 |

---

### 11b. ui/components/ShelfPanel.kt (L1-L354)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L55 |
| `invertColorMatrix` | private val ColorMatrix | L57-L63 |
| `ShelfPanel` | @Composable fun | L66-L302 |
| Parameters (10): | isDark, shelfFiles, shelfSortBy, onFileSelected, onImportClick, onClose, onRename, onSortSelected, onDelete, modifier | L67-L83 |
| Local colors + states | panelBg, panelBorder, itemBg, itemBorder, muted, accent, text, danger + renameTarget/renameText/menuTarget/deleteTarget/sortMenuOpen + **sortedFiles（渲染层排序兜底）** | L84-L101 |
| Column root (300dp left panel, 22dp 圆角) | composable | L99-L239 |
| — Close button Box | 同 ThumbnailPanel 样式 | L102-L119 |
| — Import + sort selector Row | L121-L175 |
| — — Import button ("+ 导入乐谱") | L127-L147 |
| — — Sort trigger (↓/A) + DropdownMenu（按日期排序/按名称排序，当前项 accent+✓） | L149-L175 |
| — Empty state Text ("暂无导入的乐谱") | L177-L187 |
| — **LazyVerticalStaggeredGrid (双列瀑布流，渲染 sortedFiles)**：卡片高度按缩略图真实比例（thumbAspect）+ 文件名行数自适应，自然错开无留空 | L227-L320 |
| — — 卡片 Column（combinedClickable: click→打开, longClick→menuTarget） | L233-L318 |
| — — — Thumbnail（AsyncImage or 🎼 fallback；**aspectRatio(thumbAspect)、有缩略图时背景透明（无灰底）**） | L248-L273 |
| — — — File name Text | L215-L222 |
| — — — DropdownMenu 长按菜单：重命名 / 删除（danger 红字，需确认） | L223-L236 |
| Delete AlertDialog（二次确认："确定删除…此操作不可恢复"） | L241-L262 |
| Rename AlertDialog（由菜单"重命名"触发） | L264-L303 |

---

### 11c. ui/components/SettingsPanel.kt (L1-L377)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L36 |
| `SettingsPanel(...)` | @Composable fun | L38-L245 |
| Parameters (16): | isDark, versionName, versionCode, showVersionLog, versionNotes, versionNotesLoading, downloadProgress, updateStatus, updateInfo, updateMessage, onCheckUpdate, onToggleVersionLog, onDownloadUpdate, onInstallUpdate, onClose, modifier | L40-L55 |
| Local colors | panelBg, panelBorder, text, muted, divider, ctrlBg, ctrlBorder, accent, onAccent | L55-L63 |
| Root Column (300dp right panel, 22dp 圆角) | composable | L65-L204 |
| — Close button Box | 同 ThumbnailPanel 样式 | L72-L89 |
| — Content Column | composable | L91-L183 |
| — — Title + version line | L97-L99 |
| — — 查看本版本更新日志 toggle（showVersionLog 展开，L107-L120 滚动显示 versionNotes/加载中） | L101-L120 |
| — — divider | L122 |
| — — Check-update button（Checking/Downloading 时禁用，文案"检查中…"/"下载中…"） | L125-L148 |
| — — `when(updateStatus)` 状态区 | Idle/Checking 空态 · UpToDate/Error 文案 · Downloading **应用内进度条+百分比** · Available 版本+notes+查看更新日志展开+立即更新按钮 · Downloaded 立即安装按钮 | L150-L195 |
| — Footer Spacer(weight) + 版权行 "© 2026 Xusysysy · MSV 乐谱查看器" | L180-L183 |
| `openSourceProjects` | private val — 开源组件许可清单（**含 IMSLP CC BY-SA 4.0 内容协议与 Peachnote 预览接口**） | L46-L58 |
| — 开源许可 AlertDialog | 许可列表 + **"IMSLP 内容使用说明"法规段（公有领域因国而异/需自行确认/遵守当地版权法）** | L301-L330 |
| `StatusText` | private @Composable | L364-L367 |
| `OutlineButton` | private @Composable — accent 描边按钮 | L369-L377 |

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

### 13. viewmodel/ViewerViewModel.kt (L1-L1187)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L38 |
| `ViewerViewModel(application)` | class : AndroidViewModel | L40-L925 |
| init 块 | PDFBoxResourceLoader.init + **PagePlaceholderCache 预热**（占位图启动解码）+ 版本号填充 + sessionRepo/face 收集 | L79-L99 |
| companion | FLIP_WINDOW_MS(2000) / FAST_FLIP_COUNT(4) / FLIP_SETTLE_DELAY_MS(1000) — 快速翻动判定参数 | L42-L46 |
| `fileRepo` | private val FileRepository | L48 |
| `sessionRepo` | private val SessionRepository | L49 |
| `pdfRenderer` | private val PdfPageRenderer | L50 |
| `faceManager` | public val FaceRecognitionManager | L51 |
| `faceRepo` | private val FaceRecognitionRepository | L52 |
| `updateRepo` | private val UpdateRepository | L53 |
| `_uiState` | private val MutableStateFlow | L55 |
| `uiState` | val StateFlow (public) | L56 |
| `imslpState` | val ImslpDialogState — IMSLP 弹窗状态（showDialog/step/currentBrowseUrl/resultsFromBrowse/searchHistory 等；跨弹窗开关/旋转保持，冷启动重置） | L63 |
| `imageUris` | private var List<Uri> | L58 |
| `pdfUri` | private var Uri? | L59 |
| `loadJob` / `preloadJob` | private var Job? | L60-L61 |
| `thumbnailCache` | private val ConcurrentHashMap<Int, Uri> | L62 |
| `pageMap` | private var Map<String, Int> — per-file last-read page (KEY_PAGE_MAP) | L63 |
| `pageScales` | private val ConcurrentHashMap<Int, Float> — 每页渲染比例（1f 全分辨率 / 0.6f / 0.4f 压缩层），翻页升级判断依据 | L65-L66 |
| `flipTimestamps` / `settleJob` | ArrayDeque<Long> 翻页时间戳 + 停止翻动后恢复正常分辨率的延时任务 | L68-L70 |
| `singleRenderJob` / `downloadJob` | private var Job? — 当前页渲染任务 + 应用内更新包下载任务 | L72-L73 |
| `init` block | 版本字段填充 + 注册下载接收器 + pageMap collector + **IMSLP 搜索历史收集** + **IMSLP 免责声明状态收集** + face prefs load + faceState 同步 + onGesture→faceFlip + restoreSession() + 冷启动静默检查更新 | L83-L126 |
| `addImslpHistory(q)` / `clearImslpHistory()` | public fun — IMSLP 搜索历史记录/清空（委托 SessionRepository，DataStore 持久化） | L123-L133 |
| `ackImslpDisclaimer()` | public fun — 确认 IMSLP 免责声明（持久化） | L135-L137 |
| `handleShareIntent(intent)` | public fun | L117-L146 |
| `onEvent(event)` | public fun (event dispatch, spread-aware page step, update/log events, shelf sort/delete, +Add/Delete/RenameBookmark) | L142-L183 |
| `handleFilesSelected(uris)` | private fun — imports file, then calls loadShelfFiles() if shelf is visible | L189-L215 |
| `openPdf(uri, name, restorePage=0)` | private fun — 入口 cancelPageRendering()；打开后读取 PDF 书签 | L213-L256 |
| `openImages(uris, name, initialPage=0)` | private fun — 图片用原图全部标记 1f；书签清空（仅 PDF 支持书签） | L259-L287 |
| `cancelPageRendering()` | private fun — 取消 preloadJob/singleRenderJob/settleJob + 清空 flipTimestamps/pageScales | L291-L300 |
| `goToPage(page)` | private fun — 页码变更统一入口 → onFlipHappened | L302-L311 |
| `onFlipHappened(center)` | private fun — **无条件 preloadAround 按当前页重算渲染**（修复跳转不加载回归）；记录翻页时间戳；快速模式重置 1s settle | L313-L322 |
| `isFastFlipping()` | private fun — 2s 窗口内翻页 ≥4 次判定快速连续翻动 | L325-L329 |
| `renderPageToCacheComputeSize(pageIndex, ratio)` | private fun — tracked by singleRenderJob，渲染后标记 scale=1f | L331-L350 |
| `preloadAround(center, forceFullRes=false)` | public fun — 快速翻动模式：**当前页同步优先渲染**，窗口内其余缺失页并行 0.6x Q85（跳过升级保证速度）；正常模式：**中心页 0.35x Q70 快速预览先行 → 全尺寸 Q95 替换（replacePage：Coil 预热后换 URI）**，next Q95、±1 Q90 缺失**或 pageScales≠1f** 时全尺寸渲染升级，2-3 页 Q85@0.6x、≥4 页 Q80@0.4x 仅缺失时渲染；淘汰同步清理 pageScales | L352-L464 |
| `docCacheKey` | private val getter — pdfUri path hash (cache file namespacing) | L466-L467 |
| `renderPage(pageIndex, pageW, pageH, zoom, quality=95)` | private fun — JPEG into cacheDir，文件名含 `{pageW}x{pageH}`（尺寸变化→URI 变化→Coil 缓存失效） | L469-L480 |
| `updateViewportSize(width, height)` | private fun | L482-L494 |
| `setZoom(zoom)` | private fun | L496-L498 |
| `panBy(dx, dy)` | private fun | L500-L504 |
| `toggleUI()` | private fun | L506-L508 |
| `toggleThumbnails()` | private fun — 打开时关闭设置面板 | L510-L514 |
| `toggleShelf()` | private fun — 打开时关闭设置面板 | L516-L520 |
| `toggleSettings()` | private fun — 打开时关闭缩略图/谱架（三面板互斥） | L522-L531 |
| `setShelfSort(sort)` | private fun — 显式设置排序方式并重载谱架 | L533-L538 |
| `deleteShelfFile(uri)` | private fun — 删除文件 + 清理页面/缩略图缓存 + 删除当前打开谱子时回到空闲态 + 重载谱架 | L540-L569 |
| `setSpreadMode(spread)` | private fun — on enable, aligns current page to even index | L571-L578 |
| `loadShelfFiles()` | private fun — sorts by shelfSortBy, maps to ShelfFile（**图片文件即解码边界带 thumbAspect**）, generates PDF thumbnails per file（**回填 thumbnailUri+thumbAspect**） | L632-L667 |
| `imageAspect(uri)` | private fun — 图片宽/高比（inJustDecodeBounds，不加载位图） | L669-L676 |
| `generatePdfThumbnail(fileUri)` | private fun — renders page 0 at 200px PNG, caches by path hash, **返回 Pair<Uri, Float?>（含宽/高比）** | L677-L706 |
| `pngAspect(file)` | private fun — 缓存 PNG 宽/高比（inJustDecodeBounds） | L707-L716 |
| `renameShelfFile(oldUri, newName)` | private fun — renames file, refreshes shelf, reopens at same page | L637-L657 |
| `openShelfFile(uri)` | private fun — closes shelf, restores page via pageMap[name] | L659-L674 |
| `readPdfBookmarks(uri)` | private fun — pdfbox 读取 PDF 原生 Document Outline → List<PageBookmark>（颜色编码 "#RRGGBB|标题"） | L676-L697 |
| `writePdfBookmarks()` | private fun — 关渲染器 → pdfbox 重写 Document Outline（**按页码升序写入**）→ 保存 → 重开渲染器 → preloadAround | L699-L726 |
| `addBookmark(page, title, color)` | private fun — 追加书签（标题 ≤50 字）→ writePdfBookmarks | L728-L734 |
| `deleteBookmark(id)` | private fun — 移除书签 → writePdfBookmarks | L736-L739 |
| `renameBookmark(id, title)` | private fun — 重命名书签 → writePdfBookmarks | L741-L744 |
| `toggleTheme()` | private fun | L674-L676 |
| `toggleFace()` | private fun — syncs faceManager running/enabled with uiState.faceEnabled | L678-L687 |
| `faceFlip(dir)` | private fun — sets uiState.pendingFlip (consumed by Stage) | L689-L692 |
| `flipDone()` | private fun — clears pendingFlip | L694-L697 |
| `showFaceOverlay()` | private fun | L699-L702 |
| `hideFaceOverlay()` | private fun — hides + persists face prefs via faceRepo.save | L704-L710 |
| `checkUpdate(manual)` | private fun — **① version.json 静态清单优先（raw 静态文件不走 API 配额；不加查询频率限制）② 清单失败回退双源 API 并行查询**；仅取比当前新的候选（平手 Gitee 优先）；各源可达性写入 updateMessage；已下载未安装时直达 Downloaded+安装；manual 双失败显示 Error，自动静默回 Idle | L785-L823 |
| `downloadUpdate()` | private fun — **断点续传**（isDownloaded 快路径直达安装；downloadApk Range 续传，进度→downloadProgress），完成后 Downloaded + 自动调起 installUpdate() | L754-L780 |
| `installUpdate()` | private fun — 未授权 → 跳 unknown sources 授权页；已授权 → FileProvider 安装 Intent（下载完成自动调起 + 设置面板"立即安装"按钮均可触发） | L747-L760 |
| `startActivity(intent)` | private fun — runCatching 包装 | L791-L793 |
| `dismissUpdateDialog()` | private fun — 仅关弹窗（保留 Available 状态） | L795-L798 |
| `toggleVersionLog()` | private fun — 展开/收起本版本更新日志；首次展开按已安装版本 tag 从两平台拉取 release body | L800-L815 |
| `resetZoom()` | private fun | L817-L819 |
| `reset()` | private fun — 重建 ViewerState 保留版本字段 + cancelPageRendering() | L821-L835 |
| `reload()` | private fun | L837-L852 |
| `saveSession()` | private fun | L854-L874 |
| `getThumbnailUri(pageIndex)` | public fun | L876-L877 |
| `preloadPage(pageIndex)` | public fun（当前无调用方） | L879-L898 |
| `preloadThumbnails()` | private fun — PNG thumbs keyed by docCacheKey | L900-L924 |
| `restoreSession()` | private fun — accessibility check, restores mode/page/uris | L926-L951 |
| `onCleared()` | override fun — close pdfRenderer | L924-L928 |

---

### 14. data/model/ViewerState.kt (L1-L117)

| Element | Type | Lines |
|---|---|---|
| Package + import | — | L1-L3 |
| `ViewerState` | data class (36 fields) | L5-L42 |
| Fields: | mode, currentPage, pageCount, zoom, panOffsetX, panOffsetY, showUI, showThumbnails, showShelf, isDarkTheme, statusMessage, isLoading, fileName, pageUris, pageWidth, pageHeight, viewportWidth, viewportHeight, thumbnailsLoading, shelfFiles(emptyList()), shelfSortBy(ShelfSort.DATE), isSpreadMode(false), faceEnabled(false), faceActive(false), showFaceOverlay(false), pendingFlip(null), showSettings(false), appVersionName(""), appVersionCode(0), updateStatus(Idle), updateInfo(null), updateMessage(""), showUpdateDialog(false), showVersionLog(false), versionNotes(""), versionNotesLoading(false), downloadProgress(0), bookmarks(emptyList()) | L6-L41 |
| `ShelfSort` | enum (NAME, DATE) | L45 |
| `Mode` | sealed class | L47-L51 |
| — `Idle` | data object | L48 |
| — `Image` | data object | L49 |
| — `Pdf` | data object | L50 |
| `ViewerEvent` | sealed class | L54-L87 |
| — `FilesSelected(uris)` | data class | L55 |
| — `GoToPage(page)` | data class | L56 |
| — `NextPage` | data object | L57 |
| — `PrevPage` | data object | L58 |
| — `SetZoom(zoom)` | data class | L59 |
| — `PanBy(dx, dy)` | data class | L60 |
| — `UpdateViewportSize(width, height)` | data class | L61 |
| — `ToggleUI` | data object | L62 |
| — `ToggleThumbnails` | data object | L63 |
| — `ToggleTheme` | data object | L64 |
| — `ResetZoom` | data object | L65 |
| — `Reset` | data object | L66 |
| — `Reload` | data object | L67 |
| — `ToggleShelf` | data object | L68 |
| — `OpenShelfFile(uri)` | data class | L69 |
| — `RenameShelfFile(uri, newName)` | data class | L70 |
| — `SetShelfSort(sort)` | data class | L71 |
| — `DeleteShelfFile(uri)` | data class | L72 |
| — `SetSpreadMode(spread)` | data class | L73 |
| — `ToggleFace` | data object | L74 |
| — `ShowFaceOverlay` | data object | L75 |
| — `HideFaceOverlay` | data object | L76 |
| — `FlipDone` | data object | L77 |
| — `ToggleSettings` | data object | L78 |
| — `CheckUpdate` | data object | L79 |
| — `DownloadUpdate` | data object | L80 |
| — `InstallUpdate` | data object | L81 |
| — `DismissUpdateDialog` | data object | L82 |
| — `ToggleVersionLog` | data object | L83 |
| — `AddBookmark(page, title, color)` | data class | L84 |
| — `DeleteBookmark(id)` | data class | L85 |
| — `RenameBookmark(id, title)` | data class | L86 |
| `ShelfFile` | data class (5 fields) — name, uri, thumbnailUri, lastModified, **thumbAspect（缩略图宽/高比，瀑布流自适应）** | L95-L101 |
| Fields: | name(String), uri(Uri), thumbnailUri(Uri?), lastModified(Long) | L90-L93 |
| `UpdateInfo` | data class (5 fields) — tag, notes(摘要), fullNotes(完整更新日志), apkUrl, source | L96-L102 |
| Fields: | tag(L97), notes(L98), fullNotes(L99), apkUrl(L100), source(L101) | L97-L101 |
| `PageBookmark` | data class (4 fields) — id, page, title, color | L104-L110 |
| Fields: | id(L105), page(L106), title(L107), color(L108) | L105-L108 |
| `UpdateStatus` | enum (Idle, Checking, UpToDate, Available, Downloading, Downloaded, Error) | L112-L113 |

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

### 16. data/repository/SessionRepository.kt (L1-L112)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L10 |
| `Context.dataStore` | extension property ("session") | L12 |
| `SessionRepository(context)` | class | L14-L101 |
| Companion object — keys | KEY_MODE, KEY_CURRENT_PAGE, KEY_URIS, KEY_FILE_NAME, KEY_PAGE_MAP, **KEY_IMSLP_HISTORY + IMSLP_HISTORY_LIMIT(10)**, **KEY_IMSLP_DISCLAIMER_ACK** | L16-L26 |
| `SessionData` | nested data class (4 fields) | L28-L33 |
| `sessionFlow: Flow<SessionData?>` | val | L35-L42 |
| `saveSession(mode, currentPage, uris, fileName)` | suspend fun — also records fileName→currentPage into KEY_PAGE_MAP JSON | L44-L60 |
| `getPageMap(): Flow<Map<String, Int>>` | fun — parses KEY_PAGE_MAP JSON (per-file last-read page) | L63-L69 |
| `getImslpHistory(): Flow<List<String>>` | fun — 解析 KEY_IMSLP_HISTORY JSON 数组（最近在前） | L71-L78 |
| `addImslpHistory(query)` | suspend fun — 去重置顶、上限 10 条写回 | L80-L92 |
| `clearImslpHistory()` | suspend fun — remove KEY_IMSLP_HISTORY | L94-L96 |
| `getImslpDisclaimerAck(): Flow<Boolean>` | fun — IMSLP 免责声明是否已确认（默认 false=首次使用） | L101-L103 |
| `ackImslpDisclaimer()` | suspend fun — 置 true（永久生效） | L105-L107 |
| `clearSession()` | suspend fun | L109-L111 |

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

### 18. facer/FaceCamera.kt (L1-L135)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L43 |
| `F` / `E` / `L` / `B` | private val IntArray — landmark index pairs: face oval / eyes / lips / brows | L45-L48 |
| `FaceCamera(manager, visible, modifier)` | @Composable fun | L50-L134 |`n| 预览镜像显示层 | aspectRatio Box graphicsLayer scaleX=-1（仅 mirrored=true 时；位图+线条整体翻转，对齐不变，管线不动） | L111-L112 |
| Permission state + request launcher | camera permission, Toast on deny | L56-L59 |
| LaunchedEffect permission request | L61 |
| Early returns | no permission L63; model init failure L65-L68 | L63-L68 |
| `exec` + DisposableEffect | analyzer executor；绑定仅 ImageAnalysis（无 Preview use case/PreviewView），onDispose unbindAll + shutdown | L70-L94 |
| `state` collect + alpha animation | fade-in when visible | L96-L99 |
| `camModifier` | visible → fillMaxWidth 4:3; hidden → 1dp (keeps analyzer alive) | L101-L102 |
| 预览 Box | **送检位图 Image 即预览画面（WYSIWYG）**：容器 aspectRatio=frameWidth/Height（竖屏不失真），否则暗色"正在启动相机…"占位 | L104-L129 |
| — 位图 Image + Landmarks Canvas | 线条与画面同空间（px = lm.x*w, lm.y*h），横竖屏精确对齐 | L113-L128 |

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

### 20. facer/FaceRecognitionManager.kt (L1-L215)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L26 |
| `FaceRecognitionManager(context)` | class | L28-L215 |
| Companion `TAG` | const "MSV_FACE" | L29 |
| `FaceState` | data class — running, enabled, triggerMode, thresholds, mirrored, actionThreshold, actionActive, fps, scores, landmarks, status, frameWidth/Height（送检位图尺寸）, **previewImage（送检位图预览，WYSIWYG）** | L31-L39 |
| `Thresholds` | data class — blink, pucker, puckerBiasL, puckerBiasR | L38 |
| `GestureScores` | data class — lWink, rWink, lPucker, rPucker | L39 |
| `TriggerMode` | enum (WINK, PUCKER, BOTH) | L40 |
| `Gesture` | enum (LEFT_WINK, RIGHT_WINK, LEFT_PUCKER, RIGHT_PUCKER, NONE) | L41 |
| `_state` / `stateFlow` | MutableStateFlow / StateFlow<FaceState> | L43-L44 |
| `onGesture` | public var ((Gesture) -> Unit)? — invoked on main handler | L45 |
| `landmarker` + smoothing/hysteresis fields | private | L47-L50 |
| `init(): Boolean` | fun — loads face_landmarker.task, RunningMode.IMAGE, blendshapes on, 1 face | L52-L65 |
| `updateState / currentState / isReady` | fun | L67-L69 |
| `process(ip: ImageProxy)` | fun — decode → detect → blendshapes → gesture (800ms cooldown) → mainHandler callback；**每帧导出送检位图副本为 previewImage（消除 3 帧间隔的预览拖影）** | L77-L109 |
| `processBlendshapes(cats): Gesture` | private fun — EMA smooth + hysteresis + left/right bias, updates scores + actionActive | L100-L117 |
| `score / ema / hyst` | private fun — lookup, EMA smoothing, hysteresis state | L119-L127 |
| `decode(ip): Bitmap` | private fun — **逐行拷贝 YUV 平面（兼容 rowStride 填充，修复竖屏花屏识别失败）**→NV21→ARGB_8888, rotation + optional mirror | L131-L164 |
| `copyPlaneToNv21(...)` | private fun — 按 rowStride/pixelStride 逐行拷贝，无填充走整块快路径 | L166-L184 |
| `convertYuvToBitmap(nv21, w, h, out)` | private fun — BT.601 YUV→RGB pixel loop；**chroma 按平面式索引（V 平面/U 平面分离，与 decode 组装格式配套）——修复按 NV21 交错索引读平面数据导致的绿紫色块** | L189-L215 |
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

### 23. data/repository/UpdateRepository.kt (L1-L287)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L20 |
| `compareVersions(a, b)` | top-level fun — 逐段数值版本比较（短段补 0、容错 v 前缀）；单测 `app/src/test/java/com/music/msv/CompareVersionsTest.kt` | L25-L37 |
| `UpdateRepository(context)` | class | L39-L287 |
| Companion constants | GITEE_REPO_API / GITHUB_REPO_API / GITEE_LATEST / GITHUB_LATEST / **GITEE_RAW_VERSION / GITHUB_RAW_VERSION（静态清单 raw 地址）** / TIMEOUT_MS(8000) / APK_PREFIX("MSV-ScoreViewer") / UPDATE_DIR("update") / APK_MIME | L41-L52 |
| `VersionManifest` | nested data class — versionName/versionCode/apkUrl/source | L54-L59 |
| `fetchVersionManifest()` | fun — 读根目录 version.json 静态清单（**raw 静态文件不走 API 配额**）：Gitee raw 优先 GitHub raw 兜底 | L61-L81 |
| `installedVersionName` / `installedVersionCode` | val getter — PackageManager（API33 PackageInfoFlags 分支） | L51-L68 |
| `fetchGiteeLatest` / `fetchGitHubLatest` | fun — → fetchLatest | L70-L72 |
| `fetchLatest(url, isGitee)` | private fun — httpGetString → JSONObject；assets 按 `.apk`+前缀过滤（排除源码包）；notes=摘要 fullNotes=完整 body；失败返回 null | L75-L97 |
| `fetchTagNotes(tag)` | fun — 按版本 tag 查 release body（**Gitee 优先**，GitHub 兜底）供设置页"查看本版本更新日志" | L99-L101 |
| `fetchTagBody(url, isGitee)` | private fun — httpGetString → 解析 body | L103-L110 |
| `httpGetString(url, isGitee)` | private fun — HttpURLConnection GET（UA/超时/Accept 头）；**失败自动重试一次（仅快速失败 <2s，如限流 403；超时不重试防成倍等待）** | L112-L135 |
| `summarizeNotes(body, maxLen=300)` | fun — release body 摘要（去标题前缀/拼行/截断） | L128-L144 |
| `downloadApk(url, tag, onProgress)` | suspend fun — **应用内流式下载 + Range 断点续传**（206 续传/416 视为完整/失败保留断点/协作式取消） | L155-L198 |
| `remoteApkSize(url)` | private fun — HEAD 查询远端大小（**4s 短超时**，探测用途） | L200-L213 |
| `isDownloaded(url, tag)` | fun — 本地长度 == 远端长度（已完整下载） | L215-L221 |
| `apkFileName(tag)` / `updateApkFile(tag)` | fun — `update/msv-update-{tag}.apk`（外部专属目录） | L214-L220 |
| `cleanupDownloadedApk(installedVersion)` | fun — 升级成功后清理已装版本的历史安装包 | L222-L229 |
| `isInstallPermissionGranted()` | fun — canRequestPackageInstalls | L231 |
| `unknownSourcesIntent()` | fun — ACTION_MANAGE_UNKNOWN_APP_SOURCES 授权引导 | L233-L237 |
| `installIntent(tag)` | fun — FileProvider(`${applicationId}.fileprovider`) URI + ACTION_VIEW 安装 Intent | L239-L243 |

---

### 24. release/publish.ps1 — OTA 双平台发布脚本 (L1-L157)

### 25. data/model/ImslpModels.kt (L1-L9)

| Element | Type | Lines |
|---|---|---|
| `ImslpSearchResult` | data class — title/pageid/isComposer/snippet | L3-L9 |

### 26. data/repository/ImslpRepository.kt (L1-L275)

| Element | Type | Lines |
|---|---|---|
| `ImslpRepository` | class — IMSLP 搜索 + 官方页 WebView 下载仓库（2026-09 实测：免责 cookie 门 / 等待页 data-id / Bot Check 门禁） | L23-L274 |
| companion | BASE/API/TAG/超时/**WAIT_BUDGET_MS(16s 等待页轮询预算)**/USER_AGENT(仅 api.php 搜索用)/NON_WORK_PREFIXES | L25-L41 |
| `DownloadResult` | sealed class — Success(file)/BotCheck/Error(msg) | L43-L47 |
| `httpGetString(url)` | private fun — api.php GET + 免责 cookie | L49-L65 |
| `isNonWork(title)` | private fun | L67 |
| `cleanSnippet(raw)` | private fun — **搜索简介清洗**：丢模板参数行(\|Field=)/{{模板}}/[[链接]]、解码 HTML 实体；为空则 UI 不显示灰字区 | L69-L81 |
| `search(query)` | suspend — srnamespace=0 全文 + Category 命名空间作曲家分类（**两组请求 async 并行**，出结果等待约减半；snippet 经 cleanSnippet 清洗） | L82-L123 |
| `cookieHeader(url)` | private fun — **CookieManager 全量会话 cookie**（验证放行绑定会话）+ 免责 cookie 兜底 | L126-L136 |
| `openConn(url, ua)` | private fun — HttpURLConnection（UA/Cookie/Referer） | L138-L146 |
| `resolveUrl(u)` | private fun — 相对/协议相对地址 → 绝对地址 | L148-L154 |
| `nextUrlFromHtml(body)` | private fun — 非 PDF HTML 分类：门禁(Bot Check/Start Verification)→null 交 WebView 验证；**等待页 #sm_dl_wait data-id→真实下载直链（倒计时纯前端）**；meta refresh/location.href 兜底 | L156-L170 |
| `downloadUrl(url, ua, dest, onProgress)` | suspend — withContext(IO) 委托 downloadLoop（独立函数规避 K2 lambda 推断问题） | L172-L180 |
| `downloadLoop(url, ua, dest, onProgress)` | private suspend — **流式下载 + %PDF- 魔数校验**；非 PDF→分类跟进（重复 URL 按 2s 轮询至 16s 预算，覆盖站点匿名等待间隔）；进度回调 + 协作式取消（内层 lambda 用捕获的 Job.ensureActive） | L182-L274 |

### 27. ui/components/ImslpPanel.kt (L1-L880)

| Element | Type | Lines |
|---|---|---|
| `ImslpStep` | sealed interface — Search/Results(works,composers)/**Browse(url)** 步骤状态机 | L76-L80 |
| `pdfDisplayName(filename)` | private fun — 去 PMLP 编号前缀展示 | L83-L84 |
| `ImslpSearchField(...)` | private @Composable — **胶囊搜索框（BasicTextField 自绘 42dp，文字垂直居中；替代 OutlinedTextField 矮高度时文字被压出一半的问题）** | L91-L137 |
| `ImslpDialogState` | **class（ViewerViewModel 持有，冷启动重置）** — showDialog/step/lastResults/query/busy/statusText/downloadJob/webViewRef + 下载与门禁状态（downloadingUrl/downloadProgress/gateWait/gatePassed/gateFileUrl/gateRetry）+ pageProgress + **currentBrowseUrl（onPageFinished 实时记录，重开恢复最后浏览页）** + **resultsFromBrowse（Results 返回去向）** + **searchHistory（持久化历史）** + **disclaimerAccepted/showDisclaimer（IMSLP 首次免责声明）** | L88-L121 |
| `IMSLP_PAGE_JS` | private const — onPageFinished 注入脚本：**.pld 蜜罐清空 + #sm_dl_wait data-id 立即导航（跳过等待）并隐藏倒计时弹窗 + View 预览兜底（捕获点击 View → 2s 未渲染官方组件时用 www.peachnote.com 图片接口自绘可翻页预览，官方组件渲染后自动移除）** | L112-L206 |
| `IMSLP_AD_HOSTS` | private val — **广告/统计第三方域名清单**（gtag/Clarity/广告网络等），shouldInterceptRequest 拦截提速 | L208-L219 |
| `ImslpDialog(state, isDark, onImported, onSearchCommit, onClearHistory)` | @Composable Dialog — **响应式尺寸**：宽 Search 0.70/其余 0.94 屏宽，高 Search 随历史+状态行动态(封顶 0.5 屏)/Results 按结果数分档(0.35/0.60/0.88)/Browse 恒 0.88 屏高，**animateDpAsState(tween 300) 过渡** | L225-L785 |
| — goBack() | 取消下载/清理门禁态；**Results → resultsFromBrowse 时回 Browse(currentBrowseUrl)，否则回 Search（修复返回失效）**；Browse 内优先 WebView 历史回退 | L282-L311 |
| — doSearch(q, fromBrowse) | 搜索（仓库层并行）；有结果时 onSearchCommit 记录历史 | L314-L328 |
| — openComposer/openWork | 点击结果 → Browse 步（官方 Category 页 / 官方作品页，URL 编码，同步 currentBrowseUrl） | L330-L340 |
| — startInterceptedDownload(fileUrl, ua, isAutoRetry) | 拦截下载入口：末段解码文件名（仅 .pdf）→ downloadUrl 应用内下载（进度回调）；**BotCheck → gateFileUrl 记录 + WebView 加载门禁页（mtcaptcha）**；Success → onImported 导入谱架 | L343-L375 |
| — Search 步 | 搜索框（**矮 50dp + 大圆角 20dp 统一风格**）+ **最近搜索历史列表（点击直接搜索 / 清空按钮）**+ 说明文案 | L419-L487 |
| — Results 步 | 👤作曲家组 + 📄作品组 两列网格（点作曲家→官方 Category 页，点作品→官方作品页） | L489-L541 |
| — Browse 步·搜索框+进度区 | **顶部搜索框默认收起为小胶囊（省空间），点击展开输入行（AnimatedVisibility 过渡 + 自动聚焦），提交后收起**；页面加载进度条（pageProgress 1~99）+ 下载进度条（**官方页保持挂载**） | L543-L636 |
| — Browse 步·WebView | 官方页 AndroidView：**UA 不覆盖（设备默认）** + JS/DOM 存储/第三方 Cookie/混合内容/内置缩放 + **免责 cookie 预置**；onPageStarted 置 pageProgress=0；**shouldInterceptRequest 按 IMSLP_AD_HOSTS 拦截广告/统计资源**；shouldOverrideUrlLoading **拦截 /images/*.pdf 与 Special:Redirect/file/*.pdf → 应用内下载、外链跳系统浏览器**；**onPageFinished 记录 currentBrowseUrl + 注入 IMSLP_PAGE_JS**；onProgressChanged 更新加载进度；onCreateWindow 接管弹窗；DownloadListener 兜底；**onRelease 移除并 destroy()**；**factory loadUrl(currentBrowseUrl ?: s.url) 恢复浏览位置**；**pageProgress<100 时"加载中…"覆盖层** | L638-L752 |
| — Browse 步·门禁轮询 | LaunchedEffect 每 1200ms 读 body innerText，检出 "Bot Check Passed" → **自动重试下载（≤2 次，UA 取自 WebView）** | L754-L794 |
| — IMSLP 首次免责声明 | 弹窗打开且未确认 → AlertDialog 强调 IMSLP 版权法规（公有领域因国而异/遵守当地版权法/IMSLP 条款）；"我已阅读并同意"持久化，"暂不使用"关闭弹窗 | L383-L385 + L796-L828 |

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
