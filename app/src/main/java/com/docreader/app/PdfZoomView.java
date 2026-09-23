package com.docreader.app;
import android.content.Context; import android.graphics.Bitmap; import android.graphics.Canvas; import android.graphics.Matrix;
import android.graphics.Paint; import android.graphics.RectF; import android.util.AttributeSet; import android.view.MotionEvent;
import android.view.ScaleGestureDetector; import android.view.View; import android.view.ViewConfiguration;
import java.util.ArrayList;

/**
 * Плавный вертикальный список страниц PDF с масштабированием и прокруткой.
 *
 * Страницы рисуются по запросу (см. {@link PageSource}) и держатся в памяти
 * только те, что видны рядом с экраном. Раньше все страницы рендерились в
 * память сразу и на документах в несколько десятков страниц приложение
 * падало с OutOfMemoryError.
 */
public class PdfZoomView extends View {
    public interface Listener { void onScale(float scale); }

    /** Поставщик страниц: размеры известны сразу, картинка — по запросу. */
    public interface PageSource {
        int count();
        /** Ширина страницы в пунктах (как в PDF). */
        int width(int index);
        /** Высота страницы в пунктах (как в PDF). */
        int height(int index);
        /** Попросить страницу; ответ — PdfZoomView.putPage(index, bitmap) в UI-потоке. */
        void request(int index);
        /** Страница выгружена — bitmap можно освободить. */
        void discard(int index, Bitmap bitmap);
    }

    /** Условная ширина страницы: по ней считается масштаб «по ширине». */
    private static final int VIRTUAL_WIDTH = 1000;
    /** Сколько страниц держим в памяти одновременно. */
    private static final int MAX_CACHED = 6;
    private static final float GAP_DP = 12f;

    private final ArrayList<Bitmap> pages = new ArrayList<>();     // null — страница ещё не готова
    private final ArrayList<Boolean> pending = new ArrayList<>();  // запрос уже отправлен
    private final ArrayList<Integer> tops = new ArrayList<>();     // смещение страницы
    private final ArrayList<Integer> heights = new ArrayList<>();  // высота страницы
    private final float gap;
    private int contentH;
    private PageSource source;

    private final Matrix matrix = new Matrix(); private final float[] mv = new float[9];
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint placeholder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF(); private final RectF viewRect = new RectF();
    private final Matrix inverse = new Matrix();
    private final ScaleGestureDetector scaleDet; private final int slop;
    private Listener listener; private float lastX, lastY, downX, downY;
    private int panId = -1; private boolean scaling, panning;

    public PdfZoomView(Context c) { this(c, null); }
    public PdfZoomView(Context c, AttributeSet a) { this(c, a, 0); }
    public PdfZoomView(Context c, AttributeSet a, int d) {
        super(c, a, d); setClickable(true); setFocusable(true);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        gap = GAP_DP * c.getResources().getDisplayMetrics().density;
        placeholder.setStyle(Paint.Style.FILL); placeholder.setColor(0xFFFFFFFF);
        label.setColor(0xFF78716C); label.setTextSize(16f * c.getResources().getDisplayMetrics().density);
        label.setTextAlign(Paint.Align.CENTER);
        scaleDet = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector s) { scaling = true; panning = false; if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true); return true; }
            @Override public boolean onScale(ScaleGestureDetector s) { float f = s.getScaleFactor(); if (f > 0f && !Float.isNaN(f)) { matrix.postScale(f, f, s.getFocusX(), s.getFocusY()); clamp(); requestVisible(); invalidate(); notifyScale(); } return true; }
            @Override public void onScaleEnd(ScaleGestureDetector s) { scaling = false; }
        });
        try { scaleDet.setQuickScaleEnabled(false); } catch (Exception ignored) {}
    }

    public void setListener(Listener l) { listener = l; }

    /** Задать документ. Передайте null — все страницы выгружаются из памяти. */
    public void setSource(PageSource s) {
        releaseAll();
        source = s;
        pages.clear(); pending.clear(); tops.clear(); heights.clear(); contentH = 0;
        if (s != null) {
            int n = Math.max(0, s.count());
            for (int i = 0; i < n; i++) {
                pages.add(null); pending.add(false);
                int w = Math.max(1, s.width(i)), h = Math.max(1, s.height(i));
                int vh = Math.max(1, Math.round(VIRTUAL_WIDTH * h / (float) w));
                tops.add(contentH);
                heights.add(vh);
                contentH += vh + (int) gap;
            }
            if (n > 0) contentH -= (int) gap;
        }
        matrix.reset();
        if (getWidth() > 0) fitWidth(); else { invalidate(); }
        requestVisible();
    }

    public int pageCount() { return pages.size(); }

    /** Готовая страница из {@link PageSource} (вызывается в UI-потоке). */
    public void putPage(int index, Bitmap bmp) {
        if (index < 0 || index >= pages.size()) { if (bmp != null) bmp.recycle(); return; }
        pending.set(index, false);
        Bitmap old = pages.get(index);
        if (old == bmp) return;
        pages.set(index, bmp);
        if (old != null) releaseLater(index, old);
        if (bmp == null) return; // не получилось — оставим заглушку
        invalidate();
    }

    public float getScale() { matrix.getValues(mv); return mv[Matrix.MSCALE_X]; }

    public void zoomBy(float factor) {
        matrix.postScale(factor, factor, getWidth() / 2f, getHeight() / 2f);
        clamp(); requestVisible(); invalidate(); notifyScale();
    }

    /** Показать страницу с номером index (0 — первая). */
    public void jumpToPage(int index) {
        if (index < 0 || index >= tops.size()) return;
        matrix.getValues(mv); float s = mv[Matrix.MSCALE_X]; if (s < 0.01f) s = 1f;
        matrix.setScale(s, s); matrix.postTranslate(0, -tops.get(index) * s);
        clamp(); requestVisible(); invalidate();
    }

    private void fitWidth() {
        if (getWidth() <= 0 || tops.isEmpty()) return;
        float s = getWidth() / (float) VIRTUAL_WIDTH;
        matrix.setScale(s, s);
        clamp(); invalidate(); notifyScale();
    }

    private float minScale() { return getWidth() <= 0 ? 1f : getWidth() / (float) VIRTUAL_WIDTH; }

    private void clamp() {
        matrix.getValues(mv); float s = mv[Matrix.MSCALE_X]; float min = minScale(); float max = Math.max(min * 6f, min + 0.1f);
        if (s < min || s > max) { float f = Math.max(min, Math.min(max, s)) / (s == 0 ? 1f : s); matrix.postScale(f, f, getWidth() / 2f, getHeight() / 2f); matrix.getValues(mv); s = mv[Matrix.MSCALE_X]; }
        float tx = mv[Matrix.MTRANS_X], ty = mv[Matrix.MTRANS_Y];
        float cw = VIRTUAL_WIDTH * s, ch = contentH * s; int vw = getWidth(), vh = getHeight();
        if (cw <= vw) tx = (vw - cw) / 2f; else tx = Math.min(0f, Math.max(vw - cw, tx));
        if (ch <= vh) ty = 0f; else ty = Math.min(0f, Math.max(vh - ch, ty));
        matrix.setScale(s, s); matrix.postTranslate(tx, ty);
    }

    /** Просит у источника видимые страницы и выгружает далёкие. */
    private void requestVisible() {
        if (source == null || tops.isEmpty() || getWidth() <= 0) return;
        viewRect.set(0, 0, getWidth(), getHeight());
        if (!matrix.invert(inverse)) return;
        inverse.mapRect(viewRect); // теперь это область в «виртуальных» координатах
        int first = pageAt(viewRect.top), last = pageAt(viewRect.bottom);
        for (int i = Math.max(0, first - 1); i <= Math.min(tops.size() - 1, last + 1); i++) {
            if (pages.get(i) == null && !pending.get(i)) {
                pending.set(i, true);
                try { source.request(i); } catch (Exception ignored) { pending.set(i, false); }
            }
        }
        // Держим в памяти только страницы рядом с экраном.
        int loaded = 0; for (Bitmap b : pages) if (b != null) loaded++;
        for (int i = 0; i < pages.size() && loaded > MAX_CACHED; i++) {
            if (i >= first - 2 && i <= last + 2) continue;
            Bitmap b = pages.get(i);
            if (b != null) { pages.set(i, null); releaseLater(i, b); loaded--; }
        }
    }

    /** Индекс страницы, в которую попадает виртуальная координата y. */
    private int pageAt(float y) {
        int lo = 0, hi = tops.size() - 1, res = 0;
        while (lo <= hi) { int mid = (lo + hi) / 2; if (tops.get(mid) <= y) { res = mid; lo = mid + 1; } else hi = mid - 1; }
        return res;
    }

    private void releaseAll() {
        for (int i = 0; i < pages.size(); i++) {
            Bitmap b = pages.get(i);
            if (b != null) { pages.set(i, null); releaseLater(i, b); }
        }
    }

    /** Освобождает страницу после текущего кадра: bitmap может ещё рисоваться. */
    private void releaseLater(final int index, final Bitmap bmp) {
        if (bmp == null || bmp.isRecycled()) return;
        final Runnable task = () -> { if (!bmp.isRecycled()) { try { source.discard(index, bmp); } catch (Exception ignored) { try { bmp.recycle(); } catch (Exception ignored2) {} } } };
        android.os.Handler h = getHandler();
        if (h != null) h.post(task); else task.run();
    }

    private void notifyScale() { if (listener != null) listener.onScale(getScale()); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (ow == 0 && !tops.isEmpty()) fitWidth();
        else { clamp(); requestVisible(); invalidate(); }
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xFFF4F1EA);
        if (tops.isEmpty()) return;
        canvas.save(); canvas.concat(matrix);
        for (int i = 0; i < tops.size(); i++) {
            int top = tops.get(i), bottom = top + heights.get(i);
            Bitmap b = pages.get(i);
            if (b != null && !b.isRecycled()) {
                dst.set(0, top, VIRTUAL_WIDTH, bottom);
                canvas.drawBitmap(b, null, dst, paint);
            } else {
                canvas.drawRect(0, top, VIRTUAL_WIDTH, bottom, placeholder);
                canvas.drawText("Стр. " + (i + 1) + " — загрузка…", VIRTUAL_WIDTH / 2f, top + Math.min(120f, heights.get(i) / 2f), label);
            }
        }
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
                if (panning) { matrix.postTranslate(x - lastX, y - lastY); clamp(); requestVisible(); invalidate(); }
                lastX = x; lastY = y; return true;
            case MotionEvent.ACTION_POINTER_UP: {
                int up = e.getPointerId(e.getActionIndex());
                if (up == panId) { int ni = e.getActionIndex() == 0 ? 1 : 0; if (ni < e.getPointerCount()) { panId = e.getPointerId(ni); lastX = e.getX(ni); lastY = e.getY(ni); } }
                return true;
            }
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                panId = -1; panning = false; scaling = false; requestVisible(); return true;
        }
        return true;
    }
}
