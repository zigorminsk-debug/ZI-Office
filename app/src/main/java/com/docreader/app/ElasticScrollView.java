package com.docreader.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.ScrollView;

/**
 * Список с эластичной прокруткой: за краем содержимое тянется с
 * сопротивлением и возвращается пружиной (как в iOS).
 * Используется для списков файлов, недавних документов и текста «О программе».
 */
public class ElasticScrollView extends ScrollView {
    /** Доля движения пальца, которая уходит в растяжение. */
    private static final float DAMPING = 0.5f;
    /** Доля растяжения, которая видна на экране. */
    private static final float VISIBLE = 0.6f;

    private final float maxOver; private final int slop;
    private float downY, lastY;
    private int overscroll;
    private boolean tracking;
    private ValueAnimator spring;

    public ElasticScrollView(Context c) { this(c, null); }
    public ElasticScrollView(Context c, AttributeSet a) { this(c, a, 0); }
    public ElasticScrollView(Context c, AttributeSet a, int d) {
        super(c, a, d);
        maxOver = 140f * c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setOverScrollMode(OVER_SCROLL_NEVER); // свечение заменяем «резинкой»
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tracking = true; downY = lastY = e.getY(); stopSpring();
                break;
            case MotionEvent.ACTION_MOVE: {
                if (!tracking) break;
                float y = e.getY(); float dy = y - lastY; lastY = y;
                if (Math.abs(y - downY) < slop) break;          // ещё не тянем, а нажимаем
                int dir = dy > 0 ? -1 : 1;                      // тянем вниз — проверяем верх
                if (!canScrollVertically(dir)) {
                    setOffset((int) Math.max(-maxOver, Math.min(maxOver, overscroll + dy * DAMPING)));
                } else if (overscroll != 0) {
                    setOffset(0);
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                tracking = false;
                springBack();
                break;
        }
        return super.onTouchEvent(e);
    }

    @Override public void onDetachedFromWindow() { stopSpring(); super.onDetachedFromWindow(); }

    private void setOffset(int value) {
        overscroll = value;
        if (getChildCount() > 0) getChildAt(0).setTranslationY(value * VISIBLE);
    }

    private void springBack() {
        if (overscroll == 0) return;
        stopSpring();
        spring = ValueAnimator.ofInt(overscroll, 0);
        spring.setDuration(340);
        spring.setInterpolator(new DecelerateInterpolator(1.4f));
        spring.addUpdateListener(a -> setOffset((int) a.getAnimatedValue()));
        spring.start();
    }

    private void stopSpring() {
        if (spring != null) { spring.cancel(); spring = null; }
        if (overscroll != 0) setOffset(0);
    }

    /** Позиция «резинки» — для тестов и отладки. */
    int overscrollOffset() { return overscroll; }
}
