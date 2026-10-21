package com.ast.unreal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

/** A visible editor for the original Unreal menus, which otherwise draw behind the IME. */
final class GameTextInput {
    interface Result { void complete(int request, String text, boolean accepted); }
    private static AlertDialog active;
    private static Activity owner;

    static boolean isOpen(Activity activity) { return owner == activity && active != null; }

    static void show(Activity activity, int request, String initial, int limit, Result result) {
        activity.runOnUiThread(() -> {
            if (activity.isFinishing()) { result.complete(request, "", false); return; }
            if (active != null) active.cancel();
            boolean french = "fr".equals(java.util.Locale.getDefault().getLanguage());
            EditText edit = new EditText(activity);
            edit.setSingleLine(true);
            edit.setTextSize(20);
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            edit.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            edit.setFilters(new InputFilter[] {new InputFilter.LengthFilter(limit)});
            edit.setText(initial);
            edit.setSelection(edit.length());
            int pad = (int) (20 * activity.getResources().getDisplayMetrics().density + .5f);
            FrameLayout content = new FrameLayout(activity);
            content.setPadding(pad, pad / 2, pad, 0);
            content.addView(edit, new FrameLayout.LayoutParams(-1, -2));
            boolean[] completed = {false};
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle(french ? "Saisie de texte" : "Enter text")
                    .setView(content)
                    .setPositiveButton(french ? "Valider" : "OK", (d, which) -> {
                        completed[0] = true;
                        result.complete(request, edit.getText().toString(), true);
                    })
                    .setNegativeButton(french ? "Annuler" : "Cancel", (d, which) -> {})
                    .create();
            active = dialog;
            owner = activity;
            dialog.setOnDismissListener(d -> {
                InputMethodManager ime = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (ime != null) ime.hideSoftInputFromWindow(edit.getWindowToken(), 0);
                if (active == dialog) { active = null; owner = null; }
                if (!completed[0]) result.complete(request, "", false);
            });
            edit.setOnEditorActionListener((v, action, event) -> {
                if (action == EditorInfo.IME_ACTION_DONE || (event != null
                        && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                    return true;
                }
                return false;
            });
            dialog.setOnKeyListener((d, key, event) -> {
                if (key == KeyEvent.KEYCODE_BUTTON_A || key == KeyEvent.KEYCODE_BUTTON_B) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        if (key == KeyEvent.KEYCODE_BUTTON_A) dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                        else dialog.cancel();
                    }
                    return true;
                }
                return false;
            });
            dialog.setOnShowListener(d -> {
                edit.requestFocus();
                edit.postDelayed(() -> {
                    if (active != dialog) return;
                    InputMethodManager ime = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    // Handheld controllers can be reported as hardware keyboards.
                    if (ime != null) ime.showSoftInput(edit, InputMethodManager.SHOW_FORCED);
                }, 150);
            });
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN);
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        });
    }

    static void close(Activity activity) {
        if (owner == activity && active != null) active.cancel();
    }
}
