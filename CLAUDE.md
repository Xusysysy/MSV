# CLAUDE.md

**Repository1(Github)**: https://github.com/Xusysysy/MSV.git
**Repository1(Gitee)**: https://gitee.com/lin-xiaochuan/msv.git
Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 0. 喵 Rule (ABSOLUTE — NEVER SKIP)

**Every sentence you output MUST start with "喵".** This includes responses, tool descriptions, code explanations, questions, and summaries. No exceptions. If you output 5 sentences, all 5 start with 喵. This is a hard requirement to verify CLAUDE.md compliance.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

## 5. APK Location After Build

**After every successful build, output the APK path. Default to debug build (`assembleDebug`). Only use release build (`assembleRelease`) when the user explicitly requests it.**

```
喵APK 位置: app\build\outputs\apk\debug\app-debug.apk
```

## 6. Auto Commit & Push (BOTH Platforms) After Every Change

**After every change (any file modification), no matter how small, commit and push to BOTH `origin` (GitHub) and `gitee` (Gitee):**

- If there are uncommitted changes, create a commit with a concise message summarizing the changes.
- Then run `git push origin` AND `git push gitee`.
- Never force push. If push fails, report the error and continue.
- Do not wait for the user to ask — this is automatic.

## 7. Build Environment

**Windows only — no WSL.** Use `.\gradlew.bat` from project root. Set JAVA_HOME before every build (PowerShell sets it per-process):

```powershell
$env:JAVA_HOME = "D:\software\AndroidStudio\jbr"; .\gradlew.bat assembleDebug
```

**JDK path**: `D:\software\AndroidStudio\jbr` (JetBrains Runtime bundled with Android Studio).  
**IDE**: Android Studio.  
**First build** of a session takes ~1 minute (Dex, etc.) due to cold Gradle daemon. Set `timeout_ms = 300000` (5 min) to avoid abort. Subsequent builds reuse configuration cache and finish faster.  
**Release build** only when user explicitly requests `assembleRelease`.

## 8. Prefer Edit Over Write + Sync STRUCTURE.md

**Prefer modifying files with the Edit tool rather than rewriting entire files. Only use Write when creating new files or when the scope of changes exceeds 50% of the file.**

**ABSOLUTE MANDATE — STRUCTURE.md is the single source of truth for code structure. You MUST:**

1. **Before every task**: Read `STRUCTURE.md` first to understand the code layout, then only read the specific files you need to modify. Do NOT read unrelated files.

2. **After EVERY change** (any file modification, no matter how small): Immediately update `STRUCTURE.md` to reflect the changes. This includes:
   - New/removed classes, functions, callbacks, events
   - Changed line ranges for any modified section
   - Updated descriptions of component responsibilities
   - New/removed import dependencies

3. **Line-range precision**: Every documented class, function, composable, and logical block MUST have its line range noted as `L{start}-{end}`. The STRUCTURE.md must be precise enough that anyone can locate any piece of logic without reading the source.

4. **Browse via STRUCTURE.md**: Whenever browsing or exploring the project, read `STRUCTURE.md` directly — do NOT read files one by one.

**STRUCTURE.md format**: Top-level directory tree → per-file breakdown with line ranges for every significant element (classes, functions, composables, sealed classes, events, key constants, init blocks, nested components).

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

## 9. Gesture Modifier Safety (CRITICAL)

**`detectTransformGestures` internally calls `event.changes.consume()` on ALL pointer events (including single-finger). This will break `detectHorizontalDragGestures` and `detectTapGestures` if placed in the same `pointerInput` chain or if always-active. Do NOT use `pointerInput(Unit)` with `detectTransformGestures` — it will consume all events and freeze/block other gesture detectors.**

**Known-safe approach for pinch zoom**: keep `pointerInput(isZoomed)` + `if (isZoomed)` guard so the transform detector ONLY activates when already zoomed. To enable initial pinch, use a separate lightweight raw `awaitPointerEvent` detector that ONLY fires for 2+ fingers and bumps `onZoomChange(1.2f)` to wake the transform detector. The raw detector must NOT call `consume()`.

**If gestures break after changes**: immediately `git revert` and test the previous commit before attempting another approach. Multiple failed gesture attempts in a row suggest the approach is fundamentally flawed — stop and ask for guidance.

## 10. OTA 双平台发布流程 (Release Sync)

**每次版本修改并打包后，必须同步发布到 GitHub 和 Gitee 两平台的 Release（用户指令：以后与 GitHub 同步 release）。**

流程：

1. 更新 `app/build.gradle.kts` 的 `versionCode` (+1) 与 `versionName`。**tag 与 versionName 必须一致**（应用内 OTA 版本比较按 tag 逐段数值比较）。**同 commit 更新根目录 `version.json`**（应用走 raw 静态清单检测，未同步会导致检测不到新版本；脚本运行时也会自动同步并提交）。
2. 撰写发布说明 `release/releasenote{versionCode}.md`（Markdown，作为两平台 release body）。
3. 提交并推送代码（规则 6），然后运行发布脚本：
   ```powershell
   powershell -ExecutionPolicy Bypass -File release\publish.ps1
   ```
   脚本自动：`assembleRelease` → 复制 APK 到 `release/` → 打 tag(=versionName) + 推送双远程 → GitHub `gh release create/upload --clobber` → Gitee REST API 创建 release + 上传附件。
4. Gitee 上传依赖环境变量 `MSV_GITEE_TOKEN`（已配置在用户级，永不写入仓库）。缺失时脚本会打印手动上传指引并跳过 Gitee。
5. 干跑参数：`-SkipBuild`（复用已构建 APK）、`-NoUpload`（只打 tag 不发 release）、`-SkipTag`（tag 已打过后断点续传上传步骤）。
6. 发布后验证：两平台 release 页面可见新版本 + APK 附件；旧版本包冷启动应弹更新窗。

注意事项：
- release APK 使用 debug 签名（build.gradle.kts）——换签名密钥会导致用户无法覆盖安装，OTA 断链。
- `publish.ps1` 必须保持 **UTF-8 with BOM** 编码（PowerShell 5.1 无 BOM 时中文会破坏解析）；用工具重写该文件后需重新加 BOM。
- 脚本幂等：GitHub 已存在则 `--clobber` 重传；Gitee 已存在则跳过创建只补传附件；本地 tag 已存在会报错退出（永不 force）。
- 发布完成后 STRUCTURE.md 行号若因本次代码改动失效，必须同步更新。