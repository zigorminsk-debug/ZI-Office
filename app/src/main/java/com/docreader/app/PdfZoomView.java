package com.docreader.app;
import android.content.Context; import android.graphics.Bitmap; import android.graphics.Canvas; import android.graphics.Matrix; import android.graphics.Paint;
import android.util.AttributeSet; import android.view.MotionEvent; import android.view.ScaleGestureDetector; import android.view.View; import android.view.ViewConfiguration;
import java.util.ArrayList; import java.util.List;
public class PdfZoomView extends View {
    public interface Listener { void onScale(float scale); }
    private final ArrayList<Bitmap> pages = new ArrayList<>();
    private final Matrix matrix = new Matrix(); private final float[] mv = new float[9];
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleDet; private final int slop; private final float gap;
    private Listener listener; private float contentW, contentH, lastX, lastY, downX, downY;
    private int panId = -1; private boolean scaling, panning;
    public PdfZoomView(Context c) { this(c, null); }
    public PdfZoomView(Context c, AttributeSet a) { this(c, a, 0); }
    public PdfZoomView(Context c, AttributeSet a, int d) {
        super(c, a, d); setClickable(true); setFocusable(true);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        gap = 12f * c.getResources().getDisplayMetrics().density;
        scaleDet = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector s) { scaling = true; panning = false; if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true); return true; }
            @Override public boolean onScale(ScaleGestureDetector s) { float f = s.getScaleFactor(); if (f > 0f && !Float.isNaN(f)) { matrix.postScale(f, f, s.getFocusX(), s.getFocusY()); clamp(); invalidate(); notifyScale(); } return true; }
            @Override public void onScaleEnd(ScaleGestureDetector s) { scaling = false; }
        });
        try { scaleDet.setQuickScaleEnabled(false); } catch (Exception ignored) {}
    }
    public void setListener(Listener l) { listener = l; }
    public float getScale() { matrix.getValues(mv); return mv[Matrix.MSCALE_X]; }
    public void setPages(List<Bitmap> list) {
        pages.clear(); contentW = 0; contentH = 0;
        if (list != null) for (Bitmap b : list) { if (b == null || b.isRecycled()) continue; pages.add(b); contentW = Math.max(contentW, b.getWidth()); contentH += b.getHeight() + gap; }
        matrix.reset(); post(this::fitWidth); invalidate();
    }
    public void zoomBy(float factor) { matrix.postScale(factor, factor, getWidth() / 2f, getHeight() / 2f); clamp(); invalidate(); notifyScale(); }
    public void jumpToPage(int index) {
        if (index < 0 || index >= pages.size()) return;
        float y = 0; for (int i = 0; i < index; i++) y += pages.get(i).getHeight() + gap;
        matrix.getValues(mv); float s = mv[Matrix.MSCALE_X]; if (s < 0.01f) s = 1f;
        matrix.setScale(s, s); matrix.postTranslate(0, -y * s); clamp(); invalidate();
    }
    private void fitWidth() { if (getWidth() <= 0 || contentW <= 0) return; float s = getWidth() / contentW; matrix.setScale(s, s); clamp(); invalidate(); notifyScale(); }
    private float minScale() { return (contentW <= 1 || getWidth() <= 0) ? 1f : getWidth() / contentW; }
    private void clamp() {
        matrix.getValues(mv); float s = mv[Matrix.MSCALE_X]; float min = minScale(); float max = Math.max(min * 6f, min + 0.1f);
        if (s < min || s > max) { float f = Math.max(min, Math.min(max, s)) / (s == 0 ? 1f : s); matrix.postScale(f, f, getWidth() / 2f, getHeight() / 2f); matrix.getValues(mv); s = mv[Matrix.MSCALE_X]; }
        float tx = mv[Matrix.MTRANS_X], ty = mv[Matrix.MTRANS_Y]; float cw = contentW * s, ch = contentH * s; int vw = getWidth(), vh = getHeight();
        if (cw <= vw) tx = (vw - cw) / 2f; else tx = Math.min(0f, Math.max(vw - cw, tx));
        if (ch <= vh) ty = 0f; else ty = Math.min(0f, Math.max(vh - ch, ty));
        matrix.setScale(s, s); matrix.postTranslate(tx, ty);
    }
    private void notifyScale() { if (listener != null) listener.onScale(getScale()); }
    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); if (ow == 0 && contentW > 0) fitWidth(); else { clamp(); invalidate(); } }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xFFF4F1EA); if (pages.isEmpty()) return;
        canvas.save(); canvas.concat(matrix); float y = 0;
        for (Bitmap b : pages) { if (b != null && !b.isRecycled()) canvas.drawBitmap(b, (contentW - b.getWidth()) / 2f, y, paint); y += (b == null ? 0 : b.getHeight()) + gap; }
        canvas.restore();
    }
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
