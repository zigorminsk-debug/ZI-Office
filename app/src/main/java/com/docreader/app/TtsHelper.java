package com.docreader.app;
import android.content.Context; import android.os.Bundle; import android.speech.tts.TextToSpeech; import android.speech.tts.UtteranceProgressListener; import android.widget.Toast;
import java.util.ArrayList; import java.util.List; import java.util.Locale;
final class TtsHelper {
    interface Listener { void onState(boolean speaking, int index, int total); }
    private final Context ctx; private TextToSpeech tts; private final List<String> chunks = new ArrayList<>();
    private int index; private boolean ready, playing; private float rate = 1f; private Listener listener;
    TtsHelper(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        tts = new TextToSpeech(this.ctx, status -> {
            ready = status == TextToSpeech.SUCCESS; if (!ready) return;
            int r = tts.setLanguage(new Locale("ru", "RU"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.getDefault());
            tts.setSpeechRate(rate);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { notifyState(); }
                @Override public void onDone(String id) { if (!playing) return; index++; speakCurrent(); }
                @Override public void onError(String id) { playing = false; notifyState(); }
            });
        });
    }
    void setListener(Listener l) { listener = l; }
    void play(String text) {
        if (text == null) text = ""; text = text.replace('\u00a0', ' ').trim();
        if (text.isEmpty()) { Toast.makeText(ctx, "Нет текста для чтения", Toast.LENGTH_SHORT).show(); return; }
        if (!ready || tts == null) { Toast.makeText(ctx, "Озвучка ещё запускается. Повторите через секунду.", Toast.LENGTH_SHORT).show(); return; }
        chunks.clear();
        StringBuilder buf = new StringBuilder();
        for (String p : text.split("\\n+")) {
            String s = p.trim(); if (s.isEmpty()) continue;
            if (buf.length() + s.length() > 380) { if (buf.length() > 0) { chunks.add(buf.toString().trim()); buf.setLength(0); } }
            if (buf.length() > 0) buf.append(' '); buf.append(s);
            if (buf.length() > 300) { chunks.add(buf.toString().trim()); buf.setLength(0); }
        }
        if (buf.length() > 0) chunks.add(buf.toString().trim());
        if (chunks.isEmpty()) chunks.add(text);
        index = 0; playing = true; tts.setSpeechRate(rate); speakCurrent();
    }
    void pause() { playing = false; if (tts != null) tts.stop(); notifyState(); }
    void resume() { if (chunks.isEmpty()) return; playing = true; speakCurrent(); }
    void stop() { playing = false; index = 0; if (tts != null) tts.stop(); notifyState(); }
    boolean isPlaying() { return playing; }
    float slower() { rate = Math.max(0.6f, rate - 0.15f); if (tts != null) tts.setSpeechRate(rate); return rate; }
    float faster() { rate = Math.min(1.8f, rate + 0.15f); if (tts != null) tts.setSpeechRate(rate); return rate; }
    void shutdown() { playing = false; if (tts != null) { tts.stop(); tts.shutdown(); tts = null; } }
    private void speakCurrent() {
        if (!playing || tts == null || index >= chunks.size()) { playing = false; notifyState(); return; }
        tts.speak(chunks.get(index), TextToSpeech.QUEUE_FLUSH, new Bundle(), "u" + index); notifyState();
    }
    private void notifyState() { if (listener != null) listener.onState(playing, index, chunks.size()); }
}
