/*
 * KelirGram visual background effects.
 * Generalizes the exteraGram SnowflakesEffect into a small multi-mode particle
 * system (snow / summer / autumn / spring) drawn behind the chat content.
 *
 * Based on SnowflakesEffect (Copyright Nikolai Kudashov, 2013-2018, GPL v2+).
 */

package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.Calendar;

public class KelirWeatherEffect {

    public static final int NONE = 0;
    public static final int SNOW = 1;
    public static final int SUMMER = 2;
    public static final int AUTUMN = 3;
    public static final int SPRING = 4;
    public static final int AUTO = 5;

    /** Resolve the configured mode; AUTO maps to a concrete season by month. */
    public static int resolved() {
        int e = ExteraConfig.kelirEffect;
        if (e != AUTO) {
            return e;
        }
        int month = Calendar.getInstance().get(Calendar.MONTH); // 0 = January
        if (month == 11 || month == 0 || month == 1) return SNOW;   // Dec-Feb
        if (month >= 2 && month <= 4) return SPRING;                 // Mar-May
        if (month >= 5 && month <= 7) return SUMMER;                 // Jun-Aug
        return AUTUMN;                                               // Sep-Nov
    }

    public static boolean active() {
        return resolved() != NONE;
    }

    private static final int KIND_FLAKE = 0;
    private static final int KIND_SAND = 1;
    private static final int KIND_RAIN = 2;
    private static final int KIND_DROP = 3;
    private static final int KIND_SWIRL = 4;

    private final int viewType;
    private int effect = -1;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();

    private Bitmap flakeBitmap;
    private long lastTime;

    private static final class P {
        float x, y, vx, vy, size, amp, seed, rot, age, life, alpha;
        int kind;
    }

    private final ArrayList<P> particles = new ArrayList<>();

    public KelirWeatherEffect(int viewType) {
        this.viewType = viewType;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
    }

    private void ensureFlake() {
        if (flakeBitmap != null) {
            return;
        }
        int sz = AndroidUtilities.dp(16f);
        flakeBitmap = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(flakeBitmap);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(AndroidUtilities.dp(1f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xffffffff);
        float cx = sz / 2f, cy = sz / 2f;
        float arm = sz / 2f - AndroidUtilities.dp(1.5f);
        float angle = (float) -Math.PI / 2f;
        for (int a = 0; a < 6; a++) {
            float ex = cx + (float) Math.cos(angle) * arm;
            float ey = cy + (float) Math.sin(angle) * arm;
            c.drawLine(cx, cy, ex, ey, p);
            float bx = cx + (float) Math.cos(angle) * arm * 0.6f;
            float by = cy + (float) Math.sin(angle) * arm * 0.6f;
            for (int s = -1; s <= 1; s += 2) {
                float sa = angle + s * (float) Math.PI / 4f;
                c.drawLine(bx, by, bx + (float) Math.cos(sa) * arm * 0.32f, by + (float) Math.sin(sa) * arm * 0.32f, p);
            }
            angle += (float) Math.PI / 3f;
        }
    }

    private void spawn(int eff, float W, float H, float top) {
        if (W <= 0 || H <= 0) return;
        int cap;
        int perFrame;
        switch (eff) {
            case SNOW: cap = 140; perFrame = 2; break;
            case SUMMER: cap = 150; perFrame = 4; break;
            case AUTUMN: cap = 90; perFrame = 2; break;
            case SPRING: cap = 16; perFrame = 1; break;
            default: return;
        }
        for (int i = 0; i < perFrame; i++) {
            if (particles.size() >= cap) break;
            if (Utilities.random.nextFloat() > 0.6f) continue;
            P p = new P();
            p.seed = Utilities.random.nextFloat() * (float) (Math.PI * 2);
            p.age = 0;
            p.alpha = 0;
            if (eff == SNOW) {
                p.kind = KIND_FLAKE;
                p.x = Utilities.random.nextFloat() * W;
                p.y = top + Utilities.random.nextFloat() * (H - top);
                p.vy = AndroidUtilities.dp(28f + Utilities.random.nextFloat() * 22f);
                p.vx = 0;
                p.amp = AndroidUtilities.dp(14f);
                p.size = AndroidUtilities.dp(9f + Utilities.random.nextFloat() * 9f);
                p.rot = Utilities.random.nextFloat() * (float) Math.PI;
                p.rotv = (Utilities.random.nextFloat() - 0.5f) * 0.6f;
                p.life = 6 + Utilities.random.nextFloat() * 6;
            } else if (eff == SUMMER) {
                boolean swirl = Utilities.random.nextFloat() > 0.82f;
                p.kind = swirl ? KIND_SWIRL : KIND_SAND;
                p.x = Utilities.random.nextFloat() * W;
                p.y = top + Utilities.random.nextFloat() * 40f;
                p.vy = AndroidUtilities.dp(swirl ? 60f : (360f + Utilities.random.nextFloat() * 220f));
                p.vx = AndroidUtilities.dp(swirl ? 90f : (40f + Utilities.random.nextFloat() * 40f));
                p.amp = 0;
                p.size = AndroidUtilities.dp(swirl ? 10f : (2f + Utilities.random.nextFloat() * 2.5f));
                p.rot = 0;
                p.rotv = 0;
                p.life = 4 + Utilities.random.nextFloat() * 3;
            } else if (eff == AUTUMN) {
                p.kind = KIND_RAIN;
                p.x = Utilities.random.nextFloat() * (W + 80f) - 40f;
                p.y = top + Utilities.random.nextFloat() * 30f;
                p.vy = AndroidUtilities.dp(720f + Utilities.random.nextFloat() * 220f);
                p.vx = AndroidUtilities.dp(70f);
                p.amp = 0;
                p.size = AndroidUtilities.dp(9f + Utilities.random.nextFloat() * 7f);
                p.rot = 0;
                p.rotv = 0;
                p.life = 3;
            } else { // SPRING
                p.kind = KIND_DROP;
                p.x = Utilities.random.nextFloat() * W;
                p.y = top + AndroidUtilities.dp(18f);
                p.vy = AndroidUtilities.dp(320f + Utilities.random.nextFloat() * 160f);
                p.vx = 0;
                p.amp = 0;
                p.size = AndroidUtilities.dp(2.5f + Utilities.random.nextFloat() * 1.5f);
                p.rot = 0;
                p.rotv = 0;
                p.life = 4;
            }
            particles.add(p);
        }
    }

    public void onDraw(View parent, Canvas canvas) {
        if (parent == null || canvas == null) {
            return;
        }
        int eff = resolved();
        if (eff == NONE) {
            return;
        }
        if (effect != eff) {
            effect = eff;
            particles.clear();
            lastTime = 0;
        }
        float W = parent.getMeasuredWidth();
        float H = parent.getMeasuredHeight();
        if (W <= 0 || H <= 0) {
            return;
        }
        float top = viewType == 0 ? 0 : AndroidUtilities.statusBarHeight;

        long now = System.currentTimeMillis();
        float dt = lastTime == 0 ? 0.016f : Math.min(33f, now - lastTime) / 1000f;
        lastTime = now;
        float t = now / 1000f;

        drawBackdrop(eff, canvas, W, H, top, t);
        spawn(eff, W, H, top);
        updateAndDraw(eff, canvas, W, H, dt);
        drawForeground(eff, canvas, W, H, top, t);
        parent.invalidate();
    }

    private void drawBackdrop(int eff, Canvas canvas, float W, float H, float top, float t) {
        if (eff == SNOW) {
            drawCloud(canvas, W, top, t, 0x33ffffff, 2);
        } else if (eff == AUTUMN) {
            drawCloud(canvas, W, top, t, 0x4d3a4450, 3);
        } else if (eff == SUMMER) {
            drawCloud(canvas, W, top, t, 0x22ffffff, 1);
        }
    }

    private void drawForeground(int eff, Canvas canvas, float W, float H, float top, float t) {
        if (eff == SPRING) {
            drawIcicles(canvas, W, top);
        } else if (eff == SUMMER) {
            drawParasol(canvas, W, top, t);
        }
    }

    private void drawCloud(Canvas canvas, float W, float top, float t, int color, int count) {
        fill.setColor(color);
        float span = W + AndroidUtilities.dp(160f);
        for (int i = 0; i < count; i++) {
            float speed = AndroidUtilities.dp(8f + i * 3f);
            float x = (((t * speed) + i * span / count) % span) - AndroidUtilities.dp(80f);
            float y = top + AndroidUtilities.dp(34f + i * 26f);
            float s = AndroidUtilities.dp(1f + (i % 2) * 0.4f);
            float r = AndroidUtilities.dp(20f) * s;
            canvas.drawCircle(x, y, r, fill);
            canvas.drawCircle(x + r * 1.1f, y + r * 0.3f, r * 0.8f, fill);
            canvas.drawCircle(x - r * 1.1f, y + r * 0.35f, r * 0.7f, fill);
            rect.set(x - r * 1.6f, y, x + r * 1.6f, y + r * 0.9f);
            canvas.drawRoundRect(rect, r, r, fill);
        }
    }

    private void drawIcicles(Canvas canvas, float W, float top) {
        fill.setColor(0x66bcd3e6);
        stroke.setColor(0x88d6e8f2);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(AndroidUtilities.dp(1f));
        float step = AndroidUtilities.dp(26f);
        int n = (int) (W / step) + 1;
        for (int i = 0; i < n; i++) {
            float x = i * step + step * 0.5f;
            float w = step * 0.34f;
            float len = AndroidUtilities.dp(16f + (i % 3) * 7f);
            canvas.drawLine(x - w, top, x, top + len, stroke);
            canvas.drawLine(x, top + len, x + w, top, stroke);
            canvas.drawLine(x - w, top, x + w, top, stroke);
        }
    }

    private void drawParasol(Canvas canvas, float W, float top, float t) {
        float cx = W * 0.8f;
        float cy = top + AndroidUtilities.dp(64f);
        float r = AndroidUtilities.dp(40f);
        float tilt = (float) Math.toRadians(18 + Math.sin(t * 1.1f) * 5);
        canvas.save();
        canvas.rotate(tilt, cx, cy);
        int[] cols = {0xb0b0556b, 0xb0c9a225, 0xb04a6b5a, 0xb06a5a7a};
        int seg = 6;
        for (int i = 0; i < seg; i++) {
            fill.setColor(cols[i % cols.length]);
            float a0 = 180f + i * (180f / seg);
            rect.set(cx - r, cy - r, cx + r, cy + r);
            canvas.drawArc(rect, a0, 180f / seg + 0.6f, true, fill);
        }
        stroke.setColor(0x99d8d8d8);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(AndroidUtilities.dp(1.5f));
        canvas.drawLine(cx, cy, cx, cy + r * 1.15f, stroke);
        canvas.restore();
    }

    private void updateAndDraw(int eff, Canvas canvas, float W, float H, float dt) {
        ensureFlake();
        for (int i = particles.size() - 1; i >= 0; i--) {
            P p = particles.get(i);
            p.age += dt;
            float sway = p.amp > 0 ? (float) Math.sin(p.age * 1.6f + p.seed) * p.amp : 0;
            p.x += (p.vx + sway) * dt;
            p.y += p.vy * dt;
            p.rot += p.rotv * dt;
            if (p.y > H + 24 || p.x < -40 || p.x > W + 40 || p.age > p.life) {
                particles.remove(i);
                continue;
            }
            float fadeIn = Math.min(1f, p.age / 0.4f);
            float fadeOut = Math.min(1f, (p.life - p.age) / 0.6f);
            p.alpha = Math.max(0, Math.min(fadeIn, fadeOut));
            drawParticle(canvas, p, eff);
        }
    }

    private void drawParticle(Canvas canvas, P p, int eff) {
        switch (p.kind) {
            case KIND_FLAKE: {
                int a = (int) (180 * p.alpha);
                if (a <= 0) break;
                bitmapPaint.setAlpha(a);
                float s = p.size / AndroidUtilities.dp(16f);
                canvas.save();
                canvas.scale(s, s, p.x, p.y);
                canvas.drawBitmap(flakeBitmap, p.x - AndroidUtilities.dp(8f), p.y - AndroidUtilities.dp(8f), bitmapPaint);
                canvas.restore();
                break;
            }
            case KIND_SAND: {
                fill.setColor(argb(0xc2b280, 150, p.alpha));
                canvas.drawCircle(p.x, p.y, p.size, fill);
                break;
            }
            case KIND_SWIRL: {
                stroke.setColor(argb(0xbfc4c9, 70, p.alpha));
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(AndroidUtilities.dp(1.4f));
                rect.set(p.x, p.y, p.x + p.size * 2f, p.y + p.size);
                canvas.drawArc(rect, 20, 260, false, stroke);
                break;
            }
            case KIND_RAIN: {
                stroke.setColor(argb(0x9fb0c0, 130, p.alpha));
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(AndroidUtilities.dp(1.4f));
                canvas.drawLine(p.x, p.y, p.x - p.size * 0.12f, p.y + p.size, stroke);
                break;
            }
            case KIND_DROP: {
                fill.setColor(argb(0x9fb8cc, 165, p.alpha));
                canvas.drawCircle(p.x, p.y, p.size, fill);
                break;
            }
        }
    }

    private static int argb(int rgb, int baseAlpha, float alphaFactor) {
        int a = (int) (baseAlpha * Math.max(0f, Math.min(1f, alphaFactor)));
        return (a << 24) | (rgb & 0x00ffffff);
    }
}
