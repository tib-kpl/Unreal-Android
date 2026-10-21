package com.ast.unreal;

import android.content.Context;
import android.hardware.input.InputManager;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

/** Same Android key/axis normalization as v200; SDL receives no second copy. */
final class GoldControllerInput implements InputManager.InputDeviceListener {
    interface Sink {
        void button(int key, boolean down);
        void axis(int axis, float value);
        void reset();
    }

    private final Sink sink;
    private final InputManager manager;
    private final boolean[] hat = new boolean[4];

    GoldControllerInput(Context context, Sink sink) {
        this.sink = sink;
        manager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        if (manager != null) manager.registerInputDeviceListener(this, null);
    }

    void reset() {
        java.util.Arrays.fill(hat, false);
        sink.reset();
    }

    void destroy() {
        if (manager != null) manager.unregisterInputDeviceListener(this);
    }

    private static boolean controller(InputDevice device, int source) {
        int sources = source | (device == null ? 0 : device.getSources());
        return (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    boolean key(KeyEvent event) {
        if (!controller(event.getDevice(), event.getSource())) return false;
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_UNKNOWN) {
            switch (event.getScanCode()) {
                case 304: key = 96; break;
                case 305: key = 97; break;
                case 308: key = 99; break;
                case 307: key = 100; break;
                case 310: key = 102; break;
                case 311: key = 103; break;
                case 312: key = 104; break;
                case 313: key = 105; break;
                case 289: case 317: key = 106; break;
                case 290: case 318: key = 107; break;
                default: return false;
            }
        }
        if (key != 82 && !(key >= 96 && key <= 110 && key != 98 && key != 101)
                && !(key >= 19 && key <= 22)) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN && event.getAction() != KeyEvent.ACTION_UP) return true;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() != 0) return true;
        // Many Bluetooth pads report both a digital key and an analogue trigger.
        if (key == 104 || key == 105) {
            InputDevice device = event.getDevice();
            if (range(device, key == 104 ? 17 : 18) != null
                    || range(device, key == 104 ? 23 : 22) != null) return true;
        }
        sink.button(key, event.getAction() == KeyEvent.ACTION_DOWN);
        return true;
    }

    private static InputDevice.MotionRange range(InputDevice device, int axis) {
        return device == null ? null : device.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK);
    }

    private static float axis(MotionEvent event, int primary, int fallback) {
        int selected = range(event.getDevice(), primary) != null ? primary : fallback;
        float value = event.getAxisValue(selected);
        return Float.isNaN(value) || Float.isInfinite(value) ? 0f : Math.max(-1f, Math.min(1f, value));
    }

    boolean motion(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) return false;
        if (event.getActionMasked() != MotionEvent.ACTION_MOVE) return false;
        sink.axis(0, axis(event, MotionEvent.AXIS_X, MotionEvent.AXIS_X));
        sink.axis(1, axis(event, MotionEvent.AXIS_Y, MotionEvent.AXIS_Y));
        sink.axis(2, axis(event, MotionEvent.AXIS_Z, MotionEvent.AXIS_RX));
        sink.axis(3, axis(event, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY));
        sink.axis(4, Math.max(0f, Math.max(event.getAxisValue(17), event.getAxisValue(23))));
        sink.axis(5, Math.max(0f, Math.max(event.getAxisValue(18), event.getAxisValue(22))));
        float x = event.getAxisValue(MotionEvent.AXIS_HAT_X), y = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        float[] directions = {-y, y, -x, x};
        int[] keys = {19, 20, 21, 22};
        for (int i = 0; i < 4; i++) {
            boolean down = directions[i] >= (hat[i] ? .25f : .50f);
            if (hat[i] != down) {
                hat[i] = down;
                sink.button(keys[i], down);
            }
        }
        return true;
    }

    @Override public void onInputDeviceAdded(int id) {}
    @Override public void onInputDeviceChanged(int id) {}
    @Override public void onInputDeviceRemoved(int id) { reset(); }
}
