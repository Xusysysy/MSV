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

**STRUCTURE.md format**: Top-level directory tree → per-file breakdown with line ranges for every significant element (classes, functions, composables, sealed classes, events, key constants, init blocks, nested components).

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.