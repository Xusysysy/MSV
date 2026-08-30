# MSV 乐谱查看器 v2.1.4

## 修复

- **修复横屏双页模式快速往返翻页时页面闪回旧页的问题**：快速后翻再立即前翻时，被接替的旧翻页动画其结束回调仍会复位翻页方向并重置动画位置，打断新动画导致页面短暂闪回 85/86 再恢复 87/88；现在结束回调仅在自身仍是当前动画时才复位

## 新增

- **设置页新增"查看本版本更新日志"**：版本信息下方可展开查看当前已安装版本对应的 Release 完整日志（Gitee 优先、GitHub 兜底，离线时提示未找到）

## 技术变更

- Stage doFlip 结束回调增加 flipJob 身份守卫（coroutineContext[Job] 比对）
- UpdateRepository 提取 httpGetString 公共请求方法，新增 fetchTagNotes 按版本 tag 查询 release body
- versionCode=12, versionName=2.1.4
