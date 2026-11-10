package jp.cointoss.app;

import android.app.Application;
import android.app.UiModeManager;
import android.content.SharedPreferences;
import android.os.Build;

/** 起動時に保存済みの表示モードをアプリ単位で反映する。 */
public final class CoinTossApplication extends Application {
    /** Android 12以降ではスプラッシュも含めた明暗設定をOSへ伝える。 */
    @Override public void onCreate() {
        super.onCreate();
        SharedPreferences appearance = getSharedPreferences("appearance", MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 31 && appearance.contains("night_mode")) {
            getSystemService(UiModeManager.class).setApplicationNightMode(
                    appearance.getInt("night_mode", UiModeManager.MODE_NIGHT_NO));
        }
    }
}
