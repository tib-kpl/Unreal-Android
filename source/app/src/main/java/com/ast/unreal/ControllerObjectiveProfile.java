package com.ast.unreal;

import java.util.Map;

/** Shared v200/Gold upgrade for the physical controller objective shortcut. */
final class ControllerObjectiveProfile {
    private ControllerObjectiveProfile() {}

    static String migrate(String text) {
        if ("1".equals(GoldControllerProfile.section(text, "Android").get("objectivecontrollerprofile"))) return text;
        Map<String, String> input = GoldControllerProfile.section(text, "Engine.Input");
        String rightStick = input.get("joy9");
        // R3 was either unassigned or the old shipped CenterView default.
        // Preserve other personal actions; subsequent remapping stays authoritative.
        if (rightStick == null || rightStick.isEmpty() || rightStick.equalsIgnoreCase("CenterView"))
            text = Unreal226Config.upsert(text, "Engine.Input", "Joy9", "ActivateHint");
        return Unreal226Config.upsert(text, "Android", "ObjectiveControllerProfile", "1");
    }
}
