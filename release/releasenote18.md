# MSV 乐谱查看器 v2.1.10

## 修复

- **检查更新明显变慢**：上一版起检测新增的"已下载比对"探测与两源请求为串行执行，且超时长达 8 秒——国内访问 GitHub 慢/超时时每次检查要干等十几秒；现两源**并行请求**（耗时取最大而非相加），已下载比对的 HEAD 探测改用 **4 秒短超时**
- **单一来源结果不再"静默"**：检测完成后反馈各源可达性（如 `Gitee:✗ · GitHub:✓`），"只有 GitHub"一类结果的原因一目了然（典型原因：Gitee 匿名 API 按公网 IP 限流，多设备共享同一出口 IP 时易触发）
- **网络请求失败自动重试一次**：仅针对快速失败（限流 403/5xx 类，<2s 内返回）；超时类失败不重试，避免成倍等待

## 技术变更

- checkUpdate 双源 async 并行 + srcStatus 可达性反馈（写入 updateMessage，设置面板可见）
- UpdateRepository.httpGetString 重试逻辑（快速失败才重试）、remoteApkSize 4s 短超时
- versionCode=18, versionName=2.1.10
