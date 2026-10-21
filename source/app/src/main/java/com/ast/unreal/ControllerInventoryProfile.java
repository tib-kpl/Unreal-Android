package com.ast.unreal;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Swap weapon/item cycling once, retaining the player's physical button choices. */
final class ControllerInventoryProfile {
    private static final Pattern ACTION = Pattern.compile("(?i)\\b(PrevWeapon|NextWeapon|InventoryPrevious|InventoryNext)\\b");
    private ControllerInventoryProfile() {}

    static String migrate(String text, String fallback) {
        if ("1".equals(GoldControllerProfile.section(text, "Android").get("inventorycontrollerprofile"))) return text;
        Map<String, String> input = new LinkedHashMap<>(GoldControllerProfile.section(fallback, "Engine.Input"));
        input.putAll(GoldControllerProfile.section(text, "Engine.Input"));
        for (Map.Entry<String, String> entry : input.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("joy") && !key.startsWith("unknown")) continue;
            String swapped = swapAction(entry.getValue());
            if (!swapped.equals(entry.getValue()))
                text = Unreal226Config.upsert(text, "Engine.Input", key, swapped);
        }
        return Unreal226Config.upsert(text, "Android", "InventoryControllerProfile", "1");
    }

    static String swapAction(String command) {
        Matcher matcher = ACTION.matcher(command);
        StringBuffer swapped = new StringBuffer();
        while (matcher.find()) {
            String old = matcher.group(1).toLowerCase(Locale.ROOT);
            String action = old.equals("prevweapon") ? "InventoryPrevious"
                    : old.equals("nextweapon") ? "InventoryNext"
                    : old.equals("inventoryprevious") ? "PrevWeapon" : "NextWeapon";
            matcher.appendReplacement(swapped, action);
        }
        matcher.appendTail(swapped);
        return swapped.toString();
    }
}
