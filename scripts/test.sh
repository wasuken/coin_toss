#!/usr/bin/env bash
# 履歴モデルを標準JDKだけでテストする。
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/tests
javac -encoding UTF-8 -d build/tests app/src/main/java/jp/cointoss/app/History.java tests/HistoryTest.java
java -cp build/tests jp.cointoss.app.HistoryTest
