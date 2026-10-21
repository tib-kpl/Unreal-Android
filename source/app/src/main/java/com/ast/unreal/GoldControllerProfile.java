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

    /** Import the actual v200 layout, preserving buttons already customized in Gold. */
    static String importPersonal(String gold, String v200, String shipped, String pc) {
        if ("1".equals(section(gold, "Android").get("personalcontrollerprofile"))) return gold;
        Map<String, String> current = section(gold, "Engine.Input");
        Map<String, String> defaults = section(shipped, "Engine.Input");
        Map<String, String> original = section(pc, "Engine.Input");
        Map<String, String> personal = section(v200, "Engine.Input");
        if (personal.isEmpty()) return gold;
        boolean goldSwapped = "1".equals(section(gold, "Android").get("inventorycontrollerprofile"));
        boolean v200Swapped = "1".equals(section(v200, "Android").get("inventorycontrollerprofile"));
        if (goldSwapped) {
            for (Map.Entry<String, String> entry : defaults.entrySet())
                if (entry.getKey().startsWith("joy") || entry.getKey().startsWith("unknown"))
                    entry.setValue(ControllerInventoryProfile.swapAction(entry.getValue()));
            for (Map.Entry<String, String> entry : original.entrySet())
                if (entry.getKey().startsWith("joy") || entry.getKey().startsWith("unknown"))
                    entry.setValue(ControllerInventoryProfile.swapAction(entry.getValue()));
        }
        for (Map.Entry<String, String> entry : personal.entrySet()) {
            String key = entry.getKey(), command = entry.getValue();
            // v200 uses WASD/mouse when a physical stick/trigger binding is
            // empty. Import that effective action rather than disabling it.
            if (command.isEmpty()) {
                String fallback = null;
                if (key.equals("unknownd8")) fallback = "a";
                else if (key.equals("unknownd9")) fallback = "d";
                else if (key.equals("unknownda")) fallback = "w";
                else if (key.equals("unknowndf")) fallback = "s";
                else if (key.equals("joy12")) fallback = "rightmouse";
                else if (key.equals("joy13")) fallback = "leftmouse";
                if (fallback != null) command = personal.containsKey(fallback) ? personal.get(fallback) : binding(defaults, key);
            }
            String lower = command.toLowerCase(Locale.ROOT);
            boolean controller = key.startsWith("joy") || key.startsWith("unknown");
            boolean requestedAction = lower.contains("duck") || lower.contains("activatehint") || lower.contains("activatetranslator");
            if (key.startsWith("aliases[") || (!controller && !requestedAction)) continue;
            if (controller && goldSwapped != v200Swapped) command = ControllerInventoryProfile.swapAction(command);
            String before = current.get(key);
            if (before == null || before.equalsIgnoreCase(binding(defaults, key))
                    || before.equalsIgnoreCase(binding(original, key)))
                gold = Unreal226Config.upsert(gold, "Engine.Input", key, command);
        }
        return Unreal226Config.upsert(gold, "Android", "PersonalControllerProfile", "1");
    }

    private static String binding(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null ? "" : value;
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
