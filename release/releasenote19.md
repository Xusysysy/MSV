# MSV 乐谱查看器 v2.1.11

## 修复

- **修复竖屏向前翻页时"前一页"闪现一帧的问题**：书签/防闪烁图层（ScorePageImage）的显示状态此前未与页码绑定，缩略图窗口滑动时渲染槽位被复用，旧 URI 垫底层在新页位置短暂显示前一页内容；现显示状态以页码键控（remember(pageIndex)），换页即重置——垫底防闪烁能力仅在同页升级时生效，不再跨页串位；横屏与既有动画效果不受影响

## 技术变更

- Stage.ScorePageImage 状态 remember 增加 pageIndex 键
- versionCode=19, versionName=2.1.11
