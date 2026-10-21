package com.ast.unreal;

import java.util.Map;

/** Translate Gold's Windows Galaxy settings to its Android mixer once. */
final class GoldAudioProfile {
    static String migrate(String ini) {
        Map<String, String> android = GoldControllerProfile.section(ini, "Android");
        if ("1".equals(android.get("audioprofile"))) return ini;
        Map<String, String> pc = GoldControllerProfile.section(ini, "Galaxy.GalaxyAudioSubsystem");
        Map<String, String> current = GoldControllerProfile.section(ini, "Audio.GenericAudioSubsystem");
        boolean silentDefaults = "0".equals(current.get("channels"))
                && "0".equals(current.get("musicvolume")) && "0".equals(current.get("soundvolume"));
        String[][] defaults = {
                {"UseFilter", "True"}, {"UseSurround", "False"}, {"UseStereo", "True"},
                {"UseCDMusic", "False"}, {"UseDigitalMusic", "True"}, {"ReverseStereo", "False"},
                {"Latency", "40"}, {"OutputRate", "44100Hz"}, {"Channels", "16"},
                {"MusicVolume", "160"}, {"SoundVolume", "200"},
                {"AmbientFactor", "0.700000"}, {"DopplerSpeed", "6500.000000"}
        };
        for (String[] setting : defaults) {
            String key = setting[0], lower = key.toLowerCase(java.util.Locale.ROOT);
            String value = current.get(lower);
            if (value == null || silentDefaults) {
                value = pc.get(lower);
                if (value == null && lower.equals("channels")) value = pc.get("effectschannels");
                if (value == null) value = setting[1];
                ini = Unreal226Config.upsert(ini, "Audio.GenericAudioSubsystem", key, value);
            }
        }
        return Unreal226Config.upsert(ini, "Android", "AudioProfile", "1");
    }
}
