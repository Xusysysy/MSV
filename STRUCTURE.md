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
│   │   ├── TopBar.kt            ← Top control bar (upload, page nav, thumbnails, theme, reset)
│   │   ├── Footer.kt            ← Status footer
│   │   ├── EmptyView.kt         ← Idle landing page with upload button
│   │   ├── ThumbnailPanel.kt    ← Slide-in thumbnail grid
│   │   └── LoadingOverlay.kt    ← Spinner overlay
│   └── screen/
│       └── ViewerScreen.kt      ← Root orchestrator, wires ViewModel → components
├── viewmodel/
│   └── ViewerViewModel.kt       ← Central state holder, MVVM, preload logic
├── data/
│   ├── model/
│   │   └── ViewerState.kt       ← UI state data class + Mode sealed class + ViewerEvent sealed class
│   ├── repository/
│   │   ├── FileRepository.kt    ← SAF file access, local copy, MIME detection
│   │   └── SessionRepository.kt ← 24h DataStore persistence
│   └── pdf/
│       └── PdfPageRenderer.kt   ← Android PdfRenderer wrapper, LRU bitmap cache
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
| minSdk | 26 (Android 8.0) | User preference |

## Navigation

Single-screen app — no Navigation component. State-based content switching via `ViewerViewModel.uiState`.

---

## Per-File Breakdown

### 1. MainActivity.kt (L1-L45)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L15 |
| `MainActivity` | class : ComponentActivity | L17-L45 |
| `shareIntentState` | private val MutableState<Intent?> | L19 |
| `onCreate(savedInstanceState)` | override fun | L21-L38 |
| `onNewIntent(intent)` | override fun | L40-L44 |

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

### 6. ui/screen/ViewerScreen.kt (L1-L251)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L48 |
| `ViewerScreen(viewModel: ViewerViewModel)` | @Composable fun | L50-L250 |
| `state` | collectAsState | L52 |
| `filePickerLauncher` | rememberLauncherForActivityResult | L55-L61 |
| `openFilePicker` | local val lambda | L63-L65 |
| `showPageDialog` / `pageInput` / `showResetDialog` | mutableStateOf | L67-L69 |
| `isDark` / `appBg` / `isViewing` | derived vals | L71-L74 |
| Root Box | composable | L76-L248 |
| — Inner Box (shell/viewing conditional) | composable | L83-L197 |
| — — `Mode.Idle` → EmptyView | composable | L97-L98 |
| — — `else` → Stage | composable | L100-L128 |
| — — TopBar (AnimatedVisibility, slide+top) | composable | L133-L155 |
| — — BottomFooter (AnimatedVisibility, slide+bottom) | composable | L158-L169 |
| — — ThumbnailPanel (AnimatedVisibility, slide+right) | composable | L172-L187 |
| — — Thumbnail backdrop click Box | composable | L190-L196 |
| — Page jump AlertDialog | composable | L200-L227 |
| — Reset/Reload AlertDialog | composable | L230-L247 |

---

### 7. ui/components/Stage.kt (L1-L237)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L42 |
| `SWIPE_THRESHOLD` | private const val (0.30f) | L44 |
| `Stage` | @Composable fun | L46-L237 |
| Parameters (17): | isDark, pageUris, currentPage, pageCount, pageWidth, pageHeight, zoom, panOffsetX, panOffsetY, onCenterTap, onDoubleTap, onZoomChange, onPanChange, onNextPage, onPrevPage, onViewportSizeChanged, onPreloadAround(default={}), modifier(default=Modifier) | L47-L65 |
| `bg` | derived val background color | L67 |
| `stageWidth` | mutableStateOf(0) | L68 |
| `currentZoom` | mutableFloatStateOf(zoom) | L69 |
| `transition` | Animatable(0f) | L70 |
| `scope` | rememberCoroutineScope | L71 |
| `flipJob` | mutableStateOf<Job?>(null) | L72 |
| `dragOffset` | mutableFloatStateOf(0f) | L73 |
| `isZoomed` / `pw` | derived vals | L75-L76 |
| `currentIsZoomed` etc. | 4× rememberUpdatedState | L78-L81 |
| `baseX(pageIndex)` | local fun — static x offset | L83-L86 |
| `pageX(pageIndex)` | local fun — animated x offset | L88-L96 |
| `doFlip(dir, fromOffset, easing)` | local fun — page flip animation | L98-L117 |
| `pagesToShow` | derived val — visible page window (±3) | L119-L122 |
| `pageSizeModifier` | derived Modifier | L124-L128 |
| Root Box | composable (gestures + rendering) | L131-L240 |
| — `.pointerInput(pageCount)` (outer) | coroutineScope: drag detector + tap detector, key=pageCount for cold start init | L138-L209 |
| — `.pointerInput(isZoomed)` (inner) | detectTransformGestures (zoom 0.5x–8x + pan) | L210-L218 |
| — `for (pageIndex in pagesToShow)` | page Box with AsyncImage (no background, white-filled PDF bitmaps) | L220-L237 |

---

### 8. ui/components/TopBar.kt (L1-L162)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L28 |
| `TopBar` | @Composable fun | L29-L162 |
| Parameters (11): | isDark, fileName, currentPage, pageCount, showPageNav, onUploadClick, onPageJumpClick, onThumbnailsClick, onThemeClick, onResetClick, modifier | L31-L41 |
| Local colors | bg, border, text, muted, divider, ctrlBg, ctrlBorder, accent | L43-L50 |
| Main Row | composable | L52-L161 |
| — Upload button Box | icon + text, clickable | L63-L75 |
| — Page nav (if showPageNav) | page number display + divider | L77-L101 |
| — Weight Spacer | L104 |
| — File name Text | L107-L116 |
| — Action buttons (if showPageNav) | thumbnail, theme, reset | L118-L160 |

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
| Parameters (3): | isDark(default=true), onUploadClick, modifier | L33-L35 |
| Local colors | muted, accent, text, bg, border | L37-L41 |
| Root Box | centered Column | L43-L88 |
| — Icon Text ("🎼") | L54-L57 |
| — Title Text ("乐谱查看器") | L58-L63 |
| — Description Text | L64-L69 |
| — Upload button Row | L71-L86 |

---

### 11. ui/components/ThumbnailPanel.kt (L1-L137)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L38 |
| `ThumbnailPanel` | @Composable fun | L40-L137 |
| Parameters (7): | isDark, pageCount, currentPage, getThumbnailUri, onPageSelected, onClose, modifier | L42-L48 |
| Local colors | panelBg, panelBorder, itemBg, itemBorder, itemActiveBg, itemActiveBorder, muted | L50-L56 |
| Column root | composable | L58-L136 |
| — Close button Box | L66-L82 |
| — LazyVerticalGrid (2 cols) | L85-L135 |
| — — itemsIndexed (pageIndex → thumbnail item) | L92-L134 |
| — — — Thumbnail AsyncImage | L112-L124 |
| — — — Page number Text | L125-L132 |

---

### 12. ui/components/LoadingOverlay.kt (L1-L44)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L17 |
| `LoadingOverlay` | @Composable fun | L19-L44 |
| Parameters (3): | isDark(default=true), visible(default=true), modifier | L21-L23 |
| Early return | if (!visible) return | L25 |
| Local colors | bg, accent, track | L27-L29 |
| Box overlay | CircularProgressIndicator centered | L31-L43 |

---

### 13. viewmodel/ViewerViewModel.kt (L1-L436)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L22 |
| `ViewerViewModel(application)` | class : AndroidViewModel | L24-L436 |
| `fileRepo` | private val FileRepository | L26 |
| `sessionRepo` | private val SessionRepository | L27 |
| `pdfRenderer` | private val PdfPageRenderer | L28 |
| `_uiState` | private val MutableStateFlow | L30 |
| `uiState` | val StateFlow (public) | L31 |
| `imageUris` | private var List<Uri> | L33 |
| `pdfUri` | private var Uri? | L34 |
| `loadJob` | private var Job? | L35 |
| `thumbnailCache` | private val ConcurrentHashMap | L36 |
| `init` block | restoreSession() | L38-L40 |
| `handleShareIntent(intent)` | public fun | L42-L71 |
| `onEvent(event)` | public fun (event dispatch) | L73-L89 |
| `handleFilesSelected(uris)` | private fun | L91-L116 |
| `openPdf(uri, name, restorePage=0)` | private fun | L118-L159 |
| `openImages(uris, name, initialPage=0)` | private fun | L161-L185 |
| `goToPage(page)` | private fun | L187-L195 |
| `renderPageToCacheComputeSize(pageIndex, ratio)` | private fun | L197-L217 |
| `preloadAround(center)` | public fun — renders center page FIRST (immediate UI update), then ±3 surrounding pages in background | L219-L252 |
| `renderPage(pageIndex, pageW, pageH, zoom)` | private fun | L254-L264 |
| `updateViewportSize(width, height)` | private fun | L266-L278 |
| `setZoom(zoom)` | private fun | L280-L282 |
| `panBy(dx, dy)` | private fun | L284-L288 |
| `toggleUI()` | private fun | L290-L292 |
| `toggleThumbnails()` | private fun | L294-L298 |
| `toggleTheme()` | private fun | L300-L302 |
| `resetZoom()` | private fun | L304-L306 |
| `reset()` | private fun | L308-L315 |
| `reload()` | private fun | L317-L332 |
| `saveSession()` | private fun | L334-L354 |
| `getThumbnailUri(pageIndex)` | public fun | L356-L357 |
| `preloadPage(pageIndex)` | public fun (single page preload, skips if already cached) | L359-L378 |
| `preloadThumbnails()` | private fun | L380-L403 |
| `restoreSession()` | private fun | L405-L430 |
| `onCleared()` | override fun | L432-L435 |

---

### 14. data/model/ViewerState.kt (L1-L46)

| Element | Type | Lines |
|---|---|---|
| Package + import | — | L1-L3 |
| `ViewerState` | data class (17 fields) | L5-L24 |
| Fields: | mode(Mode.Idle), currentPage(0), pageCount(0), zoom(1f), panOffsetX(0f), panOffsetY(0f), showUI(true), showThumbnails(false), isDarkTheme(true), statusMessage(""), isLoading(false), fileName(""), pageUris(emptyMap()), pageWidth(0), pageHeight(0), viewportWidth(0), viewportHeight(0), thumbnailsLoading(false) | L6-L23 |
| `Mode` | sealed class | L26-L30 |
| — `Idle` | data object | L27 |
| — `Image` | data object | L28 |
| — `Pdf` | data object | L29 |
| `ViewerEvent` | sealed class | L32-L46 |
| — `FilesSelected(uris)` | data class | L33 |
| — `GoToPage(page)` | data class | L34 |
| — `NextPage` | data object | L35 |
| — `PrevPage` | data object | L36 |
| — `SetZoom(zoom)` | data class | L37 |
| — `PanBy(dx, dy)` | data class | L38 |
| — `UpdateViewportSize(width, height)` | data class | L39 |
| — `ToggleUI` | data object | L40 |
| — `ToggleThumbnails` | data object | L41 |
| — `ToggleTheme` | data object | L42 |
| — `ResetZoom` | data object | L43 |
| — `Reset` | data object | L44 |
| — `Reload` | data object | L45 |

---

### 15. data/repository/FileRepository.kt (L1-L71)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L7 |
| `FileRepository(context)` | class | L9-L71 |
| `docsDir` | private val property (getter) | L11-L12 |
| `getFileName(uri): String` | fun | L14-L27 |
| `isPdf(fileName): Boolean` | fun | L29-L30 |
| `isImage(fileName): Boolean` | fun | L32-L37 |
| `takePersistablePermission(uri)` | fun | L39-L46 |
| `takePersistablePermissions(uris)` | fun | L48-L50 |
| `copyToLocal(uri, fileName): Uri?` | fun | L52-L63 |
| `getLocalFile(fileName): Uri?` | fun | L65-L68 |
| `openInputStream(uri)` | fun | L70 |

---

### 16. data/repository/SessionRepository.kt (L1-L55)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L9 |
| `Context.dataStore` | extension property | L11 |
| `SessionRepository(context)` | class | L13-L55 |
| Companion object — keys | KEY_MODE, KEY_CURRENT_PAGE, KEY_URIS, KEY_FILE_NAME | L15-L20 |
| `SessionData` | nested data class (4 fields) | L22-L27 |
| `sessionFlow: Flow<SessionData?>` | val | L29-L36 |
| `saveSession(mode, currentPage, uris, fileName)` | suspend fun | L38-L50 |
| `clearSession()` | suspend fun | L52-L54 |

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
