package jp.cointoss.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** 金属の表裏、厚み、空中回転と着地の影を描くコイン。 */
public final class CoinView extends View {
    private final Paint paint = new Paint(3);
    private ValueAnimator animator;
    private float progress = 1f;
    private boolean heads = true;
    private boolean startHeads = true;

    /** コイン用の描画領域と操作説明を用意する。 */
    public CoinView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("コイン。タップ、または上に弾いてトス");
        setFocusable(true);
        setClickable(true);
    }

    /** 静止したコインを指定した面で表示する。 */
    public void show(boolean heads) {
        this.heads = heads;
        progress = 1f;
        invalidate();
    }

    /** 放物線に沿って上昇・下降し、選ばれた面で着地する。 */
    public void toss(boolean result, Runnable finished) {
        startHeads = heads;
        heads = result;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1450);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> {
            progress = (float) value.getAnimatedValue();
            invalidate();
            if (progress >= 1f) finished.run();
        });
        animator.start();
    }

    /** 画面破棄時にアニメーションと完了通知を停止する。 */
    public void stop() {
        if (animator != null) {
            animator.removeAllUpdateListeners();
            animator.cancel();
            animator = null;
        }
    }

    /** 領域に合わせてコイン、接地影、回転角を描画する。 */
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth() * .67f, getHeight() * .49f);
        float r = size / 2f;
        float cx = getWidth() / 2f;
        float base = getHeight() * .61f;
        float t = progress;
        float flight = t < 1 ? (float) Math.sin(Math.PI * t) : 0;
        float cy = base - flight * getHeight() * .27f;
        float eased = 1f - (1f - t) * (1f - t);
        float angle = (float) (Math.PI * (8 + (startHeads == heads ? 0 : 1)) * eased);
        float rotation = t < 1 ? (float) Math.cos(angle) : 1f;
        boolean face = t < 1 ? (rotation >= 0 ? startHeads : !startHeads) : heads;
        float shadowWidth = r * (1f - .38f * flight);
        paint.setShader(new RadialGradient(cx, base + r + 20, shadowWidth,
                new int[]{0x350D2822, 0x00102B28}, null, Shader.TileMode.CLAMP));
        canvas.save();
        canvas.scale(1f, .19f, cx, base + r + 20);
        canvas.drawCircle(cx, base + r + 20, shadowWidth, paint);
        canvas.restore();
        paint.setShader(null);

        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(t < 1 ? (float) Math.sin(t * Math.PI) * -12 : -8);
        canvas.scale(1, Math.max(.045f, Math.abs(rotation)));
        paint.setColor(0xFF986126);
        canvas.drawCircle(0, 6, r, paint);
        paint.setShader(new LinearGradient(-r, -r, r, r,
                new int[]{0xFFFFEDAC, 0xFFE5B84F, 0xFFBE8330, 0xFFF5D888},
                new float[]{0, .42f, .76f, 1}, Shader.TileMode.CLAMP));
        canvas.drawCircle(0, 0, r, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(r * .018f);
        paint.setColor(0xFFFFF0BF);
        canvas.drawCircle(0, 0, r * .94f, paint);
        paint.setColor(0xFFAD782C);
        canvas.drawCircle(0, 0, r * .83f, paint);
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI / 32;
            paint.setColor(i < 32 ? 0x99FFF1BF : 0x99865820);
            canvas.drawLine((float) Math.cos(a) * r * .87f, (float) Math.sin(a) * r * .87f,
                    (float) Math.cos(a) * r * .91f, (float) Math.sin(a) * r * .91f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        if (face) {
            drawStar(canvas, r);
        } else {
            paint.setColor(0xFFAD782C);
            canvas.drawCircle(0, 0, r * .45f, paint);
            paint.setColor(0xFFEAC363);
            canvas.drawCircle(r * .19f, -r * .13f, r * .37f, paint);
            paint.setColor(0xFFFFEDB1);
            canvas.drawCircle(r * .33f, r * .26f, r * .055f, paint);
        }
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        paint.setTextSize(r * .13f);
        paint.setColor(0xFF98692B);
        canvas.drawText(face ? "HEADS" : "TAILS", 0, r * .68f, paint);
        canvas.restore();
    }

    /** 表の中央に四芒星の刻印を描く。 */
    private void drawStar(Canvas canvas, float r) {
        Path star = new Path();
        star.moveTo(0, -r * .53f);
        star.lineTo(r * .14f, -r * .14f);
        star.lineTo(r * .49f, 0);
        star.lineTo(r * .14f, r * .14f);
        star.lineTo(0, r * .49f);
        star.lineTo(-r * .14f, r * .14f);
        star.lineTo(-r * .49f, 0);
        star.lineTo(-r * .14f, -r * .14f);
        star.close();
        paint.setColor(Color.rgb(255, 239, 180));
        canvas.drawPath(star, paint);
        canvas.save();
        canvas.scale(.83f, .83f);
        paint.setColor(0xFFB77D2B);
        canvas.drawPath(star, paint);
        canvas.restore();
    }
}
