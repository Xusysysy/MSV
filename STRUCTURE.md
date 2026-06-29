# STRUCTURE.md — MSV Android

## Directory Map

```
app/src/main/java/com/music/msv/
├── MainActivity.kt              ← Entry point, edge-to-edge
├── ui/
│   ├── theme/
│   │   ├── Color.kt             ← Glassmorphism color tokens (dark + light)
│   │   ├── Theme.kt             ← MSVTheme composable
│   │   ├── Type.kt              ← Type scale
│   │   └── Shape.kt             ← Rounded shapes
│   ├── components/
│   │   ├── GlassSurface.kt      ← Shared glassmorphism surface
│   │   ├── TopBar.kt            ← Top control bar
│   │   ├── Stage.kt             ← Main viewport + gestures
│   │   ├── Footer.kt            ← Status footer
│   │   ├── EmptyView.kt         ← Idle landing page
│   │   ├── ThumbnailPanel.kt    ← Slide-in thumbnail grid
│   │   └── LoadingOverlay.kt    ← Spinner overlay
│   └── screen/
│       └── ViewerScreen.kt      ← Root orchestrator
├── viewmodel/
│   └── ViewerViewModel.kt       ← Central state holder
├── data/
│   ├── model/
│   │   └── ViewerState.kt       ← UI state sealed class
│   ├── repository/
│   │   ├── FileRepository.kt    ← SAF file access
│   │   └── SessionRepository.kt ← 24h DataStore persistence
│   └── pdf/
│       └── PdfRenderer.kt       ← Android PdfRenderer wrapper
└── util/
    └── Animations.kt            ← Animation constants & specs
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
