# コイントスのアーキテクチャ

最終更新: 2026-11-10

## 概要

Android 8.0（API 26）以降で動作するJavaのネイティブAndroidアプリ。compileSdk / targetSdkは36。外部実行ライブラリ、バックエンド、ネットワーク処理はない。Activity内でトスと履歴の2画面を切り替える。

## 構成

```text
app/src/main/
  AndroidManifest.xml                 起動Activity、権限ゼロ、バックアップ無効
  java/jp/cointoss/app/
    MainActivity.java                 2画面、保存、操作状態、システムバー余白
    CoinView.java                     Canvas描画とValueAnimator
    History.java                      上限100件の履歴モデルと保存形式
  res/
    drawable/ic_coin.xml              星とコインのアイコン前景
    drawable/ic_coin_mono.xml         テーマアイコン用単色図形
    mipmap-anydpi-v26/ic_launcher.xml アダプティブアイコン
    values/styles.xml                 テーマ、システムバー、スプラッシュ
    values/colors.xml                 ライトモードの配色とシステムバー設定
    values-night/                     ダークモードのテーマ・配色
scripts/
  build.py                            SDK / JDKだけでAPKをビルド
  install.sh                          USB更新インストール・起動
  test.sh                             履歴モデルのテスト
  smoke.py                            実機操作・再起動・画面キャプチャ
tests/HistoryTest.java                 履歴モデル10項目の検証
build/                                生成物・ローカル署名鍵（Git対象外）
README.md                             WSLでのビルド・USB接続・検証手順
```

ルートとappのGradle設定はAndroid Studio向け。標準手順はネットワーク不要の `scripts/build.py`。

## データの流れ

1. 起動時にSharedPreferencesから履歴を復元する。
2. ボタン、コインのタップ、上スワイプのいずれかでトスする。
3. 多重実行を防ぐためトスとタブを一時的に無効化する。
4. `SecureRandom.nextBoolean()` で表／裏を等確率で決定する。
5. プロセス共有の単一ワーカーで最新の保存値を読み、結果を先頭に追加する。101件目以降は末尾を除去し、`commit()` する。
6. 保存成功後に回転を開始し、着地時に結果と保存件数を表示する。保存失敗時はエラーを表示して操作を再開する。
7. 履歴画面は新しい順の日時付き一覧と、保存中の表／裏の件数を表示する。

画面が再生成されても共有ワーカーは保存を継続する。SharedPreferencesの変更リスナーで新しい画面にも結果を反映する。保存キューを共有し、書き込みごとに最新データを読み直すため、再生成前後の処理による履歴の取りこぼしを防ぐ。画面破棄時はコインアニメーションと変更通知を解除する。選択タブはBundleに保存する。アニメーション前に永続化するため、着地前に終了しても保存済み結果は残る。

## 保存仕様

- アプリ専用SharedPreferences: `coin_history`、キー: `entries_v1`。
- 形式: 新しい順に `UNIX時刻ミリ秒:H` または `UNIX時刻ミリ秒:T`、1行1件。
- 追加・復元の両方で最新100件に制限する。
- 不正形式、不正な面、0以下の時刻、数値オーバーフローは行単位で無視する。
- 結果は変更不可のListとして公開する。
- 日時は端末時計。並び順は記録順で、時計の変更によって並べ替えない。
- バックアップは無効。アンインストールまたはアプリのデータ消去で履歴は消える。
- AsyncStorageと同じ用途をAndroid標準APIで実現し、追加依存を不要にしている。

## 表示と操作

クリーム色の背景、深緑の文字・操作ボタン、金色のコイン。表は四芒星、裏は三日月。Canvasでグラデーション、2重の縁、64本の刻み目、厚み、接地影を描く。アイコンも同じモチーフのVectorDrawableで、外部素材への依存はない。

1450msのValueAnimatorで上昇・下降と回転を連動させる。回転面の縮小と反転で表裏を切り替え、事前決定した結果の面で停止する。OSのアニメーション倍率・無効設定に従う。ネイティブButtonとTextView、sp文字サイズ、48dp以上の操作領域、結果のライブリージョン通知、コインの読み上げ説明を使用する。

画面はスクロール可能。低い画面や大きな文字にも対応する。ボトムナビゲーションはスクロール領域の外に配置する。API 30以降は `WindowInsets.Type.systemBars() | displayCutout()` の余白をルートへ適用する。API 26〜29はsystemWindowInsetsと、対応OSのDisplayCutoutを組み合わせる。操作領域をステータスバー、切り欠き、3ボタン／ジェスチャー領域から避ける。

参考: [Android公式 edge-to-edgeガイド](https://developer.android.com/develop/ui/views/layout/edge-to-edge)。

### ダークモード

Android標準の `values-night` リソースでOSの明暗設定に連動する。通常はクリーム色、ダークモードは深緑の背景と明るい文字を使う。背景・文字・カード・集計欄・ボトムナビゲーション・選択タブ・トスボタン・押下色・履歴の面アイコンの色を名前付きカラーリソースに集約する。コイン本体とランチャーアイコンは両モードで金色を維持する。

テーマを通常／夜間で分け、起動背景とスプラッシュ、ステータスバー、下部システムバーにも同じ配色を適用する。`windowLightStatusBar` と `windowLightNavigationBar` をモードに合わせ、API 26〜29でレイアウト用フラグを設定するときも同じ明暗設定を適用する。Force Darkは無効化して意図した配色を保持する。

OSのuiMode変更は標準のActivity再生成で反映する。選択タブはBundleから、結果は保存済み履歴から復元する。トス途中の変更でも保存済み結果は残る。アプリ内の切り替え設定や追加権限・依存ライブラリは不要。

参考: [Android公式ダークテーマガイド](https://developer.android.com/develop/ui/views/theming/darktheme)。

## ビルド・配布

`scripts/build.py` はaapt2 → javac → d8 → zipalign → apksignerの順で処理し、`build/outputs/apk/coin-toss-debug.apk` を生成する。署名検証と整列検証も行う。マニフェストのpackageは一時コピーに補完し、Gradle向けソースのnamespace設定と両立させる。

ローカルデバッグ鍵は `build/debug.keystore`。ビルド間で保持する。生成物と鍵はGit対象外。`build/` 全体を消すと更新用署名鍵も失う。Gradle構成はAndroid Gradle Plugin 8.10.1、JDK 17、Gradle 8.11.1以上の8系を想定。Wrapperは同梱していない。PythonビルドとGradleの成果物・デバッグ署名鍵は別なので、端末の更新は同じビルド方式で行う。

`scripts/install.sh` は `adb install -r` で既存履歴を保持して更新後、アプリを起動する。Windows版adb.exeにはwslpathでAPKパスを変換する。Linux版ADBを使う場合は必要に応じてusbipd-winでUSBをWSLへ接続する。

## 権限・プライバシー

Manifestのuses-permissionはゼロ。INTERNET、外部ストレージ、カメラ、位置情報、通知、振動の権限を使わない。実行時権限要求、広告、解析SDK、外部送信、アカウント連携はない。アプリ専用領域の保存にはストレージ権限は不要。公開Activityはランチャーだけ。

## 検証

`bash scripts/test.sh` はJDKだけで10項目を確認する。初期状態、150件追加時の上限・順序・削除境界・集計、保存復元、不正データ、空データ、復元時の上限、変更不可を検証する。

`python3 scripts/smoke.py` はUSB実機でテスト用トスを1件追加し、連打抑止、結果表示、履歴タブ、強制終了・再起動後の永続化を確認する。アプリ画面を `build/verification/` に保存する。端末の設定変更や既存履歴の削除は行わない。横画面、文字拡大、アニメーション無効、ナビゲーション方式の網羅確認は手動で行う。

2026-11-10にAndroid実機で更新インストールとスモークテストが成功。トス中・着地後・履歴画面のPNGを確認し、ステータスバーと3ボタンナビゲーションに操作部分が重ならないことを確認した。APKの署名・整列検証も成功し、aapt2のpermissions出力で要求権限ゼロを確認した。

同日のダークモード追加後も実機へ更新インストールし、ライト／ダーク両方のトス・履歴画面のPNGを確認した。OS設定変更による再生成後も履歴件数と履歴タブの選択状態を保持することを確認し、検証後は端末の明暗設定を元へ戻した。履歴モデルの10項目も再実行して成功した。

署名鍵のパスワードは環境変数 `COIN_TOSS_KEYSTORE_PASSWORD` から取得し、値をコードやドキュメントに保存しない。
