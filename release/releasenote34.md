# MSV 乐谱查看器 v2.3.1

## 修复

- **IMSLP 人机验证成功后自动继续下载**：验证成功页是瞬时的（通过后页面会自动重载出新挑战，看起来像"闪回"）——现在每秒检测 WebView 页面状态，捕获到验证成功（Bot Check Passed / verify successfully）后 1.5 秒自动继续下载，无需再手动点击重试
- 自动续传最多尝试 2 次（防止验证未生效时无限循环）；手动"重试下载"按钮始终可用

versionCode=34, versionName=2.3.1
