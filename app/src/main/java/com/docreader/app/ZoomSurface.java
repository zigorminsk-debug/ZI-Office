package com.docreader.app;
import android.content.Context; import android.graphics.Canvas; import android.graphics.Matrix;
import android.util.AttributeSet; import android.view.MotionEvent; import android.view.ScaleGestureDetector;
import android.view.View; import android.view.ViewConfiguration; import android.view.ViewGroup;
public class ZoomSurface extends ViewGroup {
    public interface Listener { void onScale(float scale); }
    private final Matrix matrix = new Matrix(); private final float[] mv = new float[9];
    private final ScaleGestureDetector scaleDet; private final int slop; private Listener listener;
    private float lastX, lastY, downX, downY; private int panId = -1; private boolean scaling, panning;
    public ZoomSurface(Context c) { this(c, null); }
    public ZoomSurface(Context c, AttributeSet a) { this(c, a, 0); }
    public ZoomSurface(Context c, AttributeSet a, int d) {
        super(c, a, d); setClickable(true); setWillNotDraw(false);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        scaleDet = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector s) { scaling = true; panning = false; if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true); return true; }
            @Override public boolean onScale(ScaleGestureDetector s) { float f = s.getScaleFactor(); if (f > 0f && !Float.isNaN(f)) { matrix.postScale(f, f, s.getFocusX(), s.getFocusY()); clamp(); invalidate(); notifyScale(); } return true; }
            @Override public void onScaleEnd(ScaleGestureDetector s) { scaling = false; }
        });
        try { scaleDet.setQuickScaleEnabled(false); } catch (Exception ignored) {}
    }
    public void setListener(Listener l) { listener = l; }
    public float getScale() { matrix.getValues(mv); return mv[Matrix.MSCALE_X]; }
    public void zoomBy(float factor) { matrix.postScale(factor, factor, getWidth() / 2f, getHeight() / 2f); clamp(); invalidate(); notifyScale(); }
    public void setChildHeight(int h) {
        if (getChildCount() == 0) return; View c = getChildAt(0); if (h < getHeight()) h = getHeight();
        if (c.getLayoutParams() == null) return;
        if (c.getLayoutParams().height != h) { c.getLayoutParams().height = h; c.setLayoutParams(c.getLayoutParams()); }
    }
    private void notifyScale() { if (listener != null) listener.onScale(getScale()); }
    private void clamp() {
        View c = getChildCount() > 0 ? getChildAt(0) : null;
        matrix.getValues(mv); float s = mv[Matrix.MSCALE_X];
        if (s < 1f || s > 6f) { float ns = Math.max(1f, Math.min(6f, s)); matrix.postScale(ns / (s == 0 ? 1f : s), ns / (s == 0 ? 1f : s), getWidth() / 2f, getHeight() / 2f); matrix.getValues(mv); s = mv[Matrix.MSCALE_X]; }
        float tx = mv[Matrix.MTRANS_X], ty = mv[Matrix.MTRANS_Y];
        float cw = (c != null ? Math.max(c.getWidth(), getWidth()) : getWidth()) * s;
        float ch = (c != null ? Math.max(c.getHeight(), getHeight()) : getHeight()) * s;
        int vw = getWidth(), vh = getHeight();
        if (cw <= vw) tx = 0f; else tx = Math.min(0f, Math.max(vw - cw, tx));
        if (ch <= vh) ty = 0f; else ty = Math.min(0f, Math.max(vh - ch, ty));
        matrix.setScale(s, s); matrix.postTranslate(tx, ty);
    }
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
        if (getChildCount() > 0) { View c = getChildAt(0); c.layout(0, 0, c.getMeasuredWidth(), c.getMeasuredHeight()); } clamp();
    }
    @Override protected void dispatchDraw(Canvas canvas) { canvas.save(); canvas.concat(matrix); super.dispatchDraw(canvas); canvas.restore(); }
    @Override public boolean onInterceptTouchEvent(MotionEvent e) { return true; }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        scaleDet.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: panId = e.getPointerId(0); lastX = downX = e.getX(); lastY = downY = e.getY(); panning = false; scaling = false; return true;
            case MotionEvent.ACTION_POINTER_DOWN: return true;
            case MotionEvent.ACTION_MOVE:
                if (scaling || e.getPointerCount() >= 2) return true;
                int idx = e.findPointerIndex(panId); if (idx < 0) idx = 0;
                float x = e.getX(idx), y = e.getY(idx);
                if (!panning && Math.hypot(x - downX, y - downY) > slop) panning = true;
                if (panning) { matrix.postTranslate(x - lastX, y - lastY); clamp(); invalidate(); }
                lastX = x; lastY = y; return true;
            case MotionEvent.ACTION_POINTER_UP: {
                int up = e.getPointerId(e.getActionIndex());
                if (up == panId) { int ni = e.getActionIndex() == 0 ? 1 : 0; if (ni < e.getPointerCount()) { panId = e.getPointerId(ni); lastX = e.getX(ni); lastY = e.getY(ni); } }
                return true;
            }
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: panId = -1; panning = false; scaling = false; return true;
        }
        return true;
    }
}
