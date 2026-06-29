# MSV — Music Score Viewer for Android

乐谱查看器 Android 客户端。支持 PDF 与图片乐谱的本地查看，具备完整的触控手势、动画系统和毛玻璃主题。

## Features

- PDF / 多图乐谱本地查看
- 翻页动画、缩放平移、缩略图导航
- 暗色/亮色毛玻璃主题
- 触控手势：滑动翻页、双指缩放、点击边缘翻页
- 24 小时会话恢复
- 全尺寸移动端适配（手机、平板、折叠屏）

## Build

```powershell
$env:JAVA_HOME = "D:\software\AndroidStudio\jbr"
.\gradlew.bat assembleDebug
```

APK 输出：`app\build\outputs\apk\debug\app-debug.apk`

## Tech Stack

- Kotlin + Jetpack Compose + Material 3
- Coil 3 图片加载
- Android PdfRenderer PDF 渲染
- DataStore 持久化
- minSdk 26 · targetSdk 36
