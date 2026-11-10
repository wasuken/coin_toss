# コイントス

表は星、裏は月。コインをタップ・上にスワイプ・ボタンで弾くAndroidアプリです。

- 金属のグラデーション、刻み目、厚み、影付きのコインが上昇・回転・着地。
- 「トス」「履歴」のボトムナビゲーション。
- 日時と結果を最新100件だけ端末内に保存。101件目から古い順に自動削除。
- 通信、広告、解析SDK、権限要求なし。Manifestの要求権限はゼロ。
- ステータスバー、切り欠き、ジェスチャー／3ボタンナビゲーションの余白に対応。
- Android 8.0（API 26）以降。Javaによるネイティブ実装で外部実行ライブラリ不要。

## WSLでビルド

JDK 17以降、Python 3、Android SDK Platform 36、Build Tools 36.0.0、Platform Toolsが必要です。

```bash
export ANDROID_HOME="$HOME/Android"
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
python3 scripts/build.py
```

APK: `build/outputs/apk/coin-toss-debug.apk`。SDK導入済みならネットワーク、Gradle、Android Studioは不要です。リソースコンパイル、Java→DEX、zipalign、署名、署名検証を自動で行います。

初回に `build/debug.keystore` を作成し、以降も同じ鍵を使います。**履歴を残して更新するには、この鍵を保持してください。** `build/` 全体を削除すると鍵も消えるので、通常は `build/intermediates/` だけ削除します。署名鍵が変わると更新インストールできません。ストア配布用ではなく、ローカルインストール用APKです。

SDK未導入の別環境では、Android Command-line Toolsで以下を実行します。

```bash
sdkmanager 'platforms;android-36' 'build-tools;36.0.0' 'platform-tools'
sdkmanager --licenses
```

Android Studio向けGradle構成も用意しています。Gradle 8.11.1以上の8系とJDK 17で `gradle :app:assembleDebug` を実行できます。Wrapperは同梱していません。初回にプラグイン等のダウンロードが必要です。Gradleの成果物・署名鍵はPythonビルドとは別です。

## USBインストール

端末の開発者向けオプションで「USBデバッグ」を有効にし、USB接続後に端末上でPCを認証してください。既存履歴を保持してインストールし、起動します。

```bash
bash scripts/install.sh
```

Windows版adb.exeを使用する場合、スクリプトが `wslpath` でAPKパスを変換するので、Windows側で端末を認識していればUSBのWSLへの転送は不要です。

別のADBを使う場合:

```bash
ADB=/path/to/adb bash scripts/install.sh
```

Linux版ADBから直接USBを使う場合は、Windowsにusbipd-winを導入し、PowerShellで以下を実行します。BUSIDは `usbipd list` の端末番号です。

```powershell
usbipd list
# 初回bindは管理者PowerShellで実行
usbipd bind --busid BUSID
usbipd attach --wsl --busid BUSID
```

WSLのLinux版ADBで `adb devices -l` を確認します。`unauthorized` は端末の認証待ちです。複数台の場合は環境変数 `ANDROID_SERIAL` で対象を指定します。

公式資料: [Androidコマンドラインビルド](https://developer.android.com/build/building-cmdline)、[WSLのUSB接続](https://learn.microsoft.com/en-us/windows/wsl/connect-usb)。

## 検証

```bash
# 履歴モデル10項目
bash scripts/test.sh

# 要求権限（package行だけなら要求権限なし）
"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump permissions build/outputs/apk/coin-toss-debug.apk

# USB実機でトス・連打抑止・履歴・再起動を確認
python3 scripts/smoke.py
```

実機テストはテスト用トスを1件追加し、既存履歴は消しません。画面PNGは `build/verification/` に保存します。端末をロック解除し、操作せずに実行してください。

上スワイプ、横画面、大きな文字サイズ、アニメーション無効、切り欠き、3ボタンとジェスチャーナビゲーションの網羅確認は手動で行ってください。スクリプトは端末設定を変更しません。

## データ

AsyncStorageと同じ用途として、Android標準のアプリ専用SharedPreferencesを使用します。保存するのは表／裏と端末の記録時刻だけです。追加・復元時に最大100件を保証します。自動バックアップは無効。アンインストールまたはデータ消去で履歴が消えます。構成は [ARCHITECTURE.md](ARCHITECTURE.md) を参照してください。

ビルド前に環境変数 `COIN_TOSS_KEYSTORE_PASSWORD` に署名鍵のパスワードを設定してください。
