package com.ast.unreal;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Unreal Gold 226 data: detection, remembered location and installation of
 * the official disc that OldUnreal hosts. Only game data is installed; the
 * disc's Windows programs are never copied or run.
 */
final class UnrealGold {
    interface Progress { void update(String phase, int percent); }

    static final List<String> LANGUAGES = Arrays.asList("int", "frt", "det", "est", "itt");
    private static final long ISO_SIZE = 676734976L;
    private static final String ISO_MD5 = "a2dc0525242fce78c01e95b71914a3b4";
    private static final String[] MIRRORS = {
            "https://files.oldunreal.net/UNREAL_GOLD.ISO",
            "https://files2.oldunreal.net/UNREAL_GOLD.ISO",
            "https://files3.oldunreal.net/UNREAL_GOLD.ISO",
            "https://archive.org/download/totallyunreal/UNREAL_GOLD.ISO"};
    private static final String[] FOLDERS = {"System", "Maps", "Textures", "Sounds", "Music"};
    private static final String PREFS = "unreal_gold";
    private static final String KEY_ROOT = "root";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_TOUCH = "touch";
    private static final String KEY_MENU = "menu";
    private static final String KEY_TOUCH_OPACITY = "touch_opacity";

    private UnrealGold() {}

    /** True for packages saved by engine version 226 or later (file version 68+). */
    static boolean isGoldData(File root) {
        if (root == null) return false;
        File core = UnrealDataPaths.findSystemFile(new File(root, "System"), "Core.u");
        if (core == null) return false;
        try (RandomAccessFile in = new RandomAccessFile(core, "r")) {
            byte[] header = new byte[8];
            in.readFully(header);
            int tag = (header[0] & 255) | (header[1] & 255) << 8 | (header[2] & 255) << 16 | (header[3] & 255) << 24;
            int version = (header[4] & 255) | (header[5] & 255) << 8;
            return tag == 0x9E2A83C1 && version >= 68;
        } catch (IOException e) {
            return false;
        }
    }

    static boolean isSupportedGoldData(File root) {
        File system = new File(root, "System");
        return GoldDataVersion.supports(UnrealDataPaths.findSystemFile(system, "Core.u"),
                UnrealDataPaths.findSystemFile(system, "Engine.u"));
    }

    static File rememberedRoot(Context context) {
        String path = prefs(context).getString(KEY_ROOT, null);
        File root = path == null ? null : new File(path);
        return root != null && isGoldData(root) && UnrealDataPaths.hasRequiredData(root) ? root : null;
    }

    static void remember(Context context, File root, String language) {
        SharedPreferences.Editor editor = prefs(context).edit().putString(KEY_ROOT, root.getAbsolutePath());
        if (language != null) editor.putString(KEY_LANGUAGE, language);
        editor.commit();
    }

    /** Installs our small console adapter; the original UPak package stays intact. */
    static void installConsolePatch(Context context, File system) throws IOException {
        byte[] patch;
        try (InputStream in = context.getAssets().open("gold_patches/System/UnrealGoldAndroid.u")) {
            patch = readBytes(in);
        }
        File target = new File(system, "UnrealGoldAndroid.u");
        if (target.isFile() && target.length() == patch.length) {
            try (InputStream in = new FileInputStream(target)) {
                if (Arrays.equals(patch, readBytes(in))) return;
            }
        }
        write(target, patch);
    }

    private static byte[] readBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        return out.toByteArray();
    }

    /** Returns to the v200 data found by UnrealDataPaths. */
    static void forget(Context context) {
        prefs(context).edit().remove(KEY_ROOT).commit();
    }

    static String language(Context context) {
        String value = prefs(context).getString(KEY_LANGUAGE, null);
        if (value != null && LANGUAGES.contains(value)) return value;
        return "fr".equals(Locale.getDefault().getLanguage()) ? "frt" : "int";
    }

    static void setLanguage(Context context, String language) {
        prefs(context).edit().putString(KEY_LANGUAGE, language).commit();
    }

    /** Classic Unreal menus (default, as with the v200 data) instead of the UMenu windows. */
    static boolean classicMenu(Context context) {
        return !"windows".equals(prefs(context).getString(KEY_MENU, "classic"));
    }

    static void classicMenu(Context context, boolean classic) {
        prefs(context).edit().putString(KEY_MENU, classic ? "classic" : "windows").commit();
    }

    /** On-screen controller: "auto" (without a physical controller), "on" or "off". */
    static String touch(Context context) {
        return prefs(context).getString(KEY_TOUCH, "auto");
    }

    static void touch(Context context, String value) {
        prefs(context).edit().putString(KEY_TOUCH, value).commit();
    }

    /** Whole on-screen controller opacity in percent (20..100). */
    static int touchOpacity(Context context) {
        return Math.max(20, Math.min(100, prefs(context).getInt(KEY_TOUCH_OPACITY, 70)));
    }

    static void touchOpacity(Context context, int percent) {
        prefs(context).edit().putInt(KEY_TOUCH_OPACITY, percent).commit();
    }

    static float interfaceSize(Context context) {
        return Math.max(.5f, Math.min(1.25f, prefs(context).getFloat("interface_size", 1f)));
    }

    static void interfaceSize(Context context, float value) {
        prefs(context).edit().putFloat("interface_size", value).commit();
    }

    /** 0 off, 1 light, 2 medium, 3 strong. Shared by both engines. */
    static int aimAssist(Context context) {
        return Math.max(0, Math.min(3, prefs(context).getInt("aim_assist", 2)));
    }

    static void aimAssist(Context context, int level) {
        prefs(context).edit().putInt("aim_assist", Math.max(0, Math.min(3, level))).commit();
    }

    static float cameraRoll(Context context) {
        return Math.max(0f, Math.min(1f, prefs(context).getFloat("camera_roll", .25f)));
    }

    static void cameraRoll(Context context, float value) {
        prefs(context).edit().putFloat("camera_roll", value).commit();
    }

    static float gamma(Context context) {
        return Math.max(.75f, Math.min(3f, prefs(context).getFloat("gamma", 1.6f)));
    }

    static void gamma(Context context, float value) {
        prefs(context).edit().putFloat("gamma", value).commit();
    }

    private static SharedPreferences prefs(Context context) {
        // The launcher and the :unreal226 game process both write these.
        return context.getSharedPreferences(PREFS, Context.MODE_MULTI_PROCESS);
    }

    /** Downloads the Unreal Gold disc and installs its data into an empty or new folder. */
    static void install(File cache, File destination, String language, Progress progress) throws IOException {
        if (!LANGUAGES.contains(language)) throw new IOException("Unsupported language");
        boolean inPlace = destination.isDirectory();
        String[] existing = inPlace ? destination.list() : null;
        if ((destination.exists() && !inPlace) || (inPlace && (existing == null || existing.length > 0)))
            throw new IOException("Choose an empty or new folder; existing files are preserved");
        mkdir(cache);
        File iso = new File(cache, "UNREAL_GOLD.ISO");
        if (!iso.isFile() && cache.getUsableSpace() > 0 && cache.getUsableSpace() < ISO_SIZE)
            throw new IOException("Not enough internal space for the game download");
        if (!valid(iso)) {
            IOException last = null;
            for (String mirror : MIRRORS) {
                try { download(mirror, iso, progress); last = null; break; }
                catch (IOException error) {
                    if (Thread.currentThread().isInterrupted()) throw error;
                    progress.update("Unreal Gold", 0);
                    last = error;
                }
            }
            if (last != null) throw last;
        }
        // Stage beside a new folder, or inside the chosen empty folder (its
        // parent, such as a microSD root, may not be writable).
        File parent = inPlace ? destination.getCanonicalFile() : destination.getCanonicalFile().getParentFile();
        mkdir(parent);
        if (parent.getUsableSpace() > 0 && parent.getUsableSpace() < 700L * 1024 * 1024)
            throw new IOException("At least 700 MB free is required at the destination");
        File stage = new File(parent, ".unreal-install-" + UUID.randomUUID());
        mkdir(stage);
        try {
            extractDisc(iso, stage, language, progress);
            if (!isGoldData(stage) || !UnrealDataPaths.hasRequiredData(stage))
                throw new IOException("Installed game data failed validation");
            write(new File(stage, ".unreal-install-source"),
                    ("OldUnreal Unreal Gold\nLanguage=" + language + "\n").getBytes("UTF-8"));
            if (inPlace) {
                File[] staged = stage.listFiles();
                if (staged == null) throw new IOException("Cannot finalize installation");
                for (File file : staged)
                    if (!file.renameTo(new File(destination, file.getName()))) throw new IOException("Cannot finalize installation");
                if (!stage.delete()) throw new IOException("Cannot remove temporary installation folder");
            } else if (destination.exists() || !stage.renameTo(destination)) {
                throw new IOException("Cannot finalize installation");
            }
        } finally {
            if (stage.exists()) removeOwnedStage(stage);
        }
        iso.delete();
        progress.update("OK", 100);
    }

    static void extractDisc(File image, File target, String language, Progress progress) throws IOException {
        try (GoldDiscImage disc = new GoldDiscImage(image)) {
            int done = 0;
            for (GoldDiscImage.Entry entry : disc.entries()) {
                checkInterrupted();
                String path = selectedPath(entry.path, language);
                if (path != null) {
                    File output = child(target, path);
                    mkdir(output.getParentFile());
                    try (OutputStream out = new FileOutputStream(output)) { disc.copy(entry, out); }
                }
                progress.update("Unreal Gold · " + entry.path, ++done * 100 / disc.entries().size());
            }
        }
    }

    /** Maps a disc path to its installed path, or null for files that are not game data. */
    static String selectedPath(String discPath, String language) {
        String[] parts = discPath.split("/", -1);
        if (parts.length < 2 || parts.length > 3) return null;
        String folder = null;
        for (String canonical : FOLDERS)
            if (canonical.equalsIgnoreCase(parts[0])) folder = canonical;
        if (folder == null) return null;
        String name = parts[parts.length - 1];
        String lower = name.toLowerCase(Locale.ROOT);
        if (parts.length == 3) {
            String sub = parts[1];
            // Maps/UPak (Return to Na Pali) and the per-language voices in Sounds/<lang>.
            boolean keep = (folder.equals("Maps") && sub.equalsIgnoreCase("UPak"))
                    || (folder.equals("Sounds") && (sub.equalsIgnoreCase("int") || sub.equalsIgnoreCase(language)));
            if (!keep) return null;
            return folder + "/" + (folder.equals("Sounds") ? sub.toLowerCase(Locale.ROOT) : "UPak") + "/" + name;
        }
        boolean allowed;
        if (folder.equals("System")) {
            int dot = lower.lastIndexOf('.');
            String ext = dot < 0 ? "" : lower.substring(dot + 1);
            allowed = ext.equals("u") || ext.equals("ini") || ext.equals("int")
                    || (LANGUAGES.contains(ext) && ext.equals(language));
        } else {
            allowed = lower.matches(".*\\.(unr|utx|uax|umx)");
        }
        return allowed ? folder + "/" + name : null;
    }

    private static boolean valid(File file) throws IOException {
        if (!file.isFile() || file.length() != ISO_SIZE) return false;
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            try (InputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) != -1) { checkInterrupted(); digest.update(buffer, 0, n); }
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
            return ISO_MD5.equalsIgnoreCase(hex.toString());
        } catch (NoSuchAlgorithmException error) {
            throw new IOException(error);
        }
    }

    private static void download(String url, File target, Progress progress) throws IOException {
        progress.update("Unreal Gold", 0);
        File partial = new File(target.getPath() + ".part");
        URL parsed = new URL(url);
        if (!"https".equals(parsed.getProtocol())) throw new IOException("HTTPS required");
        HttpURLConnection connection = (HttpURLConnection) parsed.openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "Unreal-Android-data-installer");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
            try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(partial)) {
                byte[] buffer = new byte[65536];
                long done = 0;
                int n, last = -1;
                while ((n = in.read(buffer)) != -1) {
                    checkInterrupted();
                    done += n;
                    if (done > ISO_SIZE) throw new IOException("Download exceeds expected size");
                    out.write(buffer, 0, n);
                    int percent = (int) (done * 100 / ISO_SIZE);
                    if (percent != last) { progress.update("Unreal Gold", percent); last = percent; }
                }
            }
            if (!valid(partial)) throw new IOException("Download integrity check failed");
            if (target.exists() && !target.delete()) throw new IOException("Cannot replace cached download");
            if (!partial.renameTo(target)) throw new IOException("Cannot finalize download");
        } finally {
            connection.disconnect();
            if (partial.exists()) partial.delete();
        }
    }

    private static File child(File root, String path) throws IOException {
        File child = new File(root, path).getCanonicalFile();
        if (!child.getPath().startsWith(root.getCanonicalPath() + File.separator)) throw new IOException("Unsafe disc path");
        return child;
    }

    private static void mkdir(File dir) throws IOException {
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) throw new IOException("Cannot create folder: " + dir);
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Installation cancelled");
    }

    private static void write(File file, byte[] data) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) { out.write(data); }
    }

    private static void removeOwnedStage(File file) throws IOException {
        // Receives only the newly created staging directory, never a user folder.
        File[] children = file.listFiles();
        if (children != null) for (File c : children) removeOwnedStage(c);
        if (!file.delete()) throw new IOException("Cannot remove temporary installation file");
    }
}
