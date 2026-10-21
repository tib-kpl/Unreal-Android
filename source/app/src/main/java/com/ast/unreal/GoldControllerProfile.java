package com.ast.unreal;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** One-time migration from the PC Gold joystick layout to the shipped v200 layout. */
final class GoldControllerProfile {
    private GoldControllerProfile() {}

    static String migrate(String user, String stock, String v200) {
        if ("200".equals(section(user, "Android").get("controllerprofile"))) return user;
        Map<String, String> current = section(user, "Engine.Input");
        Map<String, String> original = section(stock, "Engine.Input");
        for (Map.Entry<String, String> entry : section(v200, "Engine.Input").entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("joy") && !key.startsWith("unknown")) continue;
            String before = current.get(key);
            String pc = original.get(key);
            if (before == null || (pc != null && before.equalsIgnoreCase(pc))) {
                user = Unreal226Config.upsert(user, "Engine.Input", key, entry.getValue());
            }
        }
        return Unreal226Config.upsert(user, "Android", "ControllerProfile", "200");
    }

    static Map<String, String> section(String text, String section) {
        Map<String, String> values = new LinkedHashMap<>();
        boolean active = false;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.startsWith("[")) active = line.equalsIgnoreCase("[" + section + "]");
            else if (active && !line.startsWith(";") && !line.startsWith("#")) {
                int equal = line.indexOf('=');
                if (equal > 0) values.put(line.substring(0, equal).trim().toLowerCase(Locale.ROOT),
                        line.substring(equal + 1).trim());
            }
        }
        return values;
    }
}
