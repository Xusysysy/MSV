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

### 6. ui/screen/ViewerScreen.kt (L1-L275)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L48 |
| `ViewerScreen(viewModel: ViewerViewModel)` | @Composable fun | L50-L275 |
| `state` | collectAsState | L52 |
| `filePickerLauncher` | rememberLauncherForActivityResult | L55-L61 |
| `openFilePicker` | local val lambda | L63-L65 |
| `showPageDialog` / `pageInput` / `showResetDialog` | mutableStateOf | L67-L69 |
| `isDark` / `appBg` / `isViewing` | derived vals | L71-L74 |
| Root Box | composable | L76-L273 |
| — Inner Box (shell/viewing conditional) | composable | L83-L223 |
| — — `Mode.Idle` → EmptyView (with onShelfClick) | composable | L97-L102 |
| — — `else` → Stage | composable | L104-L132 |
| — — TopBar (AnimatedVisibility, slide+top, onShelfClick) | composable | L138-L160 |
| — — BottomFooter (AnimatedVisibility, slide+bottom) | composable | L163-L174 |
| — — ThumbnailPanel (AnimatedVisibility, slide+right) | composable | L176-L201 |
| — — Thumbnail backdrop click Box | composable | L178-L185 |
| — — ShelfPanel (AnimatedVisibility, slide+left) | composable | L204-L220 |
| — — Shelf backdrop click Box | composable | L204-L212 |
| — Page jump AlertDialog | composable | L226-L253 |
| — Reset/Reload AlertDialog | composable | L256-L273 |

---

### 7. ui/components/Stage.kt (L1-L320)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L44 |
| `SWIPE_THRESHOLD` | private const val (0.30f) | L46 |
| `Stage` | @Composable fun | L48-L320 |
| Parameters (19): | isDark, pageUris, currentPage, pageCount, pageWidth, pageHeight, zoom, panOffsetX, panOffsetY, isSpreadMode, onCenterTap, onDoubleTap, onZoomChange, onPanChange, onNextPage, onPrevPage, onViewportSizeChanged, onSpreadModeChanged, onPreloadAround(default={}), modifier(default=Modifier) | L49-L68 |
| `bg` | derived val background color | L70 |
| `stageWidth` | mutableStateOf(0) | L71 |
| `stageHeight` | mutableStateOf(0) | L72 |
| `currentZoom` | mutableFloatStateOf(zoom) | L73 |
| `transition` | Animatable(0f) | L74 |
| `scope` | rememberCoroutineScope | L75 |
| `flipJob` | mutableStateOf<Job?>(null) | L76 |
| `dragOffset` | mutableFloatStateOf(0f) | L77 |
| `isZoomed` / `pw` | derived vals | L89-L90 |
| `displaySize` | derived Pair — fit-to-viewport display dimensions maintaining aspect ratio | L91-L105 |
| `autoSpreadMode` | derived Boolean — landscape + narrow pages | L107-L110 |
| `spreadW` / `spreadOffsetX` | derived Float — spread total width and left offset | L114-L115 |
| `currentIsZoomed` etc. | 5× rememberUpdatedState + displayW + flipUnit | L117-L123 |
| `baseX(pageIndex)` | local fun — static x offset (uses displayW) | L125-L128 |
| `pageX(pageIndex)` | local fun — animated x offset | L130-L138 |
| `doFlip(dir, fromOffset, easing)` | local fun — page flip animation (spring-based, uses flipUnit) | L140-L164 |
| `doBounce(dir)` | local fun — boundary bounce animation | L166-L175 |
| `pagesToShow` | derived val — visible page window (±3 single, ±6 spread) | L177-L182 |
| Root Box | composable (gestures + rendering, centered display) | L184-L295 |
| — spread mask Box | left gap fill when spread centered | L297-L306 |
| — `for (pageIndex in pagesToShow)` | page Box with AsyncImage, centered via displaySize | L308-L326 |

---

### 8. ui/components/TopBar.kt (L1-L162)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L28 |
| `TopBar` | @Composable fun | L29-L162 |
| Parameters (11): | isDark, fileName, currentPage, pageCount, showPageNav, onShelfClick, onPageJumpClick, onThumbnailsClick, onThemeClick, onResetClick, modifier | L31-L41 |
| Local colors | bg, border, text, muted, divider, ctrlBg, ctrlBorder, accent | L43-L50 |
| Main Row | composable | L52-L161 |
| — Shelf button Box | icon + text, clickable (calls onShelfClick) | L63-L75 |
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
| Parameters (3): | isDark(default=true), onShelfClick, modifier | L33-L35 |
| Local colors | muted, accent, text, bg, border | L37-L41 |
| Root Box | centered Column | L43-L88 |
| — Icon Text ("🎼") | L54-L57 |
| — Title Text ("乐谱查看器") | L58-L63 |
| — Description Text | L64-L69 |
| — Upload button Row | L71-L86 |

---

### 11. ui/components/ThumbnailPanel.kt (L1-L159)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L42 |
| `invertColorMatrix` | private val ColorMatrix | L44-L51 |
| `ThumbnailPanel` | @Composable fun | L53-L159 |
| Parameters (7): | isDark, pageCount, currentPage, getThumbnailUri, onPageSelected, onClose, modifier | L54-L61 |
| Local colors | panelBg, panelBorder, itemBg, itemBorder, itemActiveBg, itemActiveBorder, muted | L63-L69 |
| Column root | composable | L71-L158 |
| — Close button Box | L78-L95 |
| — gridState + LaunchedEffect scroll-to-current | L98-L103 |
| — LazyVerticalGrid (2 cols, state=gridState) | L105-L157 |
| — — itemsIndexed (pageIndex → thumbnail item) | L113-L156 |
| — — — Thumbnail AsyncImage | L133-L146 |
| — — — Page number Text | L147-L154 |

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

### 13. viewmodel/ViewerViewModel.kt (L1-L530)

| Element | Type | Lines |
|---|---|---|
| Package + imports | — | L1-L22 |
| `ViewerViewModel(application)` | class : AndroidViewModel | L24-L530 |
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
| `onEvent(event)` | public fun (event dispatch) | L73-L91 |
| `handleFilesSelected(uris)` | private fun — imports file, then calls loadShelfFiles() if shelf is visible | L94-L120 |
| `openPdf(uri, name, restorePage=0)` | private fun | L120-L161 |
| `openImages(uris, name, initialPage=0)` | private fun | L163-L187 |
| `goToPage(page)` | private fun | L189-L197 |
| `renderPageToCacheComputeSize(pageIndex, ratio)` | private fun | L199-L219 |
| `preloadAround(center)` | public fun | L221-L254 |
| `renderPage(pageIndex, pageW, pageH, zoom)` | private fun | L256-L266 |
| `updateViewportSize(width, height)` | private fun | L268-L280 |
| `setZoom(zoom)` | private fun | L282-L284 |
| `panBy(dx, dy)` | private fun | L286-L290 |
| `toggleUI()` | private fun | L292-L294 |
| `toggleThumbnails()` | private fun | L296-L300 |
| `toggleShelf()` | private fun | L305-L309 |
| `toggleShelfSort()` | private fun — toggles NAME↔DATE, reloads shelf | L311-L315 |
| `loadShelfFiles()` | private fun — sorts by shelfSortBy, maps to ShelfFile, generates PDF thumbnails via uri-key (not index) | L317-L338 |
| `generatePdfThumbnail(fileUri)` | private fun — renders page 0 at 200px, caches by filePath hash | L340-L363 |
| `renameShelfFile(oldUri, newName)` | private fun — renames file in docsDir, refreshes shelf | L365-L377 |
| `openShelfFile(uri)` | private fun — closes shelf, loads file by URI | L379-L386 |
| `toggleTheme()` | private fun | L366-L368 |
| `resetZoom()` | private fun | L370-L372 |
| `reset()` | private fun | L380-L387 |
| `reload()` | private fun | L389-L404 |
| `saveSession()` | private fun | L406-L426 |
| `getThumbnailUri(pageIndex)` | public fun | L428-L429 |
| `preloadPage(pageIndex)` | public fun | L431-L450 |
| `preloadThumbnails()` | private fun | L452-L475 |
| `restoreSession()` | private fun | L477-L502 |
| `onCleared()` | override fun | L504-L507 |

---

### 14. data/model/ViewerState.kt (L1-L62)

| Element | Type | Lines |
|---|---|---|
| Package + import | — | L1-L3 |
| `ViewerState` | data class (21 fields) | L5-L28 |
| Fields: | mode, currentPage, pageCount, zoom, panOffsetX, panOffsetY, showUI, showThumbnails, showShelf, isDarkTheme, statusMessage, isLoading, fileName, pageUris, pageWidth, pageHeight, viewportWidth, viewportHeight, thumbnailsLoading, shelfFiles(emptyList()), shelfSortBy(ShelfSort.DATE), isSpreadMode(false) | L6-L27 |
| `ShelfSort` | enum (NAME, DATE) | L29 |
| `Mode` | sealed class | L31-L35 |
| — `Idle` | data object | L32 |
| — `Image` | data object | L33 |
| — `Pdf` | data object | L34 |
| `ViewerEvent` | sealed class | L37-L55 |
| — `FilesSelected(uris)` | data class | L38 |
| — `GoToPage(page)` | data class | L39 |
| — `NextPage` | data object | L40 |
| — `PrevPage` | data object | L41 |
| — `SetZoom(zoom)` | data class | L42 |
| — `PanBy(dx, dy)` | data class | L43 |
| — `UpdateViewportSize(width, height)` | data class | L44 |
| — `ToggleUI` | data object | L45 |
| — `ToggleThumbnails` | data object | L46 |
| — `ToggleTheme` | data object | L47 |
| — `ResetZoom` | data object | L48 |
| — `Reset` | data object | L49 |
| — `Reload` | data object | L50 |
| — `ToggleShelf` | data object | L51 |
| — `OpenShelfFile(uri)` | data class | L52 |
| — `RenameShelfFile(uri, newName)` | data class | L53 |
| — `ToggleShelfSort` | data object | L54 |
| — `SetSpreadMode(spread)` | data class | L55 |
| `ShelfFile` | data class (3 fields) | L57-L62 |
| Fields: | name(String), uri(Uri), thumbnailUri(Uri?) | L59-L61 |

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
