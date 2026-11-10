package jp.cointoss.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 最新100件を新しい順で管理する、Androidに依存しない履歴モデル。 */
public final class History {
    public static final int LIMIT = 100;
    private final ArrayList<Entry> entries = new ArrayList<>();

    /** 結果と端末の記録時刻を保持する。 */
    public static final class Entry {
        public final boolean heads;
        public final long timestamp;

        /** 保存値から履歴1件を作成する。 */
        public Entry(boolean heads, long timestamp) {
            this.heads = heads;
            this.timestamp = timestamp;
        }
    }

    /** 新しい結果を先頭に追加し、上限を超えた古い結果を捨てる。 */
    public void add(boolean heads, long timestamp) {
        entries.add(0, new Entry(heads, timestamp));
        if (entries.size() > LIMIT) entries.remove(entries.size() - 1);
    }

    /** 呼び出し元から変更できない履歴を返す。 */
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /** 表の件数を数える。 */
    public int headsCount() {
        int count = 0;
        for (Entry entry : entries) if (entry.heads) count++;
        return count;
    }

    /** 区切り形式で保存用文字列を生成する。 */
    public String encode() {
        StringBuilder value = new StringBuilder();
        for (Entry entry : entries) {
            value.append(entry.timestamp).append(':').append(entry.heads ? 'H' : 'T').append('\n');
        }
        return value.toString();
    }

    /** 壊れた行を無視し、最大100件を保存順に復元する。 */
    public static History decode(String value) {
        History history = new History();
        for (String line : value.split("\n")) {
            String[] parts = line.split(":");
            if (parts.length != 2 || !(parts[1].equals("H") || parts[1].equals("T"))) continue;
            try {
                long timestamp = Long.parseLong(parts[0]);
                if (timestamp > 0) history.entries.add(new Entry(parts[1].equals("H"), timestamp));
            } catch (NumberFormatException ignored) {
                // 破損した行だけを除外して残りの履歴を利用する。
            }
            if (history.entries.size() == LIMIT) break;
        }
        return history;
    }
}
