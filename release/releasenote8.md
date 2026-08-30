# MSV 乐谱查看器 v2.1

## 新增功能

- **OTA 在线更新**：应用内检测 GitHub/Gitee 两平台最新 Release（Gitee 优先），发现新版本弹窗提醒并引导更新
- **设置页面**：顶栏新增 ⚙ 按钮入口，显示版本信息、版权信息与"检查更新"按钮
- **应用内下载**：新版本 APK 通过系统下载管理器下载（通知栏进度），完成后自动调起系统安装器；未授予"安装未知应用"权限时引导跳转授权页
- **冷启动自动检查**：每次进入应用静默检查新版本，有更新时弹窗提示，无网络时静默跳过不打扰
- **版本号逐段数值比较**：正确处理 2.0 / 2.0.1 / 2.10 等版本关系，防止降级误报
- **一键发布流水线**：release/publish.ps1 自动构建、打 tag、同步发布 APK 到 GitHub 与 Gitee 两平台 Release

## 技术变更

- 新增 `UpdateRepository`：HttpURLConnection 匿名查询两平台 release API（零第三方依赖），assets 按 `.apk` 过滤源码包
- 新增 `SettingsPanel`：右侧滑入设置面板，视觉规格与缩略图面板一致，三面板互斥（设置/缩略图/谱架）
- 顶栏动作区新增 ⚙ 按钮（▦ 👁 ↺ ⚙），既有按钮位置与长按语义不变
- AndroidManifest 新增 INTERNET / REQUEST_INSTALL_PACKAGES 权限与 FileProvider（`${applicationId}.fileprovider`）
- DownloadManager 下载去重（同 URL 任务清理）+ 下载完成广播（RECEIVER_NOT_EXPORTED）+ FileProvider 调起安装
- 升级覆盖安装依赖同签名（release 使用 debug 签名），升级成功后自动清理历史安装包
- versionCode=8, versionName=2.1
