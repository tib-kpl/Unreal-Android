package com.ast.unreal;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * Android settings opened from the gear of both game screens (Unreal Gold and
 * v200). It runs in the :unreal226 process, so for v200, whose engine shares
 * the launcher's process, it ends the game itself before reopening the launcher.
 */
public final class GoldSettingsActivity extends Activity {
    static final int OPEN = 3520, RESTART = 3521, DATA = 3522, EDIT_TOUCH = 3523, RESET_TOUCH = 3524;
    static final String EXTRA_V200 = "com.ast.unreal.EXTRA_V200";
    static final String EXTRA_GAME_PID = "com.ast.unreal.EXTRA_GAME_PID";
    private static final String[] NAMES = {"English", "Français", "Deutsch", "Español", "Italiano"};
    private static final String[] CODES = {"int", "frt", "det", "est", "itt"};
    private static final String[] V200_CODES = {UnrealDataPaths.LANGUAGE_AUTO, UnrealDataPaths.LANGUAGE_FRENCH, UnrealDataPaths.LANGUAGE_ENGLISH};
    private boolean v200;
    private String selectedLanguage;
    private boolean selectedClassicMenu;
    private boolean french;

    private String text(String fr, String en) { return french ? fr : en; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    private TextView label(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(0xffe4dac5);
        view.setPadding(0, dp(12), 0, dp(8));
        return view;
    }

    private Button button(String value, Runnable action) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private Spinner spinner(String[] names, int initial) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(initial);
        return spinner;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        french = "fr".equalsIgnoreCase(getResources().getConfiguration().locale.getLanguage());
        v200 = getIntent().getBooleanExtra(EXTRA_V200, false);
        String currentLanguage = v200 ? UnrealDataPaths.gameLanguagePreference(this) : UnrealGold.language(this);
        selectedLanguage = state == null ? currentLanguage : state.getString("language", currentLanguage);
        selectedClassicMenu = state == null ? UnrealGold.classicMenu(this) : state.getBoolean("classicMenu", true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(12), dp(24), dp(16));

        body.addView(label(text("Manette sur écran tactile", "On-screen touch controller"), 17));
        String mode = UnrealGold.touch(this);
        Spinner touch = spinner(new String[]{text("Automatique (sans manette physique)", "Automatic (without a physical controller)"),
                text("Toujours afficher", "Always show"), text("Masquer", "Hide")}, mode.equals("on") ? 1 : mode.equals("off") ? 2 : 0);
        touch.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                UnrealGold.touch(GoldSettingsActivity.this, new String[]{"auto", "on", "off"}[position]);
            }
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        body.addView(touch);
        final TextView opacityLabel = label("", 15);
        final String opacityText = text("Opacité de la manette tactile : ", "On-screen controller opacity: ");
        int opacity = UnrealGold.touchOpacity(this);
        opacityLabel.setText(opacityText + opacity + " %");
        body.addView(opacityLabel);
        SeekBar opacityBar = new SeekBar(this);
        opacityBar.setMax(16); // 20..100 % in 5 % steps
        opacityBar.setProgress((opacity - 20) / 5);
        opacityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int percent = 20 + progress * 5;
                opacityLabel.setText(opacityText + percent + " %");
                UnrealGold.touchOpacity(GoldSettingsActivity.this, percent);
            }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        body.addView(opacityBar, new LinearLayout.LayoutParams(-1, dp(48)));
        body.addView(button(text("Modifier la disposition de la manette tactile", "Edit on-screen controller layout"), () -> {
            setResult(EDIT_TOUCH);
            finish();
        }));
        body.addView(button(text("Réinitialiser la disposition", "Reset layout"), () ->
                new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                        .setMessage(text("Remettre les boutons tactiles à leur position d’origine ?", "Restore the touch buttons to their default position?"))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(text("Réinitialiser", "Reset"), (dialog, which) -> { setResult(RESET_TOUCH); finish(); })
                        .show()));

        if (!v200) {
            body.addView(label(text("Affichage", "Display"), 17));
            displaySlider(body, text("Taille des menus classiques et du HUD : ", "Classic menu and HUD size: "),
                    UnrealGold.interfaceSize(this), .5f, .05f, 15, false);
            displaySlider(body, text("Gamma (zones sombres) : ", "Gamma (shadow detail): "),
                    UnrealGold.gamma(this), .75f, .05f, 45, true);
            body.addView(label(text("La taille s’adapte à la résolution. Les deux réglages s’appliquent au retour au jeu.",
                    "Size adapts to the resolution. Both settings apply when you return to the game."), 14));
        }

        body.addView(label(text("Aide à la visée (manette)", "Aim assist (controller)"), 17));
        Spinner aim = spinner(new String[]{text("Désactivée", "Off"), text("Légère", "Light"),
                text("Moyenne", "Medium"), text("Forte", "Strong")}, UnrealGold.aimAssist(this));
        aim.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                UnrealGold.aimAssist(GoldSettingsActivity.this, position);
            }
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        body.addView(aim);
        body.addView(label(text("Suivi progressif des ennemis proches du viseur. Un mouvement franc du stick reprend la main. Appliqué au retour au jeu.",
                "Gently tracks enemies near the crosshair. A firm stick movement overrides it. Applies when you return to the game."), 14));

        body.addView(label(text("Confort de la caméra", "Camera comfort"), 17));
        TextView rollLabel = label("", 15);
        body.addView(rollLabel);
        SeekBar roll = new SeekBar(this);
        roll.setMax(20);
        roll.setProgress(Math.round(UnrealGold.cameraRoll(this) * 20));
        roll.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                rollLabel.setText(text("Inclinaison latérale : ", "Sideways camera tilt: ") + (progress * 5) + " %"
                        + (progress == 0 ? text(" (désactivée)", " (disabled)") : ""));
                if (fromUser) UnrealGold.cameraRoll(GoldSettingsActivity.this, progress / 20f);
            }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        rollLabel.setText(text("Inclinaison latérale : ", "Sideways camera tilt: ") + Math.round(UnrealGold.cameraRoll(this) * 100) + " %");
        body.addView(roll, new LinearLayout.LayoutParams(-1, dp(48)));
        body.addView(label(text("0 % désactive l’inclinaison, 100 % conserve l’effet d’origine. Appliqué au retour au jeu.",
                "0% disables tilt; 100% keeps the original effect. Applies when you return to the game."), 14));

        body.addView(label(text("Langue du jeu", "Game language"), 17));
        final String[] codes = v200 ? V200_CODES : CODES;
        String[] names = v200 ? new String[]{text("Automatique (langue d’Android)", "Automatic (Android language)"), "Français", "English"} : NAMES;
        Spinner language = spinner(names, Math.max(0, java.util.Arrays.asList(codes).indexOf(selectedLanguage)));
        language.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) { selectedLanguage = codes[position]; }
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        body.addView(language);
        body.addView(label(v200
                ? text("La traduction française est téléchargée depuis OldUnreal au redémarrage si nécessaire.",
                    "The French translation is downloaded from OldUnreal on restart when needed.")
                : text("Les textes français manquants sont téléchargés au redémarrage. Les voix doivent être présentes dans les fichiers du jeu.",
                    "Missing French texts download on restart. Voice files must be present in the game data."), 14));

        if (v200) {
            body.addView(button(text("Appliquer la langue et relancer le jeu…", "Apply language and restart…"), () ->
                    confirm(text("Quitter la partie et relancer le jeu avec cette langue ?", "Leave the game and restart with this language?"), RESTART)));
        } else {
            body.addView(label(text("Menu du jeu", "Game menu"), 17));
            Spinner menu = spinner(new String[]{text("Classique (console, comme la v200)", "Classic (console, as in v200)"),
                    text("Fenêtres (UMenu d’Unreal Gold)", "Windows (Unreal Gold UMenu)")}, selectedClassicMenu ? 0 : 1);
            menu.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) { selectedClassicMenu = position == 0; }
                public void onNothingSelected(AdapterView<?> parent) { }
            });
            body.addView(menu);
            body.addView(button(text("Appliquer la langue et le menu, relancer le jeu…", "Apply language and menu, restart…"), () ->
                    confirm(text("Quitter la partie et relancer le jeu avec ces réglages ?", "Leave the game and restart with these settings?"), RESTART)));
        }

        body.addView(label(text("Emplacement du jeu", "Game location"), 17));
        TextView path = label(getIntent().getStringExtra(UnrealDataPaths.EXTRA_UNREAL_ROOT), 13);
        path.setTextIsSelectable(true);
        body.addView(path);
        body.addView(button(text("Changer de fichiers ou d’emplacement…", "Change game data or location…"), () ->
                confirm(text("Quitter la partie et ouvrir le gestionnaire de fichiers du jeu ? Les fichiers actuels seront conservés.",
                        "Leave the game and open the game data manager? Current files will be preserved."), DATA)));
        body.addView(button(text("Retour au jeu", "Return to game"), this::finish));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(body);
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(0xff181416);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(24), 0, dp(24), 0);
        header.addView(label(text("Réglages Android", "Android settings"), 22), new LinearLayout.LayoutParams(0, -2, 1));
        Button topBack = button(text("Retour au jeu", "Return to game"), this::finish);
        header.addView(topBack);
        outer.addView(header);
        outer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(outer);
        topBack.requestFocus();
    }

    private void displaySlider(LinearLayout body, String caption, float value, float min, float step, int max, boolean gamma) {
        TextView current = label("", 15);
        body.addView(current);
        SeekBar slider = new SeekBar(this);
        slider.setMax(max);
        slider.setProgress(Math.round((value - min) / step));
        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                float chosen = min + progress * step;
                current.setText(caption + String.format(java.util.Locale.getDefault(), gamma ? "%.2f" : "%.0f %%", gamma ? chosen : chosen * 100));
                if (fromUser) {
                    if (gamma) UnrealGold.gamma(GoldSettingsActivity.this, chosen);
                    else UnrealGold.interfaceSize(GoldSettingsActivity.this, chosen);
                }
            }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        };
        slider.setOnSeekBarChangeListener(listener);
        listener.onProgressChanged(slider, slider.getProgress(), false);
        body.addView(slider, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void confirm(String message, int result) {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(text("Continuer", "Continue"), (dialog, which) -> {
                    if (v200) {
                        if (result == RESTART) UnrealDataPaths.setGameLanguagePreference(this, selectedLanguage);
                        restartV200(result == DATA);
                        return;
                    }
                    if (result == RESTART) {
                        UnrealGold.setLanguage(this, selectedLanguage);
                        UnrealGold.classicMenu(this, selectedClassicMenu);
                    }
                    setResult(result);
                    finish();
                }).show();
    }

    /**
     * The v200 engine cannot restart inside its process, which is also the
     * launcher's: end it from this process, then open the launcher in a new one.
     */
    private void restartV200(boolean manageData) {
        int gamePid = getIntent().getIntExtra(EXTRA_GAME_PID, 0);
        if (gamePid > 0 && gamePid != android.os.Process.myPid()) android.os.Process.killProcess(gamePid);
        android.content.Intent launcher = new android.content.Intent(this, MainActivity.class);
        launcher.putExtra(MainActivity.EXTRA_MANAGE_DATA, manageData);
        launcher.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(launcher);
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("language", selectedLanguage);
        state.putBoolean("classicMenu", selectedClassicMenu);
        super.onSaveInstanceState(state);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.getAction() == KeyEvent.ACTION_UP) onBackPressed();
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_A) {
            return super.dispatchKeyEvent(new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(),
                    KeyEvent.KEYCODE_DPAD_CENTER, event.getRepeatCount()));
        }
        return super.dispatchKeyEvent(event);
    }
}
