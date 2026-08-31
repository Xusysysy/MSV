# MSV OTA 双平台发布脚本（PowerShell 5.1+）
# 用法: powershell -ExecutionPolicy Bypass -File release\publish.ps1 [-SkipBuild] [-NoUpload] [-SkipTag] [-NoteFile <path>]
# 流程: 解析版本 → assembleRelease → 复制 APK → tag(=versionName) 推送双远程
#       → GitHub gh release → Gitee REST API（token: $env:MSV_GITEE_TOKEN）
param(
    [string]$NoteFile = "",
    [switch]$SkipBuild,
    [switch]$NoUpload,
    [switch]$SkipTag
)

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Owner = "lin-xiaochuan"; $Repo = "msv"
$GhRepo = "Xusysysy/MSV"
$GiteeApi = "https://gitee.com/api/v5/repos/$Owner/$Repo"
$GiteeAttachField = "file"   # 实测 Gitee attach_files 要求字段名 file（单数），用 files 会报 file is missing
$JavaHome = "D:\software\AndroidStudio\jbr"

function Fail($msg) { Write-Host "[publish] $msg" -ForegroundColor Red; exit 1 }

# ── 1. 版本解析（单一事实源 = app/build.gradle.kts）──
$gradleText = Get-Content app\build.gradle.kts -Raw
if ($gradleText -notmatch 'versionName\s*=\s*"([^"]+)"') { Fail "无法解析 versionName" }
$verName = $Matches[1]
if ($gradleText -notmatch 'versionCode\s*=\s*(\d+)') { Fail "无法解析 versionCode" }
$verCode = [int]$Matches[1]
$tag = $verName   # tag 与 versionName 必须一致（OTA 版本比较的前提）
$apkName = "MSV-ScoreViewer-v$verName-release.apk"
Write-Host "[publish] 版本: v$verName (versionCode=$verCode) tag=$tag"

# ── 1.5 同步根目录 version.json（应用走 raw 静态清单检测，须提交推送才能被读取）──
$verJson = @{
    versionName  = $verName
    versionCode  = $verCode
    apkUrlGitee  = "https://gitee.com/$Owner/$Repo/releases/download/$tag/$apkName"
    apkUrlGitHub = "https://github.com/$GhRepo/releases/download/$tag/$apkName"
} | ConvertTo-Json
[IO.File]::WriteAllText("$PSScriptRoot\..\version.json", $verJson, (New-Object System.Text.UTF8Encoding($false)))
git add version.json
git commit -m "chore: sync version.json to v$verName" | Out-Null
if ($LASTEXITCODE -eq 0) { git push origin | Out-Null; git push gitee | Out-Null }

# ── 2. 发布说明 ──
if (-not $NoteFile) { $NoteFile = "release\releasenote$verCode.md" }
$body = $tag
if (Test-Path $NoteFile) {
    # 用 ReadAllText 而非 Get-Content：后者会给字符串附加 PSPath 等扩展属性，ConvertTo-Json 会把 body 序列化成对象导致 Gitee 报 body is invalid
    $body = [IO.File]::ReadAllText($NoteFile, [Text.Encoding]::UTF8)
}
else { Write-Warning "[publish] 未找到发布说明 $NoteFile，body 将仅含版本号" }

# ── 3. 构建 ──
if (-not $SkipBuild) {
    Write-Host "[publish] 构建 assembleRelease ..."
    $env:JAVA_HOME = $JavaHome
    & .\gradlew.bat assembleRelease
    if ($LASTEXITCODE -ne 0) { Fail "构建失败" }
}
$apkOut = "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apkOut)) { Fail "未找到构建产物 $apkOut（如已构建过请加 -SkipBuild 前先手动构建）" }
New-Item -ItemType Directory -Force -Path release | Out-Null
Copy-Item $apkOut "release\$apkName" -Force
Write-Host "[publish] APK 已复制: release\$apkName"

# ── 4. tag + 推送双远程（永不 force；-SkipTag 用于中断后续跑上传）──
if (-not $SkipTag) {
git rev-parse -q --verify "refs/tags/$tag" | Out-Null
if ($LASTEXITCODE -eq 0) { Fail "tag $tag 已存在，请人工处理（永不 force）" }
git tag $tag
if ($LASTEXITCODE -ne 0) { Fail "打 tag 失败" }
git push origin $tag
if ($LASTEXITCODE -ne 0) { Fail "推送 tag 到 origin 失败" }
git push gitee $tag
if ($LASTEXITCODE -ne 0) { Write-Warning "推送 tag 到 gitee 失败（继续）" }
Write-Host "[publish] tag $tag 已推送双远程"
}

if ($NoUpload) { Write-Host "[publish] -NoUpload：跳过 release 上传"; exit 0 }

$ghFail = $false; $giteeFail = $false

# ── 5. GitHub（gh CLI）──
try {
    $ghCmd = Get-Command gh -ErrorAction SilentlyContinue
    $ghExe = if ($ghCmd) { $ghCmd.Source } else { "C:\Program Files\GitHub CLI\gh.exe" }
    if (-not (Test-Path $ghExe)) { throw "未找到 gh CLI" }
    # PS5.1: EAP=Stop 时 2>$null 会把 native stderr 变成终止错误，需临时降级 EAP
    $ErrorActionPreference = "Continue"
    & $ghExe release view $tag -R $GhRepo 2>$null | Out-Null
    $viewExit = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    if ($viewExit -eq 0) {
        & $ghExe release upload $tag "release\$apkName" -R $GhRepo --clobber
        if ($LASTEXITCODE -ne 0) { throw "upload 失败" }
        Write-Host "[GitHub] release $tag 已存在，APK 已覆盖上传" -ForegroundColor Green
    } else {
        $noteArg = @()
        if (Test-Path $NoteFile) { $noteArg = @("--notes-file", $NoteFile) }
        & $ghExe release create $tag "release\$apkName" -R $GhRepo --title "MSV 乐谱查看器 v$verName" @noteArg
        if ($LASTEXITCODE -ne 0) { throw "create 失败" }
        Write-Host "[GitHub] release $tag 已创建" -ForegroundColor Green
    }
} catch { Write-Warning "[GitHub] 发布失败: $_"; $ghFail = $true }

# ── 6. Gitee（REST API）──
$token = $env:MSV_GITEE_TOKEN
if (-not $token) {
    Write-Warning @"
[Gitee] 未检测到环境变量 MSV_GITEE_TOKEN，已跳过自动发布。手动上传步骤：
  1. 浏览器打开 https://gitee.com/$Owner/$Repo/releases/new
  2. 标签: $tag   标题: MSV 乐谱查看器 v$verName   描述: 粘贴 $NoteFile 内容
  3. 上传附件: release\$apkName
  （或设置后重跑:  `$env:MSV_GITEE_TOKEN = "<Gitee 私人令牌>" ）
"@
    $giteeFail = $true
} else {
    try {
        $relId = $null; $hasApk = $false
        try {
            $existing = Invoke-RestMethod -Uri "$GiteeApi/releases/tags/$tag`?access_token=$token" -Method Get
            $relId = $existing.id
            $hasApk = ($existing.assets | Where-Object { $_.name -eq $apkName }) -ne $null
        } catch {
            $code = 0
            try { $code = [int]$_.Exception.Response.StatusCode } catch {}
            if ($code -ne 404) { throw "查询 release 失败 (HTTP $code)" }
        }
        if (-not $relId) {
            $payload = @{
                tag_name = $tag
                name = "MSV 乐谱查看器 v$verName"
                body = $body
                target_commitish = "master"
                prerelease = $false
            } | ConvertTo-Json
            $created = Invoke-RestMethod -Uri "$GiteeApi/releases?access_token=$token" -Method Post `
                -ContentType "application/json;charset=utf-8" -Body ([Text.Encoding]::UTF8.GetBytes($payload))
            $relId = $created.id
            Write-Host "[Gitee] release $tag 已创建 (id=$relId)" -ForegroundColor Green
        }
        if ($hasApk) {
            Write-Host "[Gitee] APK 附件已存在，跳过上传" -ForegroundColor Green
        } else {
            # PS 5.1 无 Invoke-RestMethod -Form，用 System.Net.Http multipart
            Add-Type -AssemblyName System.Net.Http
            $client = New-Object System.Net.Http.HttpClient
            $form = New-Object System.Net.Http.MultipartFormDataContent
            $bytes = [IO.File]::ReadAllBytes((Resolve-Path "release\$apkName"))
            $fileContent = New-Object System.Net.Http.ByteArrayContent(, $bytes)
            $form.Add($fileContent, $GiteeAttachField, $apkName)
            $resp = $client.PostAsync("$GiteeApi/releases/$relId/attach_files?access_token=$token", $form).Result
            if (-not $resp.IsSuccessStatusCode) {
                throw "附件上传失败 HTTP $($resp.StatusCode): $($resp.Content.ReadAsStringAsync().Result)"
            }
            Write-Host "[Gitee] APK 已上传" -ForegroundColor Green
        }
    } catch { Write-Warning "[Gitee] 发布失败: $_"; $giteeFail = $true }
}

# ── 7. 汇总 ──
Write-Host ""
Write-Host "=== 发布结果 v$verName ==="
if ($ghFail) { Write-Host "GitHub: ❌ 失败" -ForegroundColor Red }
else { Write-Host "GitHub: ✅ https://github.com/$GhRepo/releases/tag/$tag" -ForegroundColor Green }
if ($giteeFail) { Write-Host "Gitee:  ⚠ 未能自动完成（见上方指引）" -ForegroundColor Yellow }
else { Write-Host "Gitee:  ✅ https://gitee.com/$Owner/$Repo/releases/tag/$tag" -ForegroundColor Green }
if ($ghFail -or $giteeFail) { exit 1 } else { exit 0 }
