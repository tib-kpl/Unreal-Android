package com.ast.unreal;

import android.app.Activity;
import android.content.ContextWrapper;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/** Game-themed Android settings for both v200 and Gold; shown over the existing engine surface. */
final class GoldSettingsPanel extends ContextWrapper {
    static final int RESTART = 3521, DATA = 3522, EDIT_TOUCH = 3523, RESET_TOUCH = 3524;
    private static final String[] NAMES = {"English", "Français", "Deutsch", "Español", "Italiano"};
    private static final String[] CODES = {"int", "frt", "det", "est", "itt"};
    private static final String[] V200_CODES = {UnrealDataPaths.LANGUAGE_AUTO, UnrealDataPaths.LANGUAGE_FRENCH, UnrealDataPaths.LANGUAGE_ENGLISH};
    private boolean v200, french, selectedClassicMenu;
    private String selectedLanguage;
    private GameSettingsMenu menu;
    private final File root;
    private int result;
    private void setResult(int action) { result = action; }
    private void finish() { menu.dismiss(); }

    static GameSettingsMenu open(Activity activity, File root, boolean v200, GameSettingsMenu.Result completed) {
        return new GoldSettingsPanel(activity, root, v200, completed).menu;
    }
    private String text(String fr, String en) { return french ? fr : en; }

    private GoldSettingsPanel(Activity activity, File root, boolean v200, GameSettingsMenu.Result completed) {
        super(activity);
        this.root = root;
        french = "fr".equalsIgnoreCase(getResources().getConfiguration().locale.getLanguage());
        this.v200 = v200;
        String currentLanguage = v200 ? UnrealDataPaths.gameLanguagePreference(this) : UnrealGold.language(this);
        selectedLanguage = currentLanguage;
        selectedClassicMenu = UnrealGold.classicMenu(this);
        menu = new GameSettingsMenu(activity, false);
        menu.setOnDismissListener(dialog -> { if (!activity.isFinishing()) completed.complete(result); });
        menu.page(text("Commandes", "Controls"), this::controls);
        menu.page(text("Confort", "Comfort"), this::comfort);
        if (!v200) menu.page(text("Affichage", "Display"), this::display);
        menu.page(text("Jeu", "Game"), this::game);
        menu.show(v200 ? "UNREAL" : "UNREAL GOLD", text("RÉGLAGES ANDROID  /  " , "ANDROID SETTINGS  /  ")
                + (v200 ? "v200" : "226"), -1);
    }

    private void controls() {
        menu.section(text("Manette sur écran tactile", "On-screen touch controller"));
        String mode = UnrealGold.touch(this);
        menu.choice(text("Visibilité", "Visibility"), new String[]{text("Automatique · sans manette physique", "Automatic · without a controller"),
                text("Toujours afficher", "Always show"), text("Masquer", "Hide")}, mode.equals("on") ? 1 : mode.equals("off") ? 2 : 0, 0,
                index -> UnrealGold.touch(this, new String[]{"auto", "on", "off"}[index]));
        String[] opacity = new String[17];
        for (int i = 0; i < opacity.length; i++) opacity[i] = (20 + i * 5) + " %";
        menu.slider(text("Opacité", "Opacity"), 16, (UnrealGold.touchOpacity(this) - 20) / 5, (UnrealGold.DEFAULT_TOUCH_OPACITY - 20) / 5, opacity,
                value -> UnrealGold.touchOpacity(this, 20 + value * 5));
        menu.button(text("Modifier la disposition tactile", "Edit touch layout"), () -> { setResult(EDIT_TOUCH); finish(); });
        menu.button(text("Réinitialiser la disposition", "Reset touch layout"), () ->
                menu.confirm(text("Remettre les boutons tactiles à leur position d’origine ?", "Restore the touch buttons to their default position?"),
                        text("Réinitialiser", "Reset"), () -> { setResult(RESET_TOUCH); finish(); }));
        menu.note(text("Select + Start ouvre ce menu à la manette. La petite icône est réservée au mode tactile.",
                "Select + Start opens these settings with a controller. The small icon appears in touch mode only."));
    }

    private void comfort() {
        menu.section(text("Aide à la visée · manette", "Aim assist · controller"));
        menu.choice(text("Intensité", "Strength"), new String[]{text("Désactivée", "Off"), text("Légère", "Light"),
                text("Moyenne", "Medium"), text("Forte", "Strong")}, UnrealGold.aimAssist(this), UnrealGold.DEFAULT_AIM_ASSIST, value -> UnrealGold.aimAssist(this, value));
        menu.note(text("Suivi progressif des ennemis proches du viseur. Un mouvement franc du stick reprend la main.",
                "Gently tracks enemies near the crosshair. A firm stick movement overrides it."));
        menu.section(text("Confort de la caméra", "Camera comfort"));
        String[] values = new String[21];
        for (int i = 0; i < values.length; i++) values[i] = (i * 5) + " %";
        menu.slider(text("Inclinaison latérale", "Sideways camera tilt"), 20, Math.round(UnrealGold.cameraRoll(this) * 20), Math.round(UnrealGold.DEFAULT_CAMERA_ROLL * 20), values,
                value -> UnrealGold.cameraRoll(this, value / 20f));
        menu.note(text("0 % désactive l’inclinaison, 100 % conserve l’effet d’origine. Ces réglages s’appliquent au retour au jeu.",
                "0% disables tilt; 100% keeps the original effect. These settings apply when you return to the game."));
    }

    private void display() {
        menu.section(text("Menus classiques et HUD", "Classic menus and HUD"));
        displaySlider(text("Taille de l’interface", "Interface size"), UnrealGold.interfaceSize(this), .5f, .05f, 15, false);
        menu.section(text("Lumière", "Lighting"));
        displaySlider(text("Gamma · zones sombres", "Gamma · shadow detail"), UnrealGold.gamma(this), .75f, .05f, 45, true);
        menu.note(text("La taille s’adapte à la résolution. Les réglages s’appliquent au retour au jeu.",
                "Size adapts to the resolution. Settings apply when you return to the game."));
    }

    private void displaySlider(String caption, float value, float min, float step, int max, boolean gamma) {
        String[] values = new String[max + 1];
        for (int i = 0; i <= max; i++) values[i] = String.format(Locale.getDefault(), gamma ? "%.2f" : "%.0f %%", gamma ? min + i * step : (min + i * step) * 100);
        menu.slider(caption, max, Math.max(0, Math.min(max, Math.round((value - min) / step))), Math.round(((gamma ? UnrealGold.DEFAULT_GAMMA : UnrealGold.DEFAULT_INTERFACE_SIZE) - min) / step), values, index -> {
            if (gamma) UnrealGold.gamma(this, min + index * step);
            else UnrealGold.interfaceSize(this, min + index * step);
        });
    }

    private void game() {
        menu.section(text("Langue du jeu", "Game language"));
        final String[] codes = v200 ? V200_CODES : CODES;
        String[] names = v200 ? new String[]{text("Automatique · langue d’Android", "Automatic · Android language"), "Français", "English"} : NAMES;
        menu.choice(text("Langue", "Language"), names, Math.max(0, Arrays.asList(codes).indexOf(selectedLanguage)), v200 ? 0 : Arrays.asList(CODES).indexOf(UnrealGold.defaultLanguage()), index -> selectedLanguage = codes[index]);
        menu.note(v200 ? text("La traduction française sera téléchargée au redémarrage si nécessaire.", "The French translation downloads on restart if needed.")
                : text("Les textes français manquants seront téléchargés au redémarrage. Les voix doivent être présentes dans les fichiers du jeu.",
                        "Missing French texts download on restart. Voice files must be present in the game data."));
        if (!v200) {
            menu.choice(text("Menu du jeu", "Game menu"), new String[]{text("Classique · comme la v200", "Classic · as in v200"),
                    text("Fenêtres · Unreal Gold UMenu", "Windows · Unreal Gold UMenu")}, selectedClassicMenu ? 0 : 1, 0,
                    index -> selectedClassicMenu = index == 0);
        }
        menu.button(text("Appliquer et relancer le jeu…", "Apply and restart the game…"), () ->
                confirm(text("Quitter la partie et relancer le jeu avec ces réglages ?", "Leave the game and restart with these settings?"), RESTART));
        defaultsSection();
        menu.section(text("Fichiers du jeu", "Game data"));
        menu.path(root.getAbsolutePath());
        menu.button(text("Changer de fichiers ou d’emplacement…", "Change game data or location…"), () ->
                confirm(text("Quitter la partie et ouvrir le gestionnaire de fichiers du jeu ? Les fichiers actuels seront conservés.",
                        "Leave the game and open the game data manager? Current files will be preserved."), DATA));
    }

    private void defaultsSection() {
        menu.section(text("Réglages par défaut", "Default settings"));
        menu.button(text("Réinitialiser les réglages Android…", "Reset Android settings…"), () ->
                menu.confirm(text("Rétablir les valeurs par défaut des réglages Android ? La disposition tactile, les fichiers et les sauvegardes seront conservés. La langue et le menu du jeu prendront effet à la prochaine relance.",
                        "Restore the default Android settings? Touch layouts, game data and saves will be preserved. Language and game menu changes take effect on the next launch."),
                        text("Réinitialiser", "Reset"), () -> {
            UnrealGold.resetAndroidSettings(this, !v200);
            selectedLanguage = v200 ? UnrealDataPaths.gameLanguagePreference(this) : UnrealGold.language(this);
            selectedClassicMenu = UnrealGold.classicMenu(this);
            menu.refresh();
            android.widget.Toast.makeText(this, text("Réglages réinitialisés", "Settings reset"), android.widget.Toast.LENGTH_SHORT).show();
        }));
        menu.note(text("Disposition tactile et fichiers du jeu conservés. Langue et menu : prochaine relance.",
                "Touch layout and game data are preserved. Language and game menu: next launch."));
    }

    private void confirm(String message, int result) {
        menu.confirm(message, text("Continuer", "Continue"), () -> {
                    if (v200) {
                        if (result == RESTART) UnrealDataPaths.setGameLanguagePreference(this, selectedLanguage);
                        setResult(result);
                        finish();
                        return;
                    }
                    if (result == RESTART) {
                        UnrealGold.setLanguage(this, selectedLanguage);
                        UnrealGold.classicMenu(this, selectedClassicMenu);
                    }
                    setResult(result);
                    finish();
                });
    }

}
