package com.ast.unreal;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.lang.ref.WeakReference;
import java.util.Locale;

public class MainActivity extends Activity {
    // UNREAL_ANDROID_API16_ACTIVITY_V212: keep the launcher class loadable on Android 4.1/API 16.

    private static final int REQ_LEGACY_STORAGE = 2001;
    private static final int REQ_SELECT_UNREAL_FOLDER = 3001;
    private static final int REQ_SELECT_UNREAL_ZIP = 3002;
    private static final int REQ_SELECT_PARENT_FOLDER = 3003;
    private static final int REQ_FILE_ACCESS = 2002;
    /** Opens the game data manager instead of starting the remembered game. */
    static final String EXTRA_MANAGE_DATA = "com.ast.unreal.EXTRA_MANAGE_DATA";
    // UNREAL_ANDROID_FRENCH_LANGUAGE_V221: set by the "Game language" launcher shortcut.
    static final String EXTRA_CHOOSE_LANGUAGE = "com.ast.unreal.EXTRA_CHOOSE_LANGUAGE";

    // UNREAL_ANDROID_IMPORT_SINGLE_V222
    // The import thread outlives the Activity instance that started it (the
    // Activity can be recreated, e.g. when a controller reconnects). Track the
    // import process-wide so a new instance shows progress instead of starting
    // the game on half-copied data or a second import, and hand the result to
    // whichever instance is current when the import ends.
    private static final Object IMPORT_LOCK = new Object();
    private static boolean sImportRunning;
    private static UnrealDataPaths.ImportResult sPendingImportResult;
    private static WeakReference<MainActivity> sCurrentInstance = new WeakReference<>(null);

    private File selectedRoot;
    private String lastImportMessage;
    private TextView busyMessage;
    private Thread goldInstallerThread;
    private Destination pendingDestination;
    private Runnable afterFileAccess;
    private boolean awaitingAllFilesAccess;

    private File unrealRoot() {
        if (selectedRoot == null) selectedRoot = UnrealDataPaths.findBestUnrealRoot(this);
        return selectedRoot;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        hideSystemUi();
        sCurrentInstance = new WeakReference<>(this);

        if (resumeImportState()) return;

        if (needsLegacyStoragePermission()) {
            requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE }, REQ_LEGACY_STORAGE);
            return;
        }
        continueStartup();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (awaitingAllFilesAccess) {
            awaitingAllFilesAccess = false;
            continueAfterFileAccess();
        }
    }

    @Override
    protected void onDestroy() {
        if (sCurrentInstance.get() == this) sCurrentInstance = new WeakReference<>(null);
        super.onDestroy();
    }

    private boolean resumeImportState() {
        UnrealDataPaths.ImportResult pending;
        synchronized (IMPORT_LOCK) {
            if (sImportRunning) {
                showBusyScreen(
                        t("Unreal-Daten werden importiert", "Importing Unreal data"),
                        t("Der Import läuft noch. Bitte warten.", "The import is still running. Please wait."));
                return true;
            }
            pending = sPendingImportResult;
            sPendingImportResult = null;
        }
        if (pending == null) return false;
        handleImportResult(pending);
        return true;
    }

    private static void deliverPendingImportResult() {
        MainActivity activity = sCurrentInstance.get();
        if (activity == null || activity.isFinishing()) return; // the next onCreate picks it up
        UnrealDataPaths.ImportResult result;
        synchronized (IMPORT_LOCK) {
            result = sPendingImportResult;
            sPendingImportResult = null;
        }
        if (result != null) activity.handleImportResult(result);
    }

    private void startZipImport(final Uri uri) {
        synchronized (IMPORT_LOCK) {
            if (sImportRunning) return;
            sImportRunning = true;
            sPendingImportResult = null;
        }
        final Context appContext = getApplicationContext();
        new Thread(() -> {
            UnrealDataPaths.ImportResult result = null;
            try {
                result = UnrealDataPaths.importUnrealZip(appContext, uri);
            } catch (Throwable t) {
                result = UnrealDataPaths.ImportResult.fail("Import failed.", t);
            } finally {
                synchronized (IMPORT_LOCK) {
                    sImportRunning = false;
                    sPendingImportResult = result;
                }
                new Handler(Looper.getMainLooper()).post(MainActivity::deliverPendingImportResult);
            }
        }, "UE1ZipImport").start();
    }

    private void setKeepScreenOn(boolean on) {
        // A large import can take minutes; do not let the device sleep (or kill us) mid-copy.
        if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private boolean needsLegacyStoragePermission() {
        if (Build.VERSION.SDK_INT < 23 || Build.VERSION.SDK_INT > 32) return false;
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) return false;
        return !UnrealDataPaths.hasRequiredData(UnrealDataPaths.primaryAppRoot(this));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LEGACY_STORAGE) continueStartup();
        if (requestCode == REQ_FILE_ACCESS) continueAfterFileAccess();
    }

    private void continueStartup() {
        boolean manage = getIntent() != null && getIntent().getBooleanExtra(EXTRA_MANAGE_DATA, false);
        File gold = UnrealGold.rememberedRoot(this);
        if (gold != null && !manage) {
            launchGold(gold);
            return;
        }
        selectedRoot = UnrealDataPaths.findBestUnrealRoot(this);
        if (manage) {
            showMissingDataScreen();
            return;
        }
        UnrealDataPaths.ensureDirectoryLayout(selectedRoot);
        UnrealDataPaths.installDefaultConfigsIfNeeded(this, selectedRoot);
        UnrealDataPaths.normalizeConfigForDetectedData(selectedRoot);

        if (UnrealDataPaths.hasRequiredData(selectedRoot, true)) {
            android.util.Log.i(UnrealDataPaths.TAG_STARTUP, "data check OK root=" + selectedRoot.getAbsolutePath());
            startGameWithLanguage(selectedRoot);
            return;
        }
        android.util.Log.w(UnrealDataPaths.TAG_STARTUP, "data check failed root=" + selectedRoot.getAbsolutePath());
        showMissingDataScreen();
    }

    // UNREAL_ANDROID_FRENCH_LANGUAGE_V221:
    // Ask for the game language on first start (or from the launcher shortcut),
    // fetch the French localization when needed, then start the engine.
    private void startGameWithLanguage(File root) {
        if (UnrealGold.isGoldData(root)) {
            UnrealGold.remember(this, root, null);
            launchGold(root);
            return;
        }
        boolean askLanguage = getIntent() != null && getIntent().getBooleanExtra(EXTRA_CHOOSE_LANGUAGE, false);
        if (askLanguage || !UnrealDataPaths.hasGameLanguagePreference(this)) {
            if (getIntent() != null) getIntent().removeExtra(EXTRA_CHOOSE_LANGUAGE);
            showLanguageChooser(root);
            return;
        }
        final File systemDir = new File(root, "System");
        if (!UnrealDataPaths.wantsFrench(this)
                || (UnrealDataPaths.hasFrenchLocalization(systemDir) && !UnrealDataPaths.needsFrenchLocalizationUpdate(systemDir))) {
            launchGame(root);
            return;
        }
        showBusyScreen(
                "Téléchargement de la traduction française",
                "Récupération des fichiers de langue (OldUnreal)…\nDownloading French language files…");
        new Thread(() -> {
            boolean ok = UnrealDataPaths.downloadFrenchLocalization(root);
            runOnUiThread(() -> {
                if (!ok) {
                    android.widget.Toast.makeText(this,
                            "Traduction française indisponible (pas de connexion ?). Le jeu démarre en anglais.",
                            android.widget.Toast.LENGTH_LONG).show();
                }
                launchGame(root);
            });
        }, "UE1FrenchDownload").start();
    }

    private void showLanguageChooser(File root) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER);
        body.setPadding(48, 36, 48, 36);

        TextView title = new TextView(this);
        title.setText("Langue du jeu / Game language");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        body.addView(title);

        TextView msg = new TextView(this);
        msg.setText("Modifiable plus tard : appui long sur l'icône > Langue du jeu.\n" +
                "Can be changed later: long-press the app icon > Game language.");
        msg.setTextSize(16);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, 24, 0, 24);
        body.addView(msg);

        String current = UnrealDataPaths.hasGameLanguagePreference(this)
                ? UnrealDataPaths.gameLanguagePreference(this) : UnrealDataPaths.LANGUAGE_AUTO;
        Button focus = null;
        String[][] choices = {
                { UnrealDataPaths.LANGUAGE_AUTO, "Automatique (langue d'Android) / Automatic" },
                { UnrealDataPaths.LANGUAGE_FRENCH, "Français" },
                { UnrealDataPaths.LANGUAGE_ENGLISH, "English" },
        };
        for (String[] choice : choices) {
            final String value = choice[0];
            Button button = new Button(this);
            button.setText(choice[1]);
            button.setAllCaps(false);
            button.setOnClickListener(v -> {
                UnrealDataPaths.setGameLanguagePreference(this, value);
                startGameWithLanguage(root);
            });
            body.addView(button);
            if (value.equals(current)) focus = button;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        setContentView(scroll);
        hideSystemUi();
        // Gamepad-only devices (AYN Odin/Thor, TV) need an initial focus.
        if (focus != null) {
            final Button initial = focus;
            initial.post(initial::requestFocus);
        }
    }

    private boolean rejectUnsupportedGold(File root) {
        if (!UnrealGold.isGoldData(root) || UnrealGold.isSupportedGoldData(root)) return false;
        lastImportMessage = g("Ce dossier contient une version patchée ou incompatible (par exemple 227). Ce port nécessite les fichiers Unreal Gold 226 d’origine. Utilisez un autre dossier ou installez Gold 226 avec le bouton de téléchargement. Les fichiers et sauvegardes existants sont conservés.\n",
                "This folder contains patched or incompatible data (such as 227). This port requires original Unreal Gold 226 files. Choose another folder or install Gold 226 with the download button. Existing files and saves are preserved.\n")
                + root.getAbsolutePath();
        showMissingDataScreen();
        dialog().setTitle(g("Fichiers du jeu incompatibles", "Incompatible game files"))
                .setMessage(lastImportMessage)
                .setPositiveButton(g("Télécharger et installer Gold 226", "Download and install Gold 226"),
                        (d, which) -> chooseGoldLanguage())
                .setNegativeButton(g("Choisir un autre dossier", "Choose another folder"), (d, which) -> {
                    pendingDestination = null;
                    requestFileAccess(this::openParentFolderPicker);
                })
                .setNeutralButton(android.R.string.cancel, null).show();
        return true;
    }

    private void launchGold(File root) {
        if (rejectUnsupportedGold(root)) return;
        if ("frt".equals(UnrealGold.language(this)) && UnrealDataPaths.needsGoldFrenchLocalization(root)) {
            showBusyScreen("Traduction française d’Unreal Gold",
                    "Téléchargement des textes manquants (traducteur, objectifs, menus)…");
            new Thread(() -> {
                UnrealDataPaths.downloadFrenchLocalization(root);
                runOnUiThread(() -> {
                    if (UnrealDataPaths.needsGoldFrenchLocalization(root))
                        android.widget.Toast.makeText(this, "Certains textes français restent indisponibles. Vérifiez la connexion.",
                                android.widget.Toast.LENGTH_LONG).show();
                    startGoldActivity(root);
                });
            }, "GoldFrenchLocalization").start();
            return;
        }
        startGoldActivity(root);
    }

    private void startGoldActivity(File root) {
        Intent intent = new Intent(this, Unreal226Activity.class);
        intent.putExtra(UnrealDataPaths.EXTRA_UNREAL_ROOT, root.getAbsolutePath());
        startActivity(intent);
        finish();
    }

    private void launchGame(File root) {
        UnrealDataPaths.rememberUnrealRoot(this, root);
        UnrealGold.forget(this);
        Intent intent = new Intent(this, UnrealSDLActivity.class);
        intent.putExtra(UnrealDataPaths.EXTRA_UNREAL_ROOT, root.getAbsolutePath());
        startActivity(intent);
        finish();
    }

    private void hideSystemUi() {
        View decor = getWindow().getDecorView();
        // These flags are integer constants and are harmless on pre-KitKat devices;
        // they also keep the API-16 path free of references to WindowInsets classes.
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        if (Build.VERSION.SDK_INT >= 30) Api30SystemUi.hide(decor);
    }

    @android.annotation.TargetApi(30)
    private static final class Api30SystemUi {
        private Api30SystemUi() {}
        static void hide(View decor) {
            android.view.WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller == null) return;
            controller.hide(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
            controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private Locale currentLocale() {
        if (Build.VERSION.SDK_INT >= 24) return Api24Locale.current(getResources().getConfiguration());
        return getResources().getConfiguration().locale;
    }

    @android.annotation.TargetApi(24)
    private static final class Api24Locale {
        private Api24Locale() {}
        static Locale current(android.content.res.Configuration configuration) {
            return configuration.getLocales().get(0);
        }
    }

    private boolean isGermanUi() {
        Locale locale = currentLocale();
        return locale != null && "de".equalsIgnoreCase(locale.getLanguage());
    }

    private String t(String de, String en) {
        return isGermanUi() ? de : en;
    }

    private void showMissingDataScreen() {
        setKeepScreenOn(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER);
        body.setPadding(48, 36, 48, 36);

        TextView title = new TextView(this);
        final boolean hasData = UnrealGold.rememberedRoot(this) != null
                || (selectedRoot != null && UnrealDataPaths.hasRequiredData(selectedRoot));
        title.setText(hasData ? g("Fichiers du jeu", t("Spieldaten", "Game data"))
                : g("Données Unreal introuvables", t("Unreal-Daten fehlen", "Unreal data not found")));
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        body.addView(title);

        TextView msg = new TextView(this);
        String extra = "";
        if (lastImportMessage != null && lastImportMessage.length() > 0) {
            extra = g("\n\nDernier message :\n", t("\n\nLetzte Meldung:\n", "\n\nLast message:\n")) + lastImportMessage;
        }
        msg.setText(t(
                "Es wurde kein vollständig lesbarer Unreal-Datenordner gefunden.\n\n" +
                "Wähle jetzt entweder den Ordner 'Unreal' aus oder importiere direkt eine ZIP-Datei mit den Unreal-Daten.\n\n" +
                "Der gewählte Ordner bzw. die ZIP-Datei muss mindestens enthalten:\n" +
                "System/Core.u\nSystem/Engine.u\nSystem/UnrealI.u oder System/UnrealShare.u\nMaps/*.unr" + extra,
                "No fully readable Unreal data folder was found.\n\n" +
                "Use your existing Unreal folder without copying, or import a ZIP file containing the Unreal data.\n\n" +
                "Folders are used in place; ZIP files are extracted into the app folder. Required files:\n" +
                "System/Core.u\nSystem/Engine.u\nSystem/UnrealI.u or System/UnrealShare.u\nMaps/*.unr" + extra));
        if (isFrenchUi()) {
            msg.setText("Téléchargez Unreal Gold depuis OldUnreal, utilisez un dossier Unreal Gold existant, "
                    + "utilisez votre dossier Unreal v200 sans copie, ou importez une archive ZIP.\n\n"
                    + "L’installation Unreal Gold permet de choisir la langue, le stockage interne, la microSD ou un dossier personnel.\n\n"
                    + "Fichiers requis : System/Core.u, System/Engine.u, System/UnrealShare.u, Maps/*.unr" + extra);
        }
        msg.setTextSize(16);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, 24, 0, 24);
        body.addView(msg);

        Button focus = null;
        final File gold = UnrealGold.rememberedRoot(this);
        if (gold != null && UnrealGold.isSupportedGoldData(gold)) {
            focus = goldButton(g("Lancer Unreal Gold", "Start Unreal Gold") + "\n" + gold.getAbsolutePath(), () -> launchGold(gold));
            body.addView(focus);
        }
        if (selectedRoot != null && !UnrealGold.isGoldData(selectedRoot) && UnrealDataPaths.hasRequiredData(selectedRoot)) {
            Button v200 = goldButton(g("Lancer Unreal (fichiers v200)", "Start Unreal (v200 data)") + "\n" + selectedRoot.getAbsolutePath(),
                    () -> startGameWithLanguage(selectedRoot));
            body.addView(v200);
            if (focus == null) focus = v200;
        }
        Button install = goldButton(g("Télécharger et installer Unreal Gold (OldUnreal)", "Download and install Unreal Gold (OldUnreal)"),
                this::chooseGoldLanguage);
        body.addView(install);
        if (focus == null) focus = install;
        body.addView(goldButton(g("Utiliser un dossier Unreal Gold existant", "Use an existing Unreal Gold folder"), () -> {
            pendingDestination = null;
            requestFileAccess(this::openParentFolderPicker);
        }));

        Button chooseFolder = new Button(this);
        chooseFolder.setText(g("Utiliser mon dossier Unreal", t("Meinen Unreal-Ordner verwenden", "Use my Unreal folder")));
        chooseFolder.setAllCaps(false);
        chooseFolder.setOnClickListener(v -> requestFileAccess(this::openUnrealFolderPicker));
        body.addView(chooseFolder);

        Button chooseZip = new Button(this);
        chooseZip.setText(g("Importer une archive ZIP", t("Unreal-ZIP auswählen", "Select Unreal ZIP")));
        chooseZip.setAllCaps(false);
        chooseZip.setOnClickListener(v -> openUnrealZipPicker());
        body.addView(chooseZip);

        Button retry = new Button(this);
        retry.setText(g("Vérifier à nouveau", t("Erneut prüfen", "Check again")));
        retry.setAllCaps(false);
        retry.setOnClickListener(v -> {
            selectedRoot = UnrealDataPaths.findBestUnrealRoot(this);
            UnrealDataPaths.ensureDirectoryLayout(selectedRoot);
            UnrealDataPaths.installDefaultConfigsIfNeeded(this, selectedRoot);
            UnrealDataPaths.normalizeConfigForDetectedData(selectedRoot);
            if (UnrealDataPaths.hasRequiredData(selectedRoot, true)) {
                startGameWithLanguage(selectedRoot);
            } else {
                lastImportMessage = t("Noch immer kein gültiger Unreal-Datenordner gefunden.", "Still no valid Unreal data folder found.");
                showMissingDataScreen();
            }
        });
        body.addView(retry);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        setContentView(scroll);
        hideSystemUi();
        final Button initial = focus;
        initial.post(initial::requestFocus);
    }

    private boolean isFrenchUi() {
        Locale locale = currentLocale();
        return locale != null && "fr".equalsIgnoreCase(locale.getLanguage());
    }

    private String g(String fr, String en) {
        return isFrenchUi() ? fr : en;
    }

    private Button goldButton(String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
    }

    @Override
    public void onBackPressed() {
        if (goldInstallerThread == null) { super.onBackPressed(); return; }
        confirmCancelGold();
    }

    private void confirmCancelGold() {
        dialog().setMessage(g("Annuler l’installation en cours ?", "Cancel the current installation?"))
                .setNegativeButton(g("Continuer", "Continue"), null)
                .setPositiveButton(g("Annuler l’installation", "Cancel installation"), (d, which) -> {
                    if (goldInstallerThread != null) goldInstallerThread.interrupt();
                }).show();
    }

    private void chooseGoldLanguage() {
        final String[] names = {"English", "Français", "Deutsch", "Español", "Italiano"};
        final String[] codes = {"int", "frt", "det", "est", "itt"};
        dialog().setTitle(g("Langue du jeu", "Game language"))
                .setItems(names, (d, which) -> chooseStorageVolume(volume ->
                        chooseDestinationFolder(volume, destination -> installGold(destination, codes[which]))))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private interface Destination { void use(File folder); }

    private void chooseStorageVolume(Destination chosen) {
        final List<File> volumes = new ArrayList<>();
        File[] candidates = Build.VERSION.SDK_INT >= 19 ? getExternalFilesDirs(null) : new File[] { getExternalFilesDir(null) };
        if (candidates != null) for (File dir : candidates) if (dir != null) volumes.add(dir);
        if (volumes.isEmpty()) volumes.add(getFilesDir());
        String[] names = new String[volumes.size() + 1];
        for (int i = 0; i < volumes.size(); i++) {
            names[i] = (i == 0 ? g("Stockage interne", "Internal storage") : g("Carte SD / stockage externe", "SD card / external storage"))
                    + "\n" + volumes.get(i).getAbsolutePath();
        }
        names[volumes.size()] = g("Autre dossier…\nAu choix, conservé si l’application est désinstallée",
                "Other folder…\nAny location, kept if the app is uninstalled");
        dialog().setTitle(g("Emplacement d’installation", "Install location"))
                .setItems(names, (d, which) -> {
                    if (which < volumes.size()) chosen.use(volumes.get(which));
                    else {
                        pendingDestination = chosen;
                        requestFileAccess(this::openParentFolderPicker);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private boolean hasAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    // The native engine needs real file paths, so a folder outside the app's
    // own directories requires "All files access" (or the legacy storage
    // permission before Android 11).
    private void requestFileAccess(Runnable granted) {
        if (hasAllFilesAccess()) { granted.run(); return; }
        afterFileAccess = granted;
        dialog().setTitle(g("Autoriser l’accès aux fichiers", "Allow file access"))
                .setMessage(g("Pour utiliser un dossier de votre choix, Android doit autoriser « Accès à tous les fichiers » pour cette application. Le jeu n’utilise que le dossier choisi.",
                        "To use a folder of your choice, Android must allow “All files access” for this app. The game only uses the chosen folder."))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(g("Autoriser", "Allow"), (d, which) -> {
                    if (Build.VERSION.SDK_INT >= 30) {
                        awaitingAllFilesAccess = true;
                        try {
                            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (ActivityNotFoundException ex) {
                            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                        }
                    } else {
                        requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQ_FILE_ACCESS);
                    }
                }).show();
    }

    private void continueAfterFileAccess() {
        Runnable next = afterFileAccess;
        afterFileAccess = null;
        if (next != null && hasAllFilesAccess()) next.run();
    }

    private void openParentFolderPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            startActivityForResult(intent, REQ_SELECT_PARENT_FOLDER);
        } catch (ActivityNotFoundException ex) {
            android.widget.Toast.makeText(this, g("Sélecteur de dossiers indisponible", "Folder picker unavailable"),
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    /** Maps a local storage tree URI ("primary:Games" or "1234-ABCD:Games") to its file path. */
    private static File treeUriToFile(Uri uri) {
        if (uri == null || !"com.android.externalstorage.documents".equals(uri.getAuthority())) return null;
        String id = DocumentsContract.getTreeDocumentId(uri);
        int colon = id.indexOf(':');
        if (colon < 0) return null;
        String volume = id.substring(0, colon), path = id.substring(colon + 1);
        File root = "primary".equalsIgnoreCase(volume) ? Environment.getExternalStorageDirectory() : new File("/storage/" + volume);
        return path.isEmpty() ? root : new File(root, path);
    }

    private void onParentFolderChosen(File folder) {
        Destination chosen = pendingDestination;
        pendingDestination = null;
        if (folder == null) {
            lastImportMessage = g("Ce dossier n’est pas un dossier local du téléphone.", "This folder is not a local device folder.");
            showMissingDataScreen();
            return;
        }
        if (rejectUnsupportedGold(folder)) return;
        if (UnrealDataPaths.hasRequiredData(folder) && (chosen == null || UnrealGold.isGoldData(folder))) {
            // Both engines require writable local paths for configs and saves.
            if (!folder.canWrite() || !new File(folder, "System").canWrite()) {
                lastImportMessage = g("Ce dossier doit être accessible en écriture pour les réglages et sauvegardes : ",
                        "This folder must be writable for settings and saves: ") + folder.getAbsolutePath();
                showMissingDataScreen();
                return;
            }
            boolean gold = UnrealGold.isGoldData(folder);
            dialog().setTitle(gold ? g("Unreal Gold trouvé", "Unreal Gold found") : g("Unreal v200 trouvé", "Unreal v200 found"))
                    .setMessage(g("Les fichiers de ce dossier seront utilisés directement.\n", "The files in this folder will be used directly.\n")
                            + folder.getAbsolutePath())
                    .setPositiveButton(g("Utiliser ces fichiers", "Use these files"), (d, which) -> {
                        selectedRoot = folder;
                        startGameWithLanguage(folder);
                    })
                    .setNegativeButton(android.R.string.cancel, null).show();
            return;
        }
        if (chosen == null) {
            lastImportMessage = g("Aucun dossier Unreal valide trouvé (System/Core.u, System/Engine.u et Maps/*.unr requis) dans : ",
                    "No valid Unreal folder found (requires System/Core.u, System/Engine.u and Maps/*.unr) in: ")
                    + folder.getAbsolutePath();
            showMissingDataScreen();
            return;
        }
        chooseDestinationFolder(folder, chosen);
    }

    private boolean isAppOwnedFolder(File folder) {
        String path = folder.getAbsolutePath();
        return path.contains("/Android/data/" + getPackageName()) || path.startsWith(getFilesDir().getAbsolutePath());
    }

    private void chooseDestinationFolder(File volume, Destination chosen) {
        // An empty personal folder is used as is; otherwise a new sub-folder
        // keeps the existing files untouched.
        String[] content = volume.list();
        if (!isAppOwnedFolder(volume) && content != null && content.length == 0) {
            dialog().setTitle(g("Installer dans ce dossier ?", "Install into this folder?"))
                    .setMessage(g("Le dossier est vide. Le jeu y sera installé directement et conservé si l’application est désinstallée.\n",
                            "The folder is empty. The game will be installed directly in it and kept if the app is uninstalled.\n")
                            + volume.getAbsolutePath())
                    .setPositiveButton(g("Installer ici", "Install here"), (d, which) -> chosen.use(volume))
                    .setNeutralButton(g("Nouveau sous-dossier", "New sub-folder"), (d, which) -> chooseSubfolderName(volume, chosen))
                    .setNegativeButton(android.R.string.cancel, null).show();
            return;
        }
        chooseSubfolderName(volume, chosen);
    }

    private void chooseSubfolderName(File volume, Destination chosen) {
        EditText name = new EditText(this);
        name.setSingleLine(true);
        String proposed = "UnrealGold";
        for (int index = 2; new File(volume, proposed).exists() && index < 1000; index++) proposed = "UnrealGold-" + index;
        name.setText(proposed);
        AlertDialog nameDialog = dialog()
                .setTitle(g("Nom du nouveau dossier", "New folder name"))
                .setMessage((isAppOwnedFolder(volume)
                        ? g("Dossier de l’application, supprimé si elle est désinstallée. ", "App folder, removed when the app is uninstalled. ")
                        : g("Dossier personnel, conservé si l’application est désinstallée. ", "Personal folder, kept if the app is uninstalled. "))
                        + g("Environ 680 Mo à télécharger et 1,3 Go libres nécessaires.\n", "About 680 MB to download and 1.3 GB free required.\n")
                        + volume.getAbsolutePath())
                .setView(name).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(g("Installer", "Install"), null).create();
        nameDialog.setOnShowListener(ignored -> nameDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String folder = name.getText().toString().trim();
            if (folder.isEmpty() || folder.equals(".") || folder.equals("..")
                    || folder.contains("/") || folder.contains("\\") || folder.contains(":")) {
                name.setError(g("Nom de dossier invalide", "Invalid folder name"));
                return;
            }
            File destination = new File(volume, folder);
            if (destination.exists()) {
                name.setError(g("Ce dossier existe déjà ; choisissez un nouveau nom", "Folder already exists; choose a new name"));
                return;
            }
            nameDialog.dismiss();
            chosen.use(destination);
        }));
        nameDialog.show();
    }

    private void installGold(File destination, String language) {
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        showBusyScreen(g("Installation d’Unreal Gold", "Unreal Gold installation"),
                g("Préparation du téléchargement officiel OldUnreal…", "Preparing the official OldUnreal download…"));
        final TextView progressText = busyMessage;
        ((LinearLayout) progressText.getParent()).addView(goldButton(
                g("Annuler l’installation", "Cancel installation"), this::confirmCancelGold));
        final String downloading = g("Téléchargement", "Downloading");
        final String extracting = g("Extraction", "Extracting");
        goldInstallerThread = new Thread(() -> {
            Exception failure = null;
            try {
                UnrealGold.install(new File(getCacheDir(), "oldunreal-installer"), destination, language, (phase, percent) ->
                        runOnUiThread(() -> progressText.setText((phase.startsWith("Unreal Gold · ")
                                ? extracting + " " + percent + " %\n" + phase.substring("Unreal Gold · ".length())
                                : downloading + " Unreal Gold : " + percent + " %"))));
                UnrealGold.remember(this, destination, language);
            } catch (Exception error) {
                failure = error;
                android.util.Log.e(UnrealDataPaths.TAG_IMPORT, "Unreal Gold install failed", error);
            }
            final Exception error = failure;
            final boolean cancelled = Thread.currentThread().isInterrupted() || error instanceof java.io.InterruptedIOException;
            runOnUiThread(() -> {
                goldInstallerThread = null;
                getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                if (error == null) {
                    launchGold(destination);
                    return;
                }
                lastImportMessage = cancelled ? g("Installation annulée.", "Installation cancelled.")
                        : g("Installation interrompue : ", "Installation stopped: ") + error.getMessage();
                showMissingDataScreen();
            });
        }, "UnrealGoldInstaller");
        goldInstallerThread.start();
    }

    private void showBusyScreen(String titleText, String messageText) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER);
        body.setPadding(48, 36, 48, 36);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        body.addView(title);

        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        body.addView(progress);

        TextView msg = new TextView(this);
        msg.setText(messageText);
        msg.setTextSize(16);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, 24, 0, 24);
        body.addView(msg);
        busyMessage = msg;

        setContentView(body);
        hideSystemUi();
        setKeepScreenOn(true);
    }

    private void openUnrealFolderPicker() {
        if (Build.VERSION.SDK_INT < 21) {
            lastImportMessage = t(
                    "Dieser Android-Stand bietet keinen systemeigenen Ordnerauswahldialog. Kopiere den Ordner 'Unreal' auf USB/SD oder in den angezeigten App-Ordner und wähle 'Erneut prüfen'.",
                    "This Android version has no system folder picker. Copy the 'Unreal' folder to USB/SD or into the shown app folder and choose 'Check again'.");
            showMissingDataScreen();
            return;
        }
        try {
            Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT_TREE");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (Build.VERSION.SDK_INT >= 19) intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            if (Build.VERSION.SDK_INT >= 21) intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            startActivityForResult(intent, REQ_SELECT_UNREAL_FOLDER);
        } catch (ActivityNotFoundException ex) {
            lastImportMessage = t(
                    "Auf diesem Gerät wurde kein kompatibler Ordnerauswahldialog gefunden: ",
                    "No compatible folder picker was found on this device: ") + ex.getMessage();
            showMissingDataScreen();
        }
    }

    private void openUnrealZipPicker() {
        try {
            String action = Build.VERSION.SDK_INT >= 19 ? "android.intent.action.OPEN_DOCUMENT" : Intent.ACTION_GET_CONTENT;
            Intent intent = new Intent(action);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (Build.VERSION.SDK_INT >= 19) intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            Intent chooser = Intent.createChooser(intent, t("Unreal-ZIP auswählen", "Select Unreal ZIP file"));
            startActivityForResult(chooser, REQ_SELECT_UNREAL_ZIP);
        } catch (ActivityNotFoundException ex) {
            lastImportMessage = t(
                    "Auf diesem Gerät wurde kein kompatibler Dateiauswahldialog gefunden: ",
                    "No compatible file picker was found on this device: ") + ex.getMessage();
            showMissingDataScreen();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SELECT_PARENT_FOLDER) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) onParentFolderChosen(treeUriToFile(data.getData()));
            else pendingDestination = null;
            return;
        }
        if (requestCode != REQ_SELECT_UNREAL_FOLDER && requestCode != REQ_SELECT_UNREAL_ZIP) return;

        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            lastImportMessage = t("Auswahl abgebrochen.", "Selection cancelled.");
            showMissingDataScreen();
            return;
        }

        Uri uri = data.getData();
        takePersistableReadPermission(uri);

        if (requestCode == REQ_SELECT_UNREAL_FOLDER) {
            onParentFolderChosen(treeUriToFile(uri));
        } else {
            importZipInBackground(uri);
        }
    }

    private void takePersistableReadPermission(Uri uri) {
        if (Build.VERSION.SDK_INT < 19) return;
        try {
            Api19Storage.takePersistableReadPermission(getContentResolver(), uri);
        } catch (Throwable t) {
            android.util.Log.w(UnrealDataPaths.TAG_IMPORT, "Could not persist read permission for " + uri + ": " + t);
        }
    }

    @android.annotation.TargetApi(19)
    private static final class Api19Storage {
        private Api19Storage() {}
        static void takePersistableReadPermission(android.content.ContentResolver resolver, Uri uri) {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
    }

    private void importZipInBackground(Uri uri) {
        showBusyScreen(
                t("Unreal-ZIP wird importiert", "Importing Unreal ZIP"),
                t("Die ZIP-Datei wird geprüft und in den sicheren App-Ordner entpackt.\nDas kann je nach Gerät einige Minuten dauern.",
                  "The ZIP file is being checked and extracted into the safe app folder.\nThis may take a few minutes depending on the device."));
        startZipImport(uri);
    }

    private void handleImportResult(UnrealDataPaths.ImportResult result) {
        setKeepScreenOn(false);
        if (result == null) {
            lastImportMessage = t("Import fehlgeschlagen: unbekannter Fehler.", "Import failed: unknown error.");
            showMissingDataScreen();
            return;
        }
        lastImportMessage = result.message;
        if (result.ok && result.root != null) {
            selectedRoot = result.root;
            UnrealDataPaths.ensureDirectoryLayout(selectedRoot);
            UnrealDataPaths.installDefaultConfigsIfNeeded(this, selectedRoot);
            UnrealDataPaths.normalizeConfigForDetectedData(selectedRoot);
            if (UnrealDataPaths.hasRequiredData(selectedRoot, true)) {
                android.util.Log.i(UnrealDataPaths.TAG_IMPORT, "import OK root=" + selectedRoot.getAbsolutePath());
                startGameWithLanguage(selectedRoot);
                return;
            }
            lastImportMessage = t("Import abgeschlossen, aber die Pflichtdateien wurden danach nicht vollständig gefunden.",
                    "Import finished, but the required files were still not found afterwards.");
        }
        showMissingDataScreen();
    }
}
