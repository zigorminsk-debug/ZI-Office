package com.docreader.app;

import android.content.Context; import android.graphics.Canvas; import android.graphics.Matrix;
import android.util.AttributeSet; import android.view.MotionEvent; import android.view.ScaleGestureDetector;
import android.view.View; import android.view.ViewGroup;

/**
 * Обёртка над WebView с масштабом и эластичной прокруткой: документ
 * тянется за край с сопротивлением и возвращается пружиной, внутри
 * границ работает инерция.
 */
public class ZoomSurface extends ViewGroup {
    public interface Listener { void onScale(float scale); }

    private static final float MIN_SCALE = 1f;
    private static final float MAX_SCALE = 6f;

    private final Matrix matrix = new Matrix(); private final float[] mv = new float[9];
    private final ScaleGestureDetector scaleDet;
    private final ElasticPan pan;
    private Listener listener;
    private int panId = -1; private boolean scaling;

    public ZoomSurface(Context c) { this(c, null); }
    public ZoomSurface(Context c, AttributeSet a) { this(c, a, 0); }
    public ZoomSurface(Context c, AttributeSet a, int d) {
        super(c, a, d); setClickable(true); setWillNotDraw(false);
        scaleDet = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector s) { scaling = true; pan.abort(); if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true); return true; }
            @Override public boolean onScale(ScaleGestureDetector s) { float f = s.getScaleFactor(); if (f > 0f && !Float.isNaN(f)) { matrix.postScale(f, f, s.getFocusX(), s.getFocusY()); clampScale(); matrix.getValues(mv); applyTranslation(mv[Matrix.MTRANS_X], mv[Matrix.MTRANS_Y]); onMoved(); notifyScale(); } return true; }
            @Override public void onScaleEnd(ScaleGestureDetector s) { scaling = false; pan.settle(); }
        });
        try { scaleDet.setQuickScaleEnabled(false); } catch (Exception ignored) {}
        pan = new ElasticPan(this, new ElasticPan.Host() {
            @Override public float translationX() { matrix.getValues(mv); return mv[Matrix.MTRANS_X]; }
            @Override public float translationY() { matrix.getValues(mv); return mv[Matrix.MTRANS_Y]; }
            @Override public void translation(float tx, float ty) { applyTranslation(tx, ty); }
            @Override public float contentWidth() { View c = getChildCount() > 0 ? getChildAt(0) : null; return c == null ? getWidth() : Math.max(c.getWidth(), getWidth()); }
            @Override public float contentHeight() { View c = getChildCount() > 0 ? getChildAt(0) : null; return c == null ? getHeight() : Math.max(c.getHeight(), getHeight()); }
            @Override public float scale() { return getScale(); }
            @Override public void onMoved() { ZoomSurface.this.onMoved(); }
        });
    }

    public void setListener(Listener l) { listener = l; }
    public float getScale() { matrix.getValues(mv); return mv[Matrix.MSCALE_X]; }

    public void zoomBy(float factor) {
        pan.abort();
        matrix.postScale(factor, factor, getWidth() / 2f, getHeight() / 2f);
        clampScale();
        matrix.getValues(mv);
        applyTranslation(mv[Matrix.MTRANS_X], mv[Matrix.MTRANS_Y]);
        onMoved(); notifyScale();
    }

    public void setChildHeight(int h) {
        if (getChildCount() == 0) return; View c = getChildAt(0); if (h < getHeight()) h = getHeight();
        if (c.getLayoutParams() == null) return;
        if (c.getLayoutParams().height != h) { c.getLayoutParams().height = h; c.setLayoutParams(c.getLayoutParams()); }
    }

    /** Записать смещение в матрицу, сохранив масштаб. */
    private void applyTranslation(float tx, float ty) {
        matrix.getValues(mv); float sx = mv[Matrix.MSCALE_X], sy = mv[Matrix.MSCALE_Y];
        matrix.setScale(sx, sy); matrix.postTranslate(tx, ty);
    }

    private void onMoved() { invalidate(); }

    private void notifyScale() { if (listener != null) listener.onScale(getScale()); }

    /** Масштаб в допустимых пределах, содержимое не уезжает бесконечно. */
    private void clampScale() {
        matrix.getValues(mv); float s = mv[Matrix.MSCALE_X];
        if (s < MIN_SCALE || s > MAX_SCALE) {
            float ns = Math.max(MIN_SCALE, Math.min(MAX_SCALE, s));
            matrix.postScale(ns / (s == 0 ? 1f : s), ns / (s == 0 ? 1f : s), getWidth() / 2f, getHeight() / 2f);
        }
    }

    /** Жёстко вернуть содержимое в границы (смена размера, программный вызов). */
    private void clampHard(int width, int height) {
        matrix.getValues(mv);
        float s = mv[Matrix.MSCALE_X], tx = mv[Matrix.MTRANS_X], ty = mv[Matrix.MTRANS_Y];
        View c = getChildCount() > 0 ? getChildAt(0) : null;
        float cw = (c == null ? width : Math.max(c.getWidth(), width)) * s;
        float ch = (c == null ? height : Math.max(c.getHeight(), height)) * s;
        if (cw <= width) tx = (width - cw) / 2f; else tx = Math.min(0f, Math.max(width - cw, tx));
        if (ch <= height) ty = 0f; else ty = Math.min(0f, Math.max(height - ch, ty));
        applyTranslation(tx, ty);
    }

    @Override public void computeScroll() { if (pan.computeScroll()) postInvalidateOnAnimation(); }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        if (getChildCount() > 0) {
            View c = getChildAt(0);
            int ch = c.getLayoutParams() != null && c.getLayoutParams().height > 0 ? c.getLayoutParams().height : h;
            c.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.max(h, ch), MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(w, h);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (getChildCount() > 0) { View c = getChildAt(0); c.layout(0, 0, c.getMeasuredWidth(), c.getMeasuredHeight()); }
        clampHard(getWidth(), getHeight());
    }

    @Override protected void dispatchDraw(Canvas canvas) { canvas.save(); canvas.concat(matrix); super.dispatchDraw(canvas); canvas.restore(); }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) { return true; }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        scaleDet.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                panId = e.getPointerId(0); scaling = false; pan.begin(e.getX(), e.getY());
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                pan.abort(); return true;
            case MotionEvent.ACTION_MOVE:
                if (scaling || e.getPointerCount() >= 2) return true;
                int idx = e.findPointerIndex(panId); if (idx < 0) idx = 0;
                pan.move(e.getX(idx), e.getY(idx));
                return true;
            case MotionEvent.ACTION_POINTER_UP: {
                int up = e.getPointerId(e.getActionIndex());
                if (up == panId) {
                    int ni = e.getActionIndex() == 0 ? 1 : 0;
                    if (ni < e.getPointerCount()) { panId = e.getPointerId(ni); pan.begin(e.getX(ni), e.getY(ni)); }
                }
                return true;
            }
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                panId = -1; scaling = false; pan.end(); return true;
        }
        return true;
    }
}
