# MSV 乐谱查看器 v2.1.12

## 修复

- **移除呼出缩略图时的主页面模糊效果**（用户反馈效果不好），恢复主页面清晰呈现
- **修复书签文字黑描边与白填充不对齐的问题**：黑描边层改用 `matchParentSize` 与白填充层强制同尺寸同位置，实现像素级对齐的黑边效果（Compose 原生 `TextStyle.drawStyle = Stroke` 仅支持单次描边或填充，双层叠加为官方推荐做法，非手搓方案）
- **修复 v2.1.8 引入的书签不可见与缩略图面板偏移回归**：Box 图层结构的缩略图面板已正确 CenterEnd 对齐，书签栏位于其下一图层

## 新增

- **版本静态清单（version.json）**：应用检查更新优先读取仓库根目录 `version.json` 静态文件（Gitee raw / GitHub raw），**不走 API 配额通道，彻底规避匿名限流 403**；清单读取失败自动回退原双源 API 查询；按用户要求**未加查询频率限制**，便于调试
- 发布脚本自动同步 version.json 并提交推送（保证 raw 指向最新版本）

## 技术变更

- UpdateRepository 新增 VersionManifest 与 fetchVersionManifest()（Gitee raw 优先、GitHub raw 兜底）
- checkUpdate 调整为清单优先、API 兜底两级策略；保留各源可达性反馈与断点续传/已下载直达安装
- versionCode=20, versionName=2.1.12
