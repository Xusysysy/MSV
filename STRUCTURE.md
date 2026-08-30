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
│   │   ├── TopBar.kt            ← Top control bar (shelf, page nav, thumbnails, theme, reset)
│   │   ├── Footer.kt            ← Status footer
│   │   ├── EmptyView.kt         ← Idle landing page with shelf button
│   │   ├── ThumbnailPanel.kt    ← Slide-in thumbnail grid (right)
│   │   ├── ShelfPanel.kt        ← Slide-in shelf panel (left), lists saved scores with thumbnails
│   │   └── LoadingOverlay.kt    ← Spinner overlay
│   └── screen/
│       └── ViewerScreen.kt      ← Root orchestrator, wires ViewModel → components
├── viewmodel/
│   └── ViewerViewModel.kt       ← Central state holder, MVVM, preload logic, face wiring
├── data/
│   ├── model/
│   │   └── ViewerState.kt       ← UI state data class + Mode sealed class + ViewerEvent sealed class
│   ├── repository/
│   │   ├── FileRepository.kt    ← SAF file access, local copy, MIME detection
│   │   └── SessionRepository.kt ← DataStore persistence (session + per-file page map)
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

### 6. ui/screen/ViewerScreen.kt (L1-L296)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L51 |
| `ViewerScreen(viewModel: ViewerViewModel)` | @Composable fun | L53-L296 |
| `state` | collectAsState | L55 |
| `filePickerLauncher` | rememberLauncherForActivityResult | L58-L64 |
| `openFilePicker` | local val lambda | L66-L68 |
| `showPageDialog` / `pageInput` / `showResetDialog` | mutableStateOf | L70-L72 |
| `isDark` / `appBg` | derived vals | L74-L75 |
| `isViewing` | derived val | L77 |
| Root Box | composable | L79-L295 |
| — Inner Box (shell/viewing conditional) | composable | L86-L235 |
| — — `Mode.Idle` → EmptyView (with onShelfClick) | composable | L100-L105 |
| — — `else` → Stage (+pendingFlip, onFlipDone) + LoadingOverlay | composable | L106-L134 |
| — — TopBar (AnimatedVisibility, slide+top, faceEnabled/faceActive/onFaceClick/onFaceLongClick) | composable | L137-L164 |
| — — BottomFooter (AnimatedVisibility, slide+bottom) | composable | L166-L178 |
| — — Thumbnail backdrop click Box | composable | L180-L189 |
| — — ThumbnailPanel (AnimatedVisibility, slide+right) | composable | L191-L205 |
| — — Shelf backdrop click Box | composable | L207-L216 |
| — — ShelfPanel (AnimatedVisibility, slide+left) | composable | L218-L234 |
| — Page jump AlertDialog | composable | L237-L265 |
| — Reset/Reload AlertDialog | composable | L267-L285 |
| — FaceRecognitionOverlay wiring (visible/onDismiss/onToggle/manager) | composable | L287-L294 |

---

### 7. ui/components/Stage.kt (L1-L406)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L45 |
| `invertColorMatrix` | private val ColorMatrix — dark-theme page inversion | L47-L54 |
| `Stage` | @Composable fun | L56-L406 |
| Parameters (22): | isDark, pageUris, currentPage, pageCount, pageWidth, pageHeight, zoom, panOffsetX, panOffsetY, isSpreadMode, pendingFlip(default=null), onCenterTap, onDoubleTap, onZoomChange, onPanChange, onNextPage, onPrevPage, onFlipDone(default={}), onViewportSizeChanged, onSpreadModeChanged, onPreloadAround(default={}), modifier(default=Modifier) | L57-L80 |
| `bg` | derived val background color | L81 |
| `stageWidth` / `stageHeight` | mutableStateOf(0) | L82-L83 |
| `currentZoom` | mutableFloatStateOf(zoom) | L84 |
| `transition` | Animatable(0f) | L85 |
| `scope` | rememberCoroutineScope | L86 |
| `flipJob` | mutableStateOf<Job?>(null) | L87 |
| `dragOffset` | mutableFloatStateOf(0f) | L88 |
| `isZoomed` / `pw` | derived vals | L90-L91 |
| `displaySize` | derived Pair? — fit-to-viewport display dimensions (null until measured) | L93-L107 |
| `autoSpreadMode` | derived Boolean — landscape + narrow pages | L109-L111 |
| LaunchedEffect(autoSpreadMode) | fires onSpreadModeChanged | L113-L115 |
| `currentIsZoomed` etc. | 8× rememberUpdatedState + displayW + flipUnit + touchSlop + pendingFlip | L117-L127 |
| LaunchedEffect(pageWidth, pageHeight) | resets transition to 0 | L129-L131 |
| `doFlip(dir, fromOffset, easing)` | local fun — spring flip; NonCancellable finally: snapTo(0) + onNextPage/onPrevPage + onFlipDone | L133-L160 |
| `doBounce(dir)` | local fun — boundary bounce animation | L162-L171 |
| LaunchedEffect(currentPendingFlip) | face-triggered pending flip → doFlip | L173-L180 |
| `pagesToShow` | derived val — visible page window (±5 single, ±6 spread), sorted descending | L182-L186 |
| Root Box | composable (gestures + rendering) | L188-L405 |
| — tap/drag awaitEachGesture pointerInput | 1/3 tap zones (flip/bounce/center tap), drag follow with boundary reversal | L197-L276 |
| — transform pointerInput (isZoomed guard) | pinch zoom + pan | L277-L285 |
| — Spread branch | left gap fill Box L295-L305, pages loop L307-L334, gradient edge masks L336-L367 | L290-L367 |
| — Single branch | pages loop with flip offset (AsyncImage, centered via displaySize) | L368-L404 |

---

### 8. ui/components/TopBar.kt (L1-L175)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L29 |
| `TopBar` | @Composable fun | L31-L175 |
| Parameters (15): | isDark, fileName(unused, kept for API compat), currentPage, pageCount, showPageNav, onShelfClick, onPageJumpClick, onThumbnailsClick, onResetClick, onThemeLongClick, faceEnabled(default=false), faceActive(default=false), onFaceClick(default={}), onFaceLongClick(default={}), modifier | L33-L49 |
| Local colors | bg, border, text, muted, divider, ctrlBg, ctrlBorder, accent | L50-L57 |
| Main Row | composable | L59-L174 |
| — Shelf button Row | icon + text, clickable (calls onShelfClick) | L70-L82 |
| — Page nav (if showPageNav) | page number display (click → jump dialog) + divider | L84-L108 |
| — Weight Spacer | L111 |
| — Action buttons (if showPageNav) | thumbnail, face, reset | L113-L173 |
| — — Thumbnail button ▦ | clickable | L115-L125 |
| — — Face button 👁 | click → ToggleFace; long-press → ShowFaceOverlay; red highlight when faceActive | L127-L156 |
| — — Reset button ↺ | click → reset dialog; long-press → toggle theme | L158-L172 |

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

### 13. viewmodel/ViewerViewModel.kt (L1-L669)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L29 |
| `ViewerViewModel(application)` | class : AndroidViewModel | L31-L669 |
| `fileRepo` | private val FileRepository | L33 |
| `sessionRepo` | private val SessionRepository | L34 |
| `pdfRenderer` | private val PdfPageRenderer | L35 |
| `faceManager` | public val FaceRecognitionManager | L36 |
| `faceRepo` | private val FaceRecognitionRepository | L37 |
| `_uiState` | private val MutableStateFlow | L39 |
| `uiState` | val StateFlow (public) | L40 |
| `imageUris` | private var List<Uri> | L42 |
| `pdfUri` | private var Uri? | L43 |
| `loadJob` / `preloadJob` | private var Job? | L44-L45 |
| `thumbnailCache` | private val ConcurrentHashMap<Int, Uri> | L46 |
| `pageMap` | private var Map<String, Int> — per-file last-read page (KEY_PAGE_MAP) | L47 |
| `init` block | pageMap collector + face prefs load + faceState→faceActive/faceEnabled sync + onGesture→faceFlip + restoreSession() | L49-L72 |
| `handleShareIntent(intent)` | public fun | L74-L103 |
| `onEvent(event)` | public fun (event dispatch, spread-aware page step) | L105-L136 |
| `handleFilesSelected(uris)` | private fun — imports file, then calls loadShelfFiles() if shelf is visible | L138-L164 |
| `openPdf(uri, name, restorePage=0)` | private fun | L166-L207 |
| `openImages(uris, name, initialPage=0)` | private fun | L209-L231 |
| `goToPage(page)` | private fun | L233-L241 |
| `renderPageToCacheComputeSize(pageIndex, ratio)` | private fun | L243-L261 |
| `preloadAround(center)` | public fun — tiered render: center/next Q95, ±1 Q90, 2-3 Q85@0.6x, ≥4 Q80@0.4x, evict outside window, loading gate | L263-L332 |
| `docCacheKey` | private val getter — pdfUri path hash (cache file namespacing) | L334-L335 |
| `renderPage(pageIndex, pageW, pageH, zoom, quality=95)` | private fun — JPEG into cacheDir | L337-L348 |
| `updateViewportSize(width, height)` | private fun | L350-L362 |
| `setZoom(zoom)` | private fun | L364-L366 |
| `panBy(dx, dy)` | private fun | L368-L372 |
| `toggleUI()` | private fun | L374-L376 |
| `toggleThumbnails()` | private fun | L378-L382 |
| `toggleShelf()` | private fun | L384-L388 |
| `toggleShelfSort()` | private fun — toggles NAME↔DATE, reloads shelf | L390-L395 |
| `setSpreadMode(spread)` | private fun — on enable, aligns current page to even index | L397-L404 |
| `loadShelfFiles()` | private fun — sorts by shelfSortBy, maps to ShelfFile, generates PDF thumbnails per file | L406-L435 |
| `generatePdfThumbnail(fileUri)` | private fun — renders page 0 at 200px PNG, caches by path hash | L437-L461 |
| `renameShelfFile(oldUri, newName)` | private fun — renames file, refreshes shelf, reopens at same page | L463-L483 |
| `openShelfFile(uri)` | private fun — closes shelf, restores page via pageMap[name] | L485-L498 |
| `toggleTheme()` | private fun | L500-L502 |
| `toggleFace()` | private fun — syncs faceManager running/enabled with uiState.faceEnabled | L504-L513 |
| `faceFlip(dir)` | private fun — sets uiState.pendingFlip (consumed by Stage) | L515-L518 |
| `flipDone()` | private fun — clears pendingFlip | L520-L523 |
| `showFaceOverlay()` | private fun | L525-L528 |
| `hideFaceOverlay()` | private fun — hides + persists face prefs via faceRepo.save | L530-L534 |
| `resetZoom()` | private fun | L536-L538 |
| `reset()` | private fun | L540-L547 |
| `reload()` | private fun | L549-L564 |
| `saveSession()` | private fun | L566-L586 |
| `getThumbnailUri(pageIndex)` | public fun | L588-L589 |
| `preloadPage(pageIndex)` | public fun | L591-L610 |
| `preloadThumbnails()` | private fun — PNG thumbs keyed by docCacheKey | L612-L636 |
| `restoreSession()` | private fun — accessibility check, restores mode/page/uris | L638-L663 |
| `onCleared()` | override fun | L665-L668 |

---

### 14. data/model/ViewerState.kt (L1-L71)

| Element | Type | Lines |
|---|---|---|
| Package + import | — | L1-L3 |
| `ViewerState` | data class (25 fields) | L5-L32 |
| Fields: | mode, currentPage, pageCount, zoom, panOffsetX, panOffsetY, showUI, showThumbnails, showShelf, isDarkTheme, statusMessage, isLoading, fileName, pageUris, pageWidth, pageHeight, viewportWidth, viewportHeight, thumbnailsLoading, shelfFiles(emptyList()), shelfSortBy(ShelfSort.DATE), isSpreadMode(false), faceEnabled(false), faceActive(false), showFaceOverlay(false), pendingFlip(null) | L6-L31 |
| `ShelfSort` | enum (NAME, DATE) | L34 |
| `Mode` | sealed class | L36-L40 |
| — `Idle` | data object | L37 |
| — `Image` | data object | L38 |
| — `Pdf` | data object | L39 |
| `ViewerEvent` | sealed class | L42-L65 |
| — `FilesSelected(uris)` | data class | L43 |
| — `GoToPage(page)` | data class | L44 |
| — `NextPage` | data object | L45 |
| — `PrevPage` | data object | L46 |
| — `SetZoom(zoom)` | data class | L47 |
| — `PanBy(dx, dy)` | data class | L48 |
| — `UpdateViewportSize(width, height)` | data class | L49 |
| — `ToggleUI` | data object | L50 |
| — `ToggleThumbnails` | data object | L51 |
| — `ToggleTheme` | data object | L52 |
| — `ResetZoom` | data object | L53 |
| — `Reset` | data object | L54 |
| — `Reload` | data object | L55 |
| — `ToggleShelf` | data object | L56 |
| — `OpenShelfFile(uri)` | data class | L57 |
| — `RenameShelfFile(uri, newName)` | data class | L58 |
| — `ToggleShelfSort` | data object | L59 |
| — `SetSpreadMode(spread)` | data class | L60 |
| — `ToggleFace` | data object | L61 |
| — `ShowFaceOverlay` | data object | L62 |
| — `HideFaceOverlay` | data object | L63 |
| — `FlipDone` | data object | L64 |
| `ShelfFile` | data class (3 fields) | L67-L71 |
| Fields: | name(String), uri(Uri), thumbnailUri(Uri?) | L68-L70 |

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
