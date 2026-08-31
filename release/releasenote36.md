# MSV 乐谱查看器 v2.3.3

## 修复

- **IMSLP 验证后下载改为 WebView 原生捕获**：验证通过后由 WebView 自己发起下载请求（其浏览上下文刚完成验证，环境完全一致），应用通过 DownloadListener 捕获最终下载地址与原生 User-Agent 落盘——不再依赖 HttpURLConnection 探测（放行凭证对独立请求可能不可见）
- 新增"浏览器打开"兜底按钮：直接调起系统浏览器打开 IMSLP 文件页（浏览器环境验证与下载必定可用），下载后经"导入乐谱"导入

versionCode=36, versionName=2.3.3
