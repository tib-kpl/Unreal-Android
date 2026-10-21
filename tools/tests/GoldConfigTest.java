package com.ast.unreal;

import java.nio.file.Files;
import java.nio.file.Path;

public class GoldConfigTest {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static String value(String text, String key) {
        for (String line : text.split("\\r?\\n")) {
            int equal = line.indexOf('=');
            if (equal > 0 && line.substring(0, equal).trim().equalsIgnoreCase(key))
                return line.substring(equal + 1).trim();
        }
        return null;
    }
    public static void main(String[] args) throws Exception {
        String profile = Files.readString(Path.of(args[0]));
        String stock = Files.readString(Path.of(args[1]), java.nio.charset.StandardCharsets.ISO_8859_1);
        String migrated = GoldControllerProfile.migrate(stock, stock, profile);
        int verified = 0;
        for (String line : profile.split("\\r?\\n")) {
            if (!line.startsWith("Joy") && !line.startsWith("Unknown")) continue;
            String[] pair = line.split("=", 2);
            check(pair[1].equals(value(migrated, pair[0])), "Wrong v200 default: " + line);
            verified++;
        }
        check(verified >= 27, "Incomplete profile coverage");
        check(migrated.equals(GoldControllerProfile.migrate(migrated, stock, profile)), "Migration must be idempotent");
        String custom = Unreal226Config.upsert(stock, "Engine.Input", "Joy1", "AltFire | OnRelease Jump");
        custom = Unreal226Config.upsert(custom, "Engine.Input", "MouseSensitivity", "2.250000");
        custom = Unreal226Config.upsert(custom, "DefaultPlayer", "Name", "CustomPlayer");
        custom = GoldControllerProfile.migrate(custom, stock, profile);
        check("AltFire | OnRelease Jump".equals(value(custom, "Joy1")), "Custom button was overwritten");
        check("2.250000".equals(value(custom, "MouseSensitivity")), "Sensitivity was overwritten");
        check("CustomPlayer".equals(value(custom, "Name")), "Player settings were overwritten");
        String rebound = Unreal226Config.upsert(migrated, "Engine.Input", "Joy13", "Jump");
        check(rebound.equals(GoldControllerProfile.migrate(rebound, stock, profile)), "Later remapping was overwritten");
        String lower = GoldControllerProfile.migrate(stock.replace("[Engine.Input]", "[engine.input]"), stock, profile);
        check("Jump".equals(value(lower, "Joy1")), "Case-insensitive section was duplicated");
        check(!lower.contains("[Engine.Input]"), "Duplicate input section");
        String empty = GoldControllerProfile.migrate("", stock, profile);
        check("Fire".equals(value(empty, "Joy13")), "New configuration lacks fire");
        String paths = "[Core.System]\r\nPaths=..\\Maps\\*.unr\r\nSavePath=..\\Save\r\nName=Keep\\This\r\n";
        paths = Unreal226Config.fixPathSeparators(paths);
        check(paths.contains("Paths=../Maps/*.unr") && paths.contains("SavePath=../Save"), "Windows paths were not normalized");
        check(paths.contains("Name=Keep\\This"), "Unrelated value was changed");
        System.out.println("PASS: " + verified + " v200 bindings, custom bindings, sensitivity, repeat launches and path migration");
    }
}
