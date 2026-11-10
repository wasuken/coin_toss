#!/usr/bin/env python3
"""インストール済みSDKとJDKだけでデバッグ署名付きAPKを生成する。"""
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def run(*args):
    """作業ディレクトリを固定し、コマンド失敗時はビルドを停止する。"""
    subprocess.run([str(arg) for arg in args], cwd=ROOT, check=True)


def main():
    """リソース、Java、DEXをパッケージ化し、整列・署名・検証する。"""
    if not os.environ.get("COIN_TOSS_KEYSTORE_PASSWORD"):
        raise SystemExit("環境変数 COIN_TOSS_KEYSTORE_PASSWORD に署名鍵のパスワードを設定してください。")
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or Path.home() / "Android")
    tools = sdk / "build-tools" / os.environ.get("ANDROID_BUILD_TOOLS", "36.0.0")
    android = sdk / "platforms/android-36/android.jar"
    if not tools.is_dir() or not android.is_file():
        raise SystemExit("Android SDK Platform 36 と Build Tools 36.0.0 が必要です。README.mdを参照してください。")
    build = ROOT / "build"
    work = build / "intermediates"
    if work.exists():
        shutil.rmtree(work)
    for folder in (work / "generated", work / "classes", work / "dex", build / "outputs/apk"):
        folder.mkdir(parents=True, exist_ok=True)
    run(tools / "aapt2", "compile", "--dir", ROOT / "app/src/main/res", "-o", work / "resources.zip")
    ET.register_namespace("android", "http://schemas.android.com/apk/res/android")
    manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml")
    manifest.getroot().set("package", "jp.cointoss.app")
    manifest.write(work / "AndroidManifest.xml", encoding="utf-8", xml_declaration=True)
    run(tools / "aapt2", "link", "-o", work / "unsigned.apk", "-I", android,
        "--manifest", work / "AndroidManifest.xml", "--java", work / "generated",
        "--rename-manifest-package", "jp.cointoss.app", "--version-code", "1", "--version-name", "1.0",
        work / "resources.zip")
    sources = sorted((ROOT / "app/src/main/java").rglob("*.java")) + sorted((work / "generated").rglob("*.java"))
    run("javac", "-encoding", "UTF-8", "--release", "17", "-classpath", android, "-d", work / "classes", *sources)
    run("jar", "cf", work / "classes.jar", "-C", work / "classes", ".")
    run(tools / "d8", "--lib", android, "--min-api", "26", "--output", work / "dex", work / "classes.jar")
    with zipfile.ZipFile(work / "unsigned.apk", "a") as apk:
        for dex in sorted((work / "dex").glob("*.dex")):
            apk.write(dex, dex.name, compress_type=zipfile.ZIP_DEFLATED)
    run(tools / "zipalign", "-f", "-p", "4", work / "unsigned.apk", work / "aligned.apk")
    key = build / "debug.keystore"
    if not key.exists():
        run("keytool", "-genkeypair", "-keystore", key, "-storepass:env", "COIN_TOSS_KEYSTORE_PASSWORD", "-keypass:env", "COIN_TOSS_KEYSTORE_PASSWORD",
            "-alias", "androiddebugkey", "-dname", "CN=Android Debug,O=Android,C=JP", "-keyalg", "RSA",
            "-keysize", "2048", "-validity", "10000", "-noprompt")
    output = build / "outputs/apk/coin-toss-debug.apk"
    run(tools / "apksigner", "sign", "--ks", key, "--ks-key-alias", "androiddebugkey",
        "--ks-pass", "env:COIN_TOSS_KEYSTORE_PASSWORD", "--key-pass", "env:COIN_TOSS_KEYSTORE_PASSWORD", "--out", output, work / "aligned.apk")
    run(tools / "apksigner", "verify", "--verbose", output)
    run(tools / "zipalign", "-c", "4", output)
    print(f"\nAPK: {output}")


if __name__ == "__main__":
    main()
