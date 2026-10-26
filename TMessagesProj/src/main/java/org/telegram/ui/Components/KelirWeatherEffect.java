/*
 * KelirGram visual background effects: snow / summer / autumn / spring.
 * Clouds, particles, icicles and a parasol drawn behind the chat content.
 *
 * Based on SnowflakesEffect (Copyright Nikolai Kudashov, 2013-2018, GPL v2+).
 */

package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
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
        int month = Calendar.getInstance().get(Calendar.MONTH);
        if (month == 11 || month == 0 || month == 1) {
            return SNOW;
        }
        if (month >= 2 && month <= 4) {
            return SPRING;
        }
        if (month >= 5 && month <= 7) {
            return SUMMER;
        }
        return AUTUMN;
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
    private final Path path = new Path();

    private Bitmap flakeBitmap;
    private Bitmap[] cloudBitmaps;
    private long lastTime;

    private final ArrayList<P> particles = new ArrayList<>();
    private final ArrayList<Cloud> clouds = new ArrayList<>();

    private float[] icicleX;
    private float[] icicleLen;
    private float[] icicleHalf;
    private float iciclesForWidth = -1;

    private static final class P {
        float x, y, vx, vy, size, amp, seed, rot, rotv, age, life, alpha;
        int kind;
    }

    private static final class Cloud {
        float x, y, scale, speed, alpha;
        int bitmap;
    }

    public KelirWeatherEffect(int viewType) {
        this.viewType = viewType;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
    }

    private static float dp(float value) {
        return AndroidUtilities.dp(value);
    }

    private void ensureBitmaps() {
        if (flakeBitmap == null) {
            int sz = (int) dp(16f);
            flakeBitmap = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(flakeBitmap);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.1f));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0xffffffff);
            float cx = sz / 2f, cy = sz / 2f;
            float arm = sz / 2f - dp(1.5f);
            float angle = (float) -Math.PI / 2f;
            for (int a = 0; a < 6; a++) {
                c.drawLine(cx, cy, cx + (float) Math.cos(angle) * arm, cy + (float) Math.sin(angle) * arm, p);
                float bx = cx + (float) Math.cos(angle) * arm * 0.58f;
                float by = cy + (float) Math.sin(angle) * arm * 0.58f;
                for (int s = -1; s <= 1; s += 2) {
                    float sa = angle + s * (float) Math.PI / 4.2f;
                    c.drawLine(bx, by, bx + (float) Math.cos(sa) * arm * 0.34f, by + (float) Math.sin(sa) * arm * 0.34f, p);
                }
                angle += (float) Math.PI / 3f;
            }
        }
        if (cloudBitmaps == null) {
            cloudBitmaps = new Bitmap[]{makeCloud(128, 62, 0), makeCloud(104, 54, 1)};
        }
    }

    /**
     * Lobes are merged into one opaque silhouette so a cloud reads as a single body
     * instead of a cluster of overlapping translucent blobs.
     */
    private static Bitmap makeCloud(float wDp, float hDp, int variant) {
        int w = (int) dp(wDp);
        int h = (int) dp(hDp);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xffffffff);
        c.drawRoundRect(new RectF(w * 0.06f, h * 0.52f, w * 0.94f, h * 0.94f), h * 0.22f, h * 0.22f, p);
        float r = h * 0.33f;
        if (variant == 0) {
            c.drawCircle(w * 0.26f, h * 0.52f, r * 0.92f, p);
            c.drawCircle(w * 0.49f, h * 0.36f, r * 1.22f, p);
            c.drawCircle(w * 0.72f, h * 0.5f, r, p);
        } else {
            c.drawCircle(w * 0.3f, h * 0.44f, r * 1.1f, p);
            c.drawCircle(w * 0.58f, h * 0.52f, r * 0.86f, p);
            c.drawCircle(w * 0.78f, h * 0.56f, r * 0.7f, p);
        }
        return bitmap;
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
            clouds.clear();
            iciclesForWidth = -1;
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

        ensureBitmaps();
        drawClouds(eff, canvas, W, H, top, dt);
        if (eff == SPRING) {
            ensureIcicles(W);
            drawIcicles(canvas, top);
        }
        spawn(eff, W, H, top);
        updateAndDraw(canvas, W, H, dt);
        if (eff == SUMMER) {
            drawParasol(canvas, W, H, top, t);
        }
        parent.invalidate();
    }

    private void drawClouds(int eff, Canvas canvas, float W, float H, float top, float dt) {
        int count;
        int rgb;
        float alpha;
        switch (eff) {
            case SNOW:
                count = 5;
                rgb = 0xffffff;
                alpha = 0.6f;
                break;
            case AUTUMN:
                count = 6;
                rgb = 0x46525e;
                alpha = 0.55f;
                break;
            case SUMMER:
                count = 3;
                rgb = 0xf2e6cf;
                alpha = 0.3f;
                break;
            default:
                clouds.clear();
                return;
        }
        if (clouds.size() != count) {
            clouds.clear();
            for (int i = 0; i < count; i++) {
                Cloud cl = new Cloud();
                cl.x = (i + Utilities.random.nextFloat() * 0.6f) * W / count;
                cl.y = top + dp(16f) + Utilities.random.nextFloat() * Math.max(dp(30f), H * 0.22f);
                cl.scale = 0.75f + Utilities.random.nextFloat() * 0.85f;
                cl.speed = dp(5f + Utilities.random.nextFloat() * 9f);
                cl.bitmap = Utilities.random.nextInt(cloudBitmaps.length);
                cl.alpha = alpha * (0.78f + Utilities.random.nextFloat() * 0.3f);
                clouds.add(cl);
            }
        }
        for (int i = 0; i < clouds.size(); i++) {
            Cloud cl = clouds.get(i);
            Bitmap bmp = cloudBitmaps[cl.bitmap];
            cl.x += cl.speed * dt;
            if (cl.x > W + dp(24f)) {
                cl.x = -bmp.getWidth() * cl.scale - dp(24f);
                cl.y = top + dp(16f) + Utilities.random.nextFloat() * Math.max(dp(30f), H * 0.22f);
            }
            int a = (int) (255 * cl.alpha);
            if (a <= 0) {
                continue;
            }
            bitmapPaint.setAlpha(a);
            canvas.save();
            canvas.translate(cl.x, cl.y);
            canvas.scale(cl.scale, cl.scale);
            canvas.drawBitmap(bmp, 0, 0, bitmapPaint);
            canvas.restore();
        }
    }

    private void ensureIcicles(float W) {
        if (iciclesForWidth == W && icicleX != null) {
            return;
        }
        iciclesForWidth = W;
        int n = Math.max(6, (int) (W / dp(34f)));
        icicleX = new float[n];
        icicleLen = new float[n];
        icicleHalf = new float[n];
        for (int i = 0; i < n; i++) {
            icicleX[i] = (i + 0.5f) / n * W + (Utilities.random.nextFloat() - 0.5f) * dp(10f);
            float tall = Utilities.random.nextFloat();
            icicleLen[i] = dp(9f + tall * tall * 32f);
            icicleHalf[i] = dp(3.2f + Utilities.random.nextFloat() * 2.6f);
        }
    }

    private void drawIcicles(Canvas canvas, float top) {
        if (icicleX == null) {
            return;
        }
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1f));
        for (int i = 0; i < icicleX.length; i++) {
            float x = icicleX[i];
            float half = icicleHalf[i];
            float len = icicleLen[i];
            path.reset();
            path.moveTo(x - half, top);
            path.lineTo(x + half, top);
            path.lineTo(x, top + len);
            path.close();
            fill.setColor(0x59d3e2ec);
            canvas.drawPath(path, fill);
            stroke.setColor(0x73eaf4fa);
            canvas.drawLine(x - half * 0.4f, top + dp(1f), x - half * 0.12f, top + len * 0.75f, stroke);
        }
    }

    private void spawn(int eff, float W, float H, float top) {
        int cap;
        int perFrame;
        switch (eff) {
            case SNOW:
                cap = 90;
                perFrame = 2;
                break;
            case SUMMER:
                cap = 110;
                perFrame = 5;
                break;
            case AUTUMN:
                cap = 55;
                perFrame = 3;
                break;
            case SPRING:
                cap = 14;
                perFrame = 1;
                break;
            default:
                return;
        }
        for (int i = 0; i < perFrame; i++) {
            if (particles.size() >= cap) {
                return;
            }
            if (Utilities.random.nextFloat() > 0.55f) {
                continue;
            }
            P p = new P();
            p.seed = Utilities.random.nextFloat() * (float) (Math.PI * 2);
            p.rot = Utilities.random.nextFloat() * (float) Math.PI;
            p.rotv = (Utilities.random.nextFloat() - 0.5f) * 0.6f;
            if (eff == SNOW) {
                p.kind = KIND_FLAKE;
                p.x = Utilities.random.nextFloat() * W;
                p.y = -dp(18f);
                p.vy = dp(38f + Utilities.random.nextFloat() * 54f);
                p.vx = dp(-6f + Utilities.random.nextFloat() * 12f);
                p.amp = dp(6f + Utilities.random.nextFloat() * 12f);
                p.size = dp(7f + Utilities.random.nextFloat() * 10f);
                p.life = 14 + Utilities.random.nextFloat() * 8;
            } else if (eff == SUMMER) {
                boolean swirl = Utilities.random.nextFloat() < 0.22f;
                p.kind = swirl ? KIND_SWIRL : KIND_SAND;
                if (swirl) {
                    p.x = Utilities.random.nextFloat() * W * 0.5f;
                    p.y = Utilities.random.nextFloat() * H;
                    p.vy = dp(-4f + Utilities.random.nextFloat() * 8f);
                    p.vx = dp(150f + Utilities.random.nextFloat() * 130f);
                    p.size = dp(9f + Utilities.random.nextFloat() * 12f);
                    p.life = 2.4f;
                } else {
                    p.x = Utilities.random.nextFloat() * W;
                    p.y = -dp(12f) - Utilities.random.nextFloat() * H * 0.25f;
                    p.vy = dp(280f + Utilities.random.nextFloat() * 200f);
                    p.vx = dp(55f + Utilities.random.nextFloat() * 45f);
                    p.size = dp(1.1f + Utilities.random.nextFloat() * 1.7f);
                    p.life = 8;
                }
            } else if (eff == AUTUMN) {
                p.kind = KIND_RAIN;
                p.x = Utilities.random.nextFloat() * (W + dp(40f));
                p.y = -dp(24f) - Utilities.random.nextFloat() * H * 0.3f;
                p.vy = dp(720f + Utilities.random.nextFloat() * 320f);
                p.vx = dp(70f + Utilities.random.nextFloat() * 40f);
                p.size = dp(13f + Utilities.random.nextFloat() * 11f);
                p.life = 5;
            } else {
                if (icicleX == null || icicleX.length == 0) {
                    return;
                }
                int idx = Utilities.random.nextInt(icicleX.length);
                p.kind = KIND_DROP;
                p.x = icicleX[idx];
                p.y = top + icicleLen[idx];
                p.vy = dp(210f + Utilities.random.nextFloat() * 140f);
                p.vx = dp(2f);
                p.size = dp(1.5f + Utilities.random.nextFloat() * 1.2f);
                p.life = 8;
            }
            particles.add(p);
        }
    }

    private void updateAndDraw(Canvas canvas, float W, float H, float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            P p = particles.get(i);
            p.age += dt;
            if (p.age > p.life) {
                particles.remove(i);
                continue;
            }
            float fadeIn = Math.min(1f, p.age / 0.35f);
            float fadeOut = Math.min(1f, (p.life - p.age) / 0.6f);
            p.alpha = Math.max(0, Math.min(fadeIn, fadeOut));
            float sway = p.amp > 0 ? (float) Math.sin(p.age * 1.35f + p.seed) * p.amp : 0;
            p.x += (p.vx + sway) * dt;
            p.y += p.vy * dt;
            p.rot += p.rotv * dt;
            if (p.y > H + dp(30f) || p.x > W + dp(60f) || p.x < -dp(60f)) {
                particles.remove(i);
                continue;
            }
            drawParticle(canvas, p);
        }
    }

    private void drawParticle(Canvas canvas, P p) {
        switch (p.kind) {
            case KIND_FLAKE: {
                int a = (int) (205 * p.alpha);
                if (a <= 0) {
                    break;
                }
                bitmapPaint.setAlpha(a);
                float s = p.size / dp(16f);
                canvas.save();
                canvas.rotate(p.rot * 57f, p.x, p.y);
                canvas.scale(s, s, p.x, p.y);
                canvas.drawBitmap(flakeBitmap, p.x - dp(8f), p.y - dp(8f), bitmapPaint);
                canvas.restore();
                break;
            }
            case KIND_SAND: {
                fill.setColor(argb(0xbda878, 165, p.alpha));
                canvas.drawCircle(p.x, p.y, p.size, fill);
                break;
            }
            case KIND_SWIRL: {
                stroke.setColor(argb(0xa9a294, 110, p.alpha));
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(dp(1.6f));
                rect.set(p.x, p.y, p.x + p.size * 2.4f, p.y + p.size * 1.2f);
                canvas.drawArc(rect, 15, 300, false, stroke);
                rect.set(p.x + p.size * 0.55f, p.y + p.size * 0.3f, p.x + p.size * 1.6f, p.y + p.size * 0.85f);
                canvas.drawArc(rect, 200, 240, false, stroke);
                break;
            }
            case KIND_RAIN: {
                stroke.setColor(argb(0x9db1c4, 150, p.alpha));
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(dp(1.3f));
                canvas.drawLine(p.x, p.y, p.x - p.vx * 0.016f, p.y + p.size, stroke);
                break;
            }
            case KIND_DROP: {
                fill.setColor(argb(0xc2d6e4, 190, p.alpha));
                canvas.drawCircle(p.x, p.y, p.size, fill);
                stroke.setColor(argb(0xc2d6e4, 110, p.alpha));
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(dp(0.9f));
                canvas.drawLine(p.x, p.y - p.size * 2.6f, p.x, p.y - p.size, stroke);
                break;
            }
        }
    }

    private void drawParasol(Canvas canvas, float W, float H, float top, float t) {
        float cx = W * 0.74f;
        float cy = top + H * 0.28f;
        float r = Math.min(dp(56f), W * 0.22f);
        float sway = (float) Math.sin(t * 0.85f) * 6f;
        canvas.save();
        canvas.rotate(15f + sway, cx, cy);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2.2f));
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setColor(argb(0x6b5a46, 200, 1f));
        canvas.drawLine(cx, cy, cx, cy + r * 1.35f, stroke);

        int[] wedges = {0xB08c4a5b, 0xB0c7b25f, 0xB0546b5e, 0xB06e5a80, 0xB0a85c4a, 0xB04f6e7a};
        float sweep = 180f / wedges.length;
        rect.set(cx - r, cy - r * 0.62f, cx + r, cy + r * 0.62f);
        for (int i = 0; i < wedges.length; i++) {
            fill.setColor(wedges[i]);
            canvas.drawArc(rect, 180f + i * sweep, sweep, true, fill);
        }
        float bump = (2 * r) / wedges.length;
        for (int i = 0; i < wedges.length; i++) {
            fill.setColor(wedges[i]);
            rect.set(cx - r + i * bump, cy - bump * 0.42f, cx - r + (i + 1) * bump, cy + bump * 0.42f);
            canvas.drawArc(rect, 0, 180, true, fill);
        }
        rect.set(cx - r, cy - r * 0.62f, cx + r, cy + r * 0.62f);
        stroke.setColor(argb(0x3a3a3a, 110, 1f));
        stroke.setStrokeWidth(dp(1.1f));
        canvas.drawArc(rect, 180, 180, false, stroke);
        fill.setColor(argb(0x6b5a46, 210, 1f));
        canvas.drawCircle(cx, cy - r * 0.62f, dp(2.4f), fill);
        canvas.restore();
    }

    private static int argb(int rgb, int baseAlpha, float alphaFactor) {
        int a = (int) (baseAlpha * Math.max(0f, Math.min(1f, alphaFactor)));
        return (a << 24) | (rgb & 0x00ffffff);
    }
}
