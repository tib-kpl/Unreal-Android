package com.ast.unreal;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import java.io.File;

/** The gear at the top right of both game screens that opens the Android settings. */
final class AndroidSettingsGear {
    private AndroidSettingsGear() {}

    /**
     * Adds the gear over the game. The settings result comes back through the
     * Activity's onActivityResult with the request code GoldSettingsActivity.OPEN.
     */
    static View install(Activity activity, File root, boolean v200) {
        final float density = activity.getResources().getDisplayMetrics().density;
        View gear = new View(activity) {
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                float cx = getWidth() / 2f, cy = getHeight() / 2f, unit = Math.min(cx, cy);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x66000000);
                canvas.drawCircle(cx, cy, unit * .85f, paint);
                paint.setColor(0xffddd5c5);
                for (int tooth = 0; tooth < 8; tooth++) {
                    canvas.save();
                    canvas.rotate(tooth * 45, cx, cy);
                    canvas.drawRect(cx - unit * .10f, cy - unit * .55f, cx + unit * .10f, cy - unit * .30f, paint);
                    canvas.restore();
                }
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(unit * .13f);
                canvas.drawCircle(cx, cy, unit * .34f, paint);
                canvas.drawCircle(cx, cy, unit * .10f, paint);
            }
        };
        gear.setContentDescription("fr".equals(java.util.Locale.getDefault().getLanguage())
                ? "Réglages Android" : "Android settings");
        // Touch only: a focusable view takes controller input away from SDL.
        gear.setFocusable(false);
        gear.setFocusableInTouchMode(false);
        gear.setOnClickListener(v -> {
            Intent intent = new Intent(activity, GoldSettingsActivity.class);
            intent.putExtra(UnrealDataPaths.EXTRA_UNREAL_ROOT, root.getAbsolutePath());
            intent.putExtra(GoldSettingsActivity.EXTRA_V200, v200);
            intent.putExtra(GoldSettingsActivity.EXTRA_GAME_PID, android.os.Process.myPid());
            activity.startActivityForResult(intent, GoldSettingsActivity.OPEN);
        });
        gear.setAlpha(.45f);
        int size = (int) (40 * density + .5f);
        activity.addContentView(gear, new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.RIGHT));
        raise(gear);
        return gear;
    }

    /** Keeps the gear above the RetroTouch overlay, which raises its own elevation. */
    static void raise(View gear) {
        if (gear == null) return;
        gear.bringToFront();
        if (Build.VERSION.SDK_INT >= 21) gear.setElevation(20000f);
    }
}
