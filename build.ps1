$ErrorActionPreference = "Stop"
$D    = "C:\Users\chongya\opencode-tools"
$APP  = "$D\app"
$JAVA = "$D\jdk\jdk-17.0.20.1+1\bin"
$BT   = "$D\bt\android-14"
$AJ   = "C:\Android\sdk\platforms\android-34\android.jar"

$env:JAVA_HOME = "$D\jdk\jdk-17.0.20.1+1"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Output "== 1. generate project =="
python "$D\gen_modern_meta.py"

Write-Output "== 2. aapt2 compile =="
Remove-Item -Recurse -Force "$APP\build" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$APP\build" | Out-Null
& "$BT\aapt2.exe" compile --dir "$APP\res" -o "$APP\build\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Output "== 3. aapt2 link =="
& "$BT\aapt2.exe" link -o "$APP\build\base.apk" -I "$AJ" --manifest "$APP\AndroidManifest.xml" -R "$APP\build\res.zip" --java "$APP\build\gen" --min-sdk-version 29 --target-sdk-version 34 -A "$APP\assets" --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Output "== 4. javac =="
$srcs = @()
$srcs += (Get-ChildItem -Recurse "$APP\src" -Filter *.java | ForEach-Object { $_.FullName })
$srcs += (Get-ChildItem -Recurse "$APP\stubs" -Filter *.java | ForEach-Object { $_.FullName })
$srcs += (Get-ChildItem -Recurse "$APP\build\gen" -Filter *.java | ForEach-Object { $_.FullName })
New-Item -ItemType Directory -Force "$APP\build\classes" | Out-Null
& "$JAVA\javac.exe" -encoding UTF-8 -source 1.8 -target 1.8 -nowarn -Xlint:-options -cp "$AJ;$D\libxposed_jar\classes.jar" -d "$APP\build\classes" $srcs
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Output "== 5. d8 (app classes only) =="
New-Item -ItemType Directory -Force "$APP\build\dex" | Out-Null
$cls = Get-ChildItem -Recurse "$APP\build\classes\com" -Filter *.class | ForEach-Object { $_.FullName }
& "$BT\d8.bat" --min-api 29 --lib "$AJ" --lib "$D\libxposed_jar\classes.jar" --output "$APP\build\dex" $cls
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Output "== 6. package apk =="
python "$D\pack_apk.py"

Write-Output "== 7. zipalign =="
Remove-Item -Force "$APP\build\aligned.apk" -ErrorAction SilentlyContinue
& "$BT\zipalign.exe" -f -p 4 "$APP\build\signed_src.apk" "$APP\build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Output "== 8. keystore + sign =="
if (-not (Test-Path "$D\enhance.jks")) {
    $eap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & "$JAVA\keytool.exe" -genkeypair -keystore "$D\enhance.jks" -alias a -keyalg RSA -keysize 2048 -validity 10000 -storepass 123456 -keypass 123456 -dname "CN=hermes, OU=dev, O=hermes, L=cn, ST=cn, C=CN" 2>&1 | Out-Null
    $ErrorActionPreference = $eap
}
$eap2 = $ErrorActionPreference
$ErrorActionPreference = "Continue"
& "$BT\apksigner.bat" sign --ks "$D\enhance.jks" --ks-pass pass:123456 --key-pass pass:123456 --out "$D\BrowserEnhance-api102.apk" "$APP\build\aligned.apk"
$code = $LASTEXITCODE
$ErrorActionPreference = $eap2
if ($code -ne 0) { throw "apksigner failed" }

Write-Output "== 9. verify =="
$eap3 = $ErrorActionPreference
$ErrorActionPreference = "Continue"
& "$BT\apksigner.bat" verify --print-certs "$D\BrowserEnhance-api102.apk" 2>&1 | Select-Object -First 4
$ErrorActionPreference = $eap3
Write-Output ("apk size: " + (Get-Item "$D\BrowserEnhance-api102.apk").Length)
