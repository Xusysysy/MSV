# MSV 乐谱查看器 v2.2.2

## 修复

- **缩放模式五项修正**：
  - 进入缩放模式现在正确隐藏顶栏与底部通知（根因：Stage 未接线 onZoomModeEnter/Exit 回调）
  - 缩放模式下翻页正确禁用（同根因）
  - 双指缩放松手后单指拖动不再跳变（指针重锚定，保持与松手前对齐）
  - 双击屏幕中央按固定比例缩放：<2x 放大到 2x，≥2x 恢复 1x 并退出缩放模式；正常模式中央双击直接进入缩放并放大到 2x
- **更新弹窗日志折叠**：发现新版本弹窗的更新日志默认折叠，点击展开后可滚动（不再挤占设置页面）

## 技术变更

- ViewerScreen Stage 调用补接 onZoomModeEnter/onZoomModeExit
- Stage 手势循环：wasPinching 指针重锚定、双击步进缩放（正常/缩放模式双路径）
- versionCode=25, versionName=2.2.2
