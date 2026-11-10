package jp.cointoss.app;

/** 履歴の上限、並び順、復元、破損時の扱いを外部ライブラリなしで検証する。 */
public final class HistoryTest {
    /** 制限を超える記録と保存データを使ってモデルの仕様を検証する。 */
    public static void main(String[] args) {
        History history = new History();
        check(history.entries().isEmpty(), "初回は空");
        for (int i = 1; i <= 150; i++) history.add(i % 2 == 0, i);
        check(history.entries().size() == 100, "上限100件");
        check(history.entries().get(0).timestamp == 150, "新しい順");
        check(history.entries().get(99).timestamp == 51, "古い50件を削除");
        check(history.headsCount() == 50, "保存中の件数だけを集計");
        check(History.decode(history.encode()).encode().equals(history.encode()), "再起動時の復元");
        check(History.decode("bad\n-1:H\n100:X\n101:H\n102:T\n999999999999999999999:H\n").entries().size() == 2,
                "壊れた行や不正な時刻を除外");
        check(History.decode("").entries().isEmpty(), "空データ");
        StringBuilder oversized = new StringBuilder();
        for (int i = 200; i > 0; i--) oversized.append(i).append(":H\n");
        History restored = History.decode(oversized.toString());
        check(restored.entries().size() == 100 && restored.entries().get(99).timestamp == 101,
                "復元時にも上限を適用");
        boolean immutable = false;
        try { history.entries().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "外部からの変更を防止");
        System.out.println("HistoryTest: 10項目成功");
    }

    /** 検証失敗を例外として通知する。 */
    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
