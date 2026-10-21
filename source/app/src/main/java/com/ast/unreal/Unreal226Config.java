package com.ast.unreal;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Android configuration for Unreal Gold 226 data, derived from the game's Unreal.ini. */
final class Unreal226Config {
    private static final String TAG = "Unreal226";

    private Unreal226Config() {}

    /** Creates System/Android226.ini on first launch; later launches keep user changes. */
    static void ensureAndroidIni(File system, String language) {
        File ini = new File(system, Unreal226Activity.INI_NAME);
        try {
            String text;
            if (ini.isFile()) {
                text = read(ini);
            } else {
                File gold = new File(system, "Unreal.ini");
                text = gold.isFile() ? read(gold) : "";
            }
            // Windows path separators in [Core.System] (Paths=..\Maps\*.unr).
            text = fixPathSeparators(text);
            // Drivers are always forced: the Windows ones in Unreal.ini cannot load.
            text = upsert(text, "Engine.Engine", "GameRenderDevice", "NOpenGLESDrv.NOpenGLESRenderDevice");
            text = upsert(text, "Engine.Engine", "WindowedRenderDevice", "NOpenGLESDrv.NOpenGLESRenderDevice");
            text = upsert(text, "Engine.Engine", "RenderDevice", "NOpenGLESDrv.NOpenGLESRenderDevice");
            text = upsert(text, "Engine.Engine", "AudioDevice", "Audio.GenericAudioSubsystem");
            text = upsert(text, "Engine.Engine", "ViewportManager", "NSDLDrv.NSDLClient");
            text = upsert(text, "Engine.Engine", "NetworkDevice", "IpDrv.TcpNetDriver");
            text = upsert(text, "Engine.Engine", "Language", language);
            text = soundLanguagePath(text, language);
            if (!ini.isFile()) {
                text = upsert(text, "NSDLDrv.NSDLClient", "StartupFullscreen", "True");
                text = upsert(text, "NSDLDrv.NSDLClient", "UseJoystick", "True");
                text = upsert(text, "NSDLDrv.NSDLClient", "Brightness", "0.600000");
                text = upsert(text, "Engine.GameEngine", "CacheSizeMegs", "4");
            }
            write(ini, text);
        } catch (IOException e) {
            Log.e(TAG, "Cannot prepare " + ini, e);
        }
    }

    static String fixPathSeparators(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (String line : text.split("(?<=\n)", -1)) {
            String t = line.trim();
            if (t.regionMatches(true, 0, "Paths=", 0, 6) || t.regionMatches(true, 0, "SavePath=", 0, 9)
                    || t.regionMatches(true, 0, "CachePath=", 0, 10)) {
                line = line.replace('\\', '/');
            }
            out.append(line);
        }
        return out.toString();
    }

    /**
     * Puts Paths=../Sounds/<language>/*.uax before the int voices, which share
     * package names, and removes the paths of the other languages.
     */
    static String soundLanguagePath(String text, String language) {
        StringBuilder out = new StringBuilder(text.length() + 40);
        for (String line : text.split("(?<=\n)", -1)) {
            String t = line.trim().toLowerCase(java.util.Locale.ROOT);
            if (t.startsWith("paths=../sounds/") && t.endsWith("/*.uax") && !t.equals("paths=../sounds/*.uax")) {
                String folder = t.substring("paths=../sounds/".length(), t.length() - "/*.uax".length());
                if (!folder.equals("int")) continue;
                if (!language.equals("int")) {
                    String nl = line.endsWith("\r\n") ? "\r\n" : "\n";
                    out.append("Paths=../Sounds/").append(language).append("/*.uax").append(nl);
                }
            }
            out.append(line);
        }
        return out.toString();
    }

    /** Sets key=value in [section], replacing the first existing entry. */
    static String upsert(String text, String section, String key, String value) {
        String nl = text.contains("\r\n") ? "\r\n" : "\n";
        String header = "[" + section + "]";
        int start = indexOfLine(text, header);
        if (start < 0) {
            String sep = text.isEmpty() || text.endsWith(nl) ? "" : nl;
            return text + sep + nl + header + nl + key + "=" + value + nl;
        }
        int bodyStart = text.indexOf('\n', start);
        bodyStart = bodyStart < 0 ? text.length() : bodyStart + 1;
        int end = text.indexOf("\n[", bodyStart - 1);
        end = end < 0 ? text.length() : end + 1;
        int pos = bodyStart;
        while (pos < end) {
            int eol = text.indexOf('\n', pos);
            eol = eol < 0 || eol > end ? end : eol + 1;
            String line = text.substring(pos, eol).trim();
            int eq = line.indexOf('=');
            if (eq > 0 && line.substring(0, eq).trim().equalsIgnoreCase(key)) {
                return text.substring(0, pos) + key + "=" + value + nl + text.substring(eol);
            }
            pos = eol;
        }
        return text.substring(0, bodyStart) + key + "=" + value + nl + text.substring(bodyStart);
    }

    private static int indexOfLine(String text, String line) {
        int from = 0;
        while (true) {
            int i = text.indexOf(line, from);
            if (i < 0) return -1;
            boolean lineStart = i == 0 || text.charAt(i - 1) == '\n';
            if (lineStart) return i;
            from = i + 1;
        }
    }

    private static String read(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("ISO-8859-1");
        }
    }

    private static void write(File f, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes("ISO-8859-1"));
        }
    }
}
