#!/usr/bin/env python3
"""USB端末でトス・連打抑止・履歴・再起動を検証し、アプリ画面を保存する。"""
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ADB = os.environ.get("ADB", str(Path(os.environ.get("ANDROID_HOME", Path.home() / "Android")) / "platform-tools/adb"))
OUT = ROOT / "build/verification"


def adb(*args):
    """ADBを実行し、バイナリ出力をそのまま返す。"""
    return subprocess.check_output([ADB, *args])


def hierarchy():
    """アプリのUIツリーを端末から取得する。"""
    adb("shell", "uiautomator", "dump", "/data/local/tmp/coin-toss-ui.xml")
    return ET.fromstring(adb("exec-out", "cat", "/data/local/tmp/coin-toss-ui.xml"))


def find(tree, text):
    """指定したテキストを含むUI要素を探す。"""
    for node in tree.iter("node"):
        if text in node.get("text", ""):
            return node
    raise AssertionError(f"UI要素がありません: {text}")


def center(node):
    """UI要素の中心座標を取得する。"""
    left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
    return (left + right) // 2, (top + bottom) // 2


def screenshot(name):
    """現在表示しているアプリ画面をPNGとして保存する。"""
    (OUT / name).write_bytes(adb("exec-out", "screencap", "-p"))


def count(tree):
    """トス画面の保存件数を読み取る。"""
    label = find(tree, "端末内に保存").get("text")
    return int(re.search(r"履歴 (\d+)", label).group(1))


def main():
    """既存履歴を消さず、1回のテスト用トスを追加して表示と永続化を確認する。"""
    OUT.mkdir(parents=True, exist_ok=True)
    adb("shell", "am", "start", "-n", "jp.cointoss.app/.MainActivity")
    tree = hierarchy()
    # 履歴タブが選択済みの場合はトスへ戻る。
    if not any("端末内に保存" in node.get("text", "") for node in tree.iter("node")):
        x, y = center(find(tree, "トス"))
        adb("shell", "input", "tap", str(x), str(y))
        tree = hierarchy()
    before = count(tree)
    screenshot("01-ready.png")
    x, y = center(find(tree, "コインを弾く"))
    adb("shell", f"input tap {x} {y}; input tap {x} {y}; input tap {x} {y}")
    screenshot("02-toss.png")
    time.sleep(1.6)
    tree = hierarchy()
    expected = min(100, before + 1)
    assert count(tree) == expected, "連打で履歴が重複しています"
    assert any(node.get("text") in ("表", "裏") for node in tree.iter("node")), "結果が表示されていません"
    screenshot("03-result.png")
    x, y = center(find(tree, "履歴"))
    # 「履歴」という文字を含む件数表示ではなく、ボトムタブを選ぶ。
    for node in tree.iter("node"):
        if node.get("class") == "android.widget.Button" and "履歴" in node.get("text", ""):
            x, y = center(node)
    adb("shell", "input", "tap", str(x), str(y))
    tree = hierarchy()
    find(tree, "コインの記録")
    find(tree, "全 " + str(expected) + " 回")
    screenshot("04-history.png")
    adb("shell", "am", "force-stop", "jp.cointoss.app")
    adb("shell", "am", "start", "-n", "jp.cointoss.app/.MainActivity")
    tree = hierarchy()
    assert count(tree) == expected, "再起動で履歴が失われています"
    screenshot("05-restarted.png")
    print(f"実機確認成功: 連打抑止・結果表示・履歴タブ・再起動後の保存（{before} → {expected}件）")
    print(f"画面記録: {OUT}")


if __name__ == "__main__":
    main()
