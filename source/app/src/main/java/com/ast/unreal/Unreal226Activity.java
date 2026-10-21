package com.ast.unreal;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.libsdl.app.SDLActivity;

import java.io.File;

/**
 * Runs Unreal Gold 226 data with the second native engine (libUnreal226.so,
 * the Android-ported UE1 v400 engine). The v200 data keeps UnrealSDLActivity.
 */
public class Unreal226Activity extends SDLActivity {
    static final String INI_NAME = "Android226.ini";
    static final String USER_INI_NAME = "Android226User.ini";
    static final String EXTRA_MAP = "com.ast.unreal.EXTRA_MAP";

    private File root;
    private View settingsGear;
    private GoldTouchBridge touchBridge;

    private static native void nativeAndroidButtonV47(int keyCode, boolean down);
    private static native void nativeAndroidAxisV47(int axis, float value);
    private static native void nativeAndroidTouchLookV101(float x, float y);
    private static native int nativeAndroidTouchUiStateRT();
    private static native int nativeAndroidInputResetSerialRT();

    @Override
    protected String[] getLibraries() {
        // libxmp.so is a dependency of libUnreal226.so; API 16-22 do not load it implicitly.
        return new String[] { "SDL2", "xmp", "Unreal226" };
    }

    @Override
    protected String[] getArguments() {
        // The first engine token is the start URL: the Unreal Gold intro map.
        return new String[] {
                "--ue1-root", dataRoot().getAbsolutePath(),
                startMap(),
                "LOG=Unreal226.log",
                "INI=" + INI_NAME,
                "USERINI=" + USER_INI_NAME
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);
        if (Build.VERSION.SDK_INT >= 21) UnrealSDLActivity.Api21Window.makeBarsTransparent(getWindow());
        if (Build.VERSION.SDK_INT >= 28) UnrealSDLActivity.Api28Window.enableShortEdgeCutout(getWindow());
        if (Build.VERSION.SDK_INT >= 30) UnrealSDLActivity.Api30Window.disableDecorFitting(getWindow());
        hideSystemUi();
        Unreal226Config.ensureAndroidIni(new File(dataRoot(), "System"), UnrealGold.language(this), screenHeight());
        // Read by the engine for the version label of the menu.
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                android.system.Os.setenv("UT99_ANDROID_VERSION_NAME", BuildConfig.VERSION_NAME, true);
            } catch (Exception ignored) {
            }
        }
        super.onCreate(savedInstanceState);
        installAndroidSettingsButton();
        installTouchControls();
        scheduleImmersiveRefresh();
    }

    @Override
    protected void onPause() {
        if (touchBridge != null) touchBridge.pause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (touchBridge != null) touchBridge.start();
        scheduleImmersiveRefresh();
    }

    private void installTouchControls() {
        try {
            touchBridge = new GoldTouchBridge(this);
            touchBridge.attach();
        } catch (Throwable t) {
            android.util.Log.e("GoldRetroTouch", "RetroTouch install failed", t);
            touchBridge = null;
        }
    }

    // RetroTouch host: the 226 engine is UT99's, so the touch controls feed
    // the same native controller path as UT99-Android.
    void touchButton(int keyCode, boolean down) { nativeAndroidButtonV47(keyCode, down); }

    void touchMove(float x, float y) {
        nativeAndroidAxisV47(MotionEvent.AXIS_X, x);
        nativeAndroidAxisV47(MotionEvent.AXIS_Y, y);
    }

    void touchLook(float x, float y) { nativeAndroidTouchLookV101(x, y); }

    /** 0 = intro/loading (touch passes through), 1 = UWindow menu, 2 = gameplay. */
    int touchUiState() {
        try { return nativeAndroidTouchUiStateRT(); } catch (Throwable t) { return 0; }
    }

    int touchInputResetSerial() {
        try { return nativeAndroidInputResetSerialRT(); } catch (Throwable t) { return 0; }
    }

    boolean touchHasTouchscreen() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
    }

    /** An external keyboard or mouse replaces the touch controls. */
    boolean touchHasDesktopInput() {
        if (Build.VERSION.SDK_INT < 29) return false;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null || device.isVirtual() || !device.isExternal()) continue;
            // Handheld firmware keeps software cursors registered as external
            // devices; controllers may also report keyboard keys.
            String name = device.getName() == null ? "" : device.getName().toLowerCase(java.util.Locale.US);
            if (name.contains("virtual mouse") || name.contains("virtual keyboard") || name.contains("virtual pointer")) continue;
            int sources = device.getSources();
            if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) continue;
            boolean keyboard = device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC;
            boolean mouse = (device.getSources() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE;
            if (keyboard || mouse) return true;
        }
        return false;
    }

    boolean touchPreferenceEnabled() { return !UnrealGold.touch(this).equals("off"); }

    boolean touchForced() { return UnrealGold.touch(this).equals("on"); }

    float touchOpacity() { return UnrealGold.touchOpacity(this) / 100f; }

    void raiseAndroidSettingsButton() {
        if (settingsGear != null) settingsGear.bringToFront();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (touchBridge != null) touchBridge.beforeHostTouch(event);
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (touchBridge != null) {
            int source = event.getSource();
            if ((source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                touchBridge.onHardwareControllerInput();
            } else if (event.getDevice() != null && event.getDevice().getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC
                    && !event.getDevice().isVirtual()) {
                touchBridge.onHardwareDesktopInput();
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (touchBridge != null && (event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            touchBridge.onHardwareControllerInput();
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) scheduleImmersiveRefresh();
    }

    @Override
    protected void onDestroy() {
        boolean cleanProcessExit = isFinishing() && !isChangingConfigurations();
        super.onDestroy();
        // UE1 cannot run SDL_main twice in one process: end it so the next launch starts fresh.
        if (cleanProcessExit) android.os.Process.killProcess(android.os.Process.myPid());
    }

    private void scheduleImmersiveRefresh() {
        hideSystemUi();
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(this::hideSystemUi, 50);
        handler.postDelayed(this::hideSystemUi, 250);
        handler.postDelayed(this::hideSystemUi, 750);
    }

    private void hideSystemUi() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        if (Build.VERSION.SDK_INT >= 30) UnrealSDLActivity.Api30Window.hideSystemBars(decor);
    }

    private void installAndroidSettingsButton() {
        final float density = getResources().getDisplayMetrics().density;
        View gear = new View(this) {
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
        gear.setContentDescription("Android settings");
        // Touch only: a focusable view takes controller input away from SDL.
        gear.setFocusable(false);
        gear.setFocusableInTouchMode(false);
        gear.setOnClickListener(v -> {
            Intent intent = new Intent(this, GoldSettingsActivity.class);
            intent.putExtra(UnrealDataPaths.EXTRA_UNREAL_ROOT, dataRoot().getAbsolutePath());
            startActivityForResult(intent, GoldSettingsActivity.OPEN);
        });
        gear.setAlpha(.45f);
        int size = (int) (40 * density + .5f);
        addContentView(gear, new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.RIGHT));
        settingsGear = gear;
        gear.bringToFront();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == GoldSettingsActivity.OPEN && result == GoldSettingsActivity.RESET_TOUCH && touchBridge != null) {
            touchBridge.resetLayout();
        }
        if (request == GoldSettingsActivity.OPEN && result == GoldSettingsActivity.EDIT_TOUCH && touchBridge != null) {
            // Wait for the bridge to restore its mode after the Activity resumes.
            settingsGear.postDelayed(() -> {
                if (!touchBridge.startLayoutEditing()) {
                    boolean french = "fr".equalsIgnoreCase(java.util.Locale.getDefault().getLanguage());
                    android.widget.Toast.makeText(this, french ? "La manette tactile est masquée pour le moment"
                            : "The touch controller is hidden right now", android.widget.Toast.LENGTH_LONG).show();
                }
            }, 600);
        }
        if (request != GoldSettingsActivity.OPEN
                || (result != GoldSettingsActivity.RESTART && result != GoldSettingsActivity.DATA)) return;
        // The native engine cannot restart inside its process: start the
        // launcher in the main process and end this one.
        Intent launcher = new Intent(this, MainActivity.class);
        launcher.putExtra(MainActivity.EXTRA_MANAGE_DATA, result == GoldSettingsActivity.DATA);
        launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(launcher);
        finish();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    private int screenHeight() {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        if (Build.VERSION.SDK_INT >= 17) getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        else getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return Math.min(metrics.widthPixels, metrics.heightPixels);
    }

    /** Unreal.unr (the Gold intro) unless the intent names another map URL. */
    private String startMap() {
        String map = getIntent() != null ? getIntent().getStringExtra(EXTRA_MAP) : null;
        return map != null && !map.trim().isEmpty() ? map.trim() : "Unreal.unr";
    }

    private File dataRoot() {
        if (root == null) {
            String path = getIntent() != null ? getIntent().getStringExtra(UnrealDataPaths.EXTRA_UNREAL_ROOT) : null;
            root = new File(path != null ? path : "");
        }
        return root;
    }
}
