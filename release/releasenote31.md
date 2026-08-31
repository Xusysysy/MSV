# MSV 乐谱查看器 v2.2.8

## 修复

- **IMSLP 下载误报"响应非 PDF"**：
  - 200 响应的人机验证页此前从 errorStream 读取（恒为空）导致检测失效——现从正确流读取内容，Bot Check 可被正确识别并弹出验证引导
  - PDF 判定改为**魔数校验**（响应前缀 `%PDF-`），兼容服务端以 octet-stream 等非标准 Content-Type 返回的真实乐谱文件

versionCode=31, versionName=2.2.8
