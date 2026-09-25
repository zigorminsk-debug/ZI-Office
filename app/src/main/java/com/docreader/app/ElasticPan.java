package com.docreader.app;

import android.view.View;
import android.widget.OverScroller;

/**
 * Эластичная прокрутка: содержимое тянется за край с сопротивлением,
 * отпускаешь — пружина возвращает его назад, а внутри границ работает
 * обычная инерция (fling).
 *
 * Класс не знает, чем именно управляет: хозяин (View) сообщает размеры
 * содержимого, текущий масштаб и применяет смещение к своей матрице.
 */
final class ElasticPan {
    interface Host {
        /** Текущее смещение содержимого, px. */
        float translationX();
        float translationY();
        /** Применить смещение, сохранив масштаб. */
        void translation(float tx, float ty);
        /** Размер содержимого без масштаба, px. */
        float contentWidth();
        float contentHeight();
        /** Текущий масштаб содержимого. */
        float scale();
        /** Хозяину нужно перерисоваться (и догрузить то, что стало видно). */
        void onMoved();
    }

    /** Насколько «тяжело» тянется край: 1 — без сопротивления. */
    private static final float RESISTANCE = 0.45f;
    /** Минимальная скорость, при которой запускается инерция, px/с. */
    private static final int MIN_FLING_VELOCITY = 220;

    private final View view; private final Host host; private final OverScroller scroller;
    private final float maxOver; private final float slop;
    private final float[] b = new float[4];   // minTx, maxTx, minTy, maxTy
    private float lastX, lastY, downX, downY, velocityX, velocityY;
    private long lastTime;
    private boolean active, panning;

    ElasticPan(View v, Host h) {
        view = v; host = h;
        scroller = new OverScroller(v.getContext());
        maxOver = 150f * v.getResources().getDisplayMetrics().density;
        slop = android.view.ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
    }

    boolean isPanning() { return panning; }

    /** Прервать анимацию (новое касание, щипок, смена страницы). */
    void abort() {
        scroller.forceFinished(true);
        active = false; panning = false; velocityX = 0; velocityY = 0;
    }

    /** Палец опущен (или сменился указатель) — точка отсчёта. */
    void begin(float x, float y) {
        abort();
        lastX = downX = x; lastY = downY = y;
        lastTime = System.currentTimeMillis();
        active = true;
    }

    /** Движение пальца. true — содержимое сдвинулось. */
    boolean move(float x, float y) {
        if (!active) return false;
        long now = System.currentTimeMillis();
        long dt = Math.max(1L, now - lastTime);
        float dx = x - lastX, dy = y - lastY;
        if (!panning) {
            if (Math.hypot(x - downX, y - downY) <= slop) { lastX = x; lastY = y; lastTime = now; return false; }
            panning = true;
        }
        bounds(b, host.scale());
        float tx = resist(host.translationX() + dx, b[0], b[1]);
        float ty = resist(host.translationY() + dy, b[2], b[3]);
        host.translation(tx, ty);
        velocityX = dx / dt * 1000f;
        velocityY = dy / dt * 1000f;
        lastX = x; lastY = y; lastTime = now;
        host.onMoved();
        return true;
    }

    /** Палец отпущен: за краем — пружина, внутри — инерция. */
    void end() {
        if (!active) return;
        active = false;
        if (!panning) return;
        panning = false;
        float tx = host.translationX(), ty = host.translationY();
        bounds(b, host.scale());
        if (outOfBounds(tx, ty)) {
            scroller.springBack(Math.round(tx), Math.round(ty), Math.round(b[0]), Math.round(b[1]), Math.round(b[2]), Math.round(b[3]));
        } else if (Math.abs(velocityX) > MIN_FLING_VELOCITY || Math.abs(velocityY) > MIN_FLING_VELOCITY) {
            // перелёт за край (overX/overY) тоже разрешён — так появляется «резинка»
            scroller.fling(Math.round(tx), Math.round(ty), Math.round(velocityX), Math.round(velocityY),
                    Math.round(b[0]), Math.round(b[1]), Math.round(b[2]), Math.round(b[3]),
                    Math.round(maxOver), Math.round(maxOver));
        } else {
            return;
        }
        view.postInvalidateOnAnimation();
    }

    /** Вызывать из View.computeScroll(): true — анимация ещё идёт. */
    boolean computeScroll() {
        if (scroller.isFinished()) return false;
        if (scroller.computeScrollOffset()) {
            host.translation(scroller.getCurrX(), scroller.getCurrY());
            host.onMoved();
            return true;
        }
        // инерция закончилась за краем — пружиной возвращаемся назад
        float tx = host.translationX(), ty = host.translationY();
        bounds(b, host.scale());
        if (outOfBounds(tx, ty)) {
            scroller.springBack(Math.round(tx), Math.round(ty), Math.round(b[0]), Math.round(b[1]), Math.round(b[2]), Math.round(b[3]));
            return true;
        }
        return false;
    }

    /** Плавно вернуть содержимое в границы (например, после смены масштаба). */
    void settle() {
        float tx = host.translationX(), ty = host.translationY();
        bounds(b, host.scale());
        if (!outOfBounds(tx, ty)) return;
        float cx = clamp(tx, b[0], b[1]), cy = clamp(ty, b[2], b[3]);
        scroller.startScroll(Math.round(tx), Math.round(ty), Math.round(cx - tx), Math.round(cy - ty), 280);
        view.postInvalidateOnAnimation();
    }

    private boolean outOfBounds(float tx, float ty) {
        return tx < b[0] - 0.5f || tx > b[1] + 0.5f || ty < b[2] - 0.5f || ty > b[3] + 0.5f;
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }

    /** Сопротивление за границами содержимого. */
    private float resist(float v, float min, float max) {
        if (v > max) return max + Math.min(maxOver, (v - max) * RESISTANCE);
        if (v < min) return min - Math.min(maxOver, (min - v) * RESISTANCE);
        return v;
    }

    /** Границы смещения при текущем масштабе. */
    private void bounds(float[] out, float scale) {
        float s = scale <= 0f ? 1f : scale;
        float cw = host.contentWidth() * s, ch = host.contentHeight() * s;
        float vw = view.getWidth(), vh = view.getHeight();
        if (cw <= vw) { out[0] = out[1] = (vw - cw) / 2f; }        // помещается — по центру
        else { out[0] = vw - cw; out[1] = 0f; }
        if (ch <= vh) { out[2] = out[3] = 0f; }                    // сверху
        else { out[2] = vh - ch; out[3] = 0f; }
    }
}
