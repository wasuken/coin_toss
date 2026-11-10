package jp.cointoss.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** トスと履歴の2画面、端末内保存とシステムバーの余白を管理する。 */
public final class MainActivity extends Activity {
    private static final int BG = 0xFFF7F5EF, INK = 0xFF183C34, MUTED = 0xFF6C7971;
    private final SecureRandom random = new SecureRandom();
    private static final ExecutorService STORAGE_WORKER = Executors.newSingleThreadExecutor();
    private History history;
    private SharedPreferences preferences;
    private LinearLayout root, content, navigation;
    private CoinView coin;
    private TextView result, subtitle, count;
    private Button toss;
    private int tab;
    private boolean busy;
    private float touchY;
    // 再生成前の画面で開始した保存も、新しい画面へ反映する。
    private final SharedPreferences.OnSharedPreferenceChangeListener historyListener = (store, key) -> {
        if (!busy && root != null && !isDestroyed()) {
            history = History.decode(store.getString("entries_v1", ""));
            render();
        }
    };

    /** 保存済み履歴を読み込み、安全な表示領域と2画面を構築する。 */
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("coin_history", MODE_PRIVATE);
        history = History.decode(preferences.getString("entries_v1", ""));
        tab = state == null ? 0 : state.getInt("tab", 0);
        root = column();
        root.setBackgroundColor(BG);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                int left = insets.getSystemWindowInsetLeft(), top = insets.getSystemWindowInsetTop();
                int right = insets.getSystemWindowInsetRight(), bottom = insets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                    left = Math.max(left, insets.getDisplayCutout().getSafeInsetLeft());
                    top = Math.max(top, insets.getDisplayCutout().getSafeInsetTop());
                    right = Math.max(right, insets.getDisplayCutout().getSafeInsetRight());
                    bottom = Math.max(bottom, insets.getDisplayCutout().getSafeInsetBottom());
                }
                view.setPadding(left, top, right, bottom);
            }
            return insets;
        });
        content = column();
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        navigation = new LinearLayout(this);
        root.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        root.requestApplyInsets();
        render();
        preferences.registerOnSharedPreferenceChangeListener(historyListener);
    }

    /** 選択中のタブを端末の画面再生成に備えて保持する。 */
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("tab", tab);
        super.onSaveInstanceState(state);
    }

    /** 破棄した画面への通知を解除し、進行中の保存は共有ワーカーで完了させる。 */
    @Override protected void onDestroy() {
        if (coin != null) coin.stop();
        preferences.unregisterOnSharedPreferenceChangeListener(historyListener);
        super.onDestroy();
    }

    /** 現在のタブを組み立て、選択状態をボトムナビゲーションに反映する。 */
    private void render() {
        if (coin != null) coin.stop();
        content.removeAllViews();
        navigation.removeAllViews();
        if (tab == 0) renderToss(); else renderHistory();
        navigation.setPadding(dp(16), dp(8), dp(16), dp(8));
        navigation.setBackgroundColor(0xFFEEEDE5);
        addTab("◉  トス", 0);
        addTab("◷  履歴", 1);
    }

    /** コイン、直近結果、トス操作をスクロール可能な画面に配置する。 */
    private void renderToss() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = column();
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(24), dp(24), dp(24), dp(20));
        scroll.addView(page);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        TextView brand = text("COIN / TOSS", 12, MUTED);
        brand.setLetterSpacing(.22f);
        page.addView(brand);
        TextView title = text("運を、ひと投げ。", 30, INK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addSpace(page, 16);
        page.addView(title);
        TextView hint = text("迷ったときは、コインにまかせて。", 14, MUTED);
        addSpace(page, 8);
        page.addView(hint);
        coin = new CoinView(this);
        boolean previous = history.entries().isEmpty() || history.entries().get(0).heads;
        coin.show(previous);
        int coinHeight = Math.max(210, Math.min(340, (int) (getResources().getConfiguration().screenHeightDp * .38f)));
        page.addView(coin, new LinearLayout.LayoutParams(-1, dp(coinHeight)));
        coin.setOnClickListener(view -> performToss());
        coin.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                touchY = event.getY();
                view.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                view.getParent().requestDisallowInterceptTouchEvent(false);
                float distance = event.getY() - touchY;
                if (distance < -dp(32) || Math.abs(distance) < dp(12)) view.performClick();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                view.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        });
        result = text(history.entries().isEmpty() ? "準備はいい？" : previous ? "表" : "裏", 32, INK);
        result.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        result.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        page.addView(result);
        subtitle = text(history.entries().isEmpty() ? "表か、裏か。確率はいつも半分。" : previous ? "HEADS · 星の面" : "TAILS · 月の面", 13, MUTED);
        addSpace(page, 6);
        page.addView(subtitle);
        addSpace(page, 24);
        toss = new Button(this);
        toss.setText("コインを弾く  ↑");
        toss.setTextSize(17);
        toss.setTextColor(Color.WHITE);
        toss.setAllCaps(false);
        toss.setBackground(ripple(INK, 20));
        toss.setOnClickListener(view -> performToss());
        page.addView(toss, new LinearLayout.LayoutParams(-1, dp(60)));
        addSpace(page, 12);
        page.addView(text("コインをタップ・上にスワイプでもトス", 12, MUTED));
        addSpace(page, 24);
        count = text("端末内に保存 · 履歴 " + history.entries().size() + " / 100件", 12, MUTED);
        page.addView(count);
    }

    /** 最新100件の集計と日時付き結果を、新しい順に表示する。 */
    private void renderHistory() {
        LinearLayout header = column();
        header.setPadding(dp(24), dp(24), dp(24), dp(20));
        header.addView(text("YOUR FLIPS", 12, MUTED));
        TextView title = text("コインの記録", 30, INK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addSpace(header, 12);
        header.addView(title);
        addSpace(header, 8);
        header.addView(text("最新100件を保存。古い記録から自動で削除。", 12, MUTED));
        addSpace(header, 24);
        int total = history.entries().size(), heads = history.headsCount();
        TextView summary = text("全 " + total + " 回     ／     表 " + heads + "     裏 " + (total - heads), 16, INK);
        summary.setPadding(dp(16), dp(20), dp(16), dp(20));
        summary.setBackground(shape(0xFFE9EADF, 16));
        header.addView(summary, new LinearLayout.LayoutParams(-1, -2));
        content.addView(header);
        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = column();
        rows.setPadding(dp(24), 0, dp(24), dp(24));
        if (total == 0) {
            addSpace(rows, 48);
            rows.addView(text("まだ記録はありません", 20, INK));
            addSpace(rows, 12);
            rows.addView(text("「トス」で最初のコインを弾いてみよう。", 14, MUTED));
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd  HH:mm:ss", Locale.JAPAN);
        for (int i = 0; i < total; i++) {
            History.Entry entry = history.entries().get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            row.setBackground(shape(Color.WHITE, 16));
            TextView face = text(entry.heads ? "✦" : "☾", 26, 0xFFB3812E);
            face.setGravity(Gravity.CENTER);
            face.setBackground(shape(0xFFFBF0D3, 28));
            row.addView(face, new LinearLayout.LayoutParams(dp(48), dp(48)));
            LinearLayout details = column();
            details.setPadding(dp(16), 0, 0, 0);
            details.addView(text(entry.heads ? "表  ·  HEADS" : "裏  ·  TAILS", 16, INK));
            details.addView(text(format.format(new Date(entry.timestamp)), 12, MUTED));
            row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
            if (i == 0) row.addView(text("最新", 11, MUTED));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(10);
            rows.addView(row, params);
        }
        scroll.addView(rows);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    /** 公平な乱数で結果を決め、保存成功後に1回だけアニメーションする。 */
    private void performToss() {
        if (busy) return;
        busy = true;
        toss.setEnabled(false);
        coin.setEnabled(false);
        setNavigationEnabled(false);
        result.setText("トス中…");
        subtitle.setText("コインの行方を見守って。 ");
        boolean heads = random.nextBoolean();
        long timestamp = System.currentTimeMillis();
        STORAGE_WORKER.execute(() -> {
            History next = History.decode(preferences.getString("entries_v1", ""));
            next.add(heads, timestamp);
            boolean saved = preferences.edit().putString("entries_v1", next.encode()).commit();
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (!saved) {
                    result.setText("保存できませんでした");
                    subtitle.setText("空き容量を確認して、もう一度お試しください。");
                    finishToss();
                    Toast.makeText(this, "結果は記録されていません", Toast.LENGTH_LONG).show();
                    return;
                }
                history = next;
                coin.toss(heads, () -> {
                    result.setText(heads ? "表" : "裏");
                    subtitle.setText(heads ? "HEADS · 星の面" : "TAILS · 月の面");
                    coin.setContentDescription((heads ? "表。星の面。" : "裏。月の面。") + "もう一度タップしてトス");
                    count.setText("端末内に保存 · 履歴 " + history.entries().size() + " / 100件");
                    finishToss();
                });
            });
        });
    }

    /** 完了・エラー後に操作を再開し、連打による重複を防ぐ。 */
    private void finishToss() {
        busy = false;
        toss.setEnabled(true);
        coin.setEnabled(true);
        setNavigationEnabled(true);
    }

    /** トス中の画面切り替えを抑止する。 */
    private void setNavigationEnabled(boolean enabled) {
        for (int i = 0; i < navigation.getChildCount(); i++) navigation.getChildAt(i).setEnabled(enabled);
    }

    /** 選択状態を読み上げ可能なボトムタブを作成する。 */
    private void addTab(String label, int index) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTextColor(index == tab ? INK : MUTED);
        button.setSelected(index == tab);
        button.setContentDescription(label + (index == tab ? "、選択中" : ""));
        button.setBackground(ripple(index == tab ? 0xFFDCE4D8 : 0xFFEEEDE5, 16));
        button.setOnClickListener(view -> {
            if (!busy && tab != index) {
                tab = index;
                render();
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(54), 1);
        params.setMargins(dp(4), 0, dp(4), 0);
        navigation.addView(button, params);
    }

    /** 縦方向のレイアウトを生成する。 */
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    /** 端末の文字サイズ設定に追従する日本語テキストを生成する。 */
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setFontFeatureSettings("palt");
        return view;
    }

    /** 指定色と角丸の背景を作成する。 */
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    /** 押下時のフィードバック付き背景を作成する。 */
    private RippleDrawable ripple(int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x2275947D), shape(color, radius), shape(Color.WHITE, radius));
    }

    /** レイアウトに指定した高さの余白を挿入する。 */
    private void addSpace(LinearLayout layout, int height) {
        layout.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    /** dpを端末密度に合わせたピクセルへ変換する。 */
    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
