# MSV 乐谱查看器 v2.1.7

## 新增

- **缩略图书签系统（PDF 原生 Document Outline 持久化）**：
  - 长按页面缩略图呼出添加书签窗口：命名（上限 50 字）+ RGB 颜色选择（8 色色板）
  - 书签以横向半透明长条显示在缩略图窗口左侧，左侧半圆、右侧延伸、宽度随名字长度自适应，文本完整显示
  - 点击书签跳转对应页码；长按书签弹出重命名/删除菜单
  - 书签与缩略图同控件组：同时加载/隐藏、相同动画；书签区域无额外背景，支持上下滚动
  - 书签写入 PDF 原生 Document Outline（颜色编码 `#RRGGBB|标题`），每次修改/打开乐谱时写入/读取，跨设备随文件携带

## 修复

- **面部识别线条左右镜像（横竖屏）**：送检位图的镜像时机由"旋转前"修正为"旋转后"（与前置摄像头预览呈现方向一致）
- **竖屏线条偏大/失真**：放弃 PreviewView + Canvas 双层变换映射，改为直接以送检位图作为预览画面（所见即所检），相机仅绑定 ImageAnalysis 分析流；预览容器锁定送检位图宽高比，竖屏不再失真，线条与画面同空间精确对齐

## 技术变更

- 新增 pdfbox-android 2.0.27.0 依赖（Apache 2.0，PDF Outline 读写）；PDFBoxResourceLoader 于 VM 初始化
- FaceRecognitionManager 每 3 帧导出送检位图副本（ImageBitmap）供预览，旧帧交由 GC 回收
- FaceCamera 重写：移除 AndroidView/PreviewView，绑定 ImageAnalysis 单用例
- ThumbnailPanel 重构为 Row（书签栏 + 缩略图列），新增长按添加书签/书签长按菜单/添加与重命名弹窗
- versionCode=15, versionName=2.1.7
