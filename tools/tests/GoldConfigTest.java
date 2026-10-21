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
        String personal = "[Engine.Input]\nJoy2=Jump\nJoy9=ActivateHint\nF3=ActivateHint | FunctionKey 3\n";
        String imported = GoldControllerProfile.importPersonal(migrated, personal, profile, stock);
        check("Jump".equals(value(imported, "Joy2")), "Personal v200 crouch/button layout was not copied");
        check("ActivateHint".equals(value(imported, "Joy9")), "Personal objective button was not copied");
        check(imported.equals(GoldControllerProfile.importPersonal(imported, personal, profile, stock)), "Personal migration repeats");
        String fallbacks = "[Engine.Input]\nUnknownDA=\nUnknownDF=\nJoy13=\nW=MoveForward\nS=MoveBackward\nLeftMouse=AltFire\n";
        String fallbackProfile = GoldControllerProfile.importPersonal(migrated, fallbacks, profile, stock);
        check("MoveForward".equals(value(fallbackProfile, "UnknownDA")), "v200 fallback forward was lost");
        check("MoveBackward".equals(value(fallbackProfile, "UnknownDF")), "v200 fallback backward was lost");
        check("AltFire".equals(value(fallbackProfile, "Joy13")), "v200 trigger fallback was lost");
        String goldCustom = Unreal226Config.upsert(migrated, "Engine.Input", "Joy9", "InventoryActivate");
        goldCustom = GoldControllerProfile.importPersonal(goldCustom, personal, profile, stock);
        check("InventoryActivate".equals(value(goldCustom, "Joy9")), "Gold custom binding was overwritten by v200");
        for (String old : new String[] { "", "Joy9=", "Joy9=CenterView", "joy9=centerview" }) {
            String objective = ControllerObjectiveProfile.migrate("[Engine.Input]\nJoy2=Duck\nJoy5=ActivateTranslator\n" + old + "\n");
            check("ActivateHint".equals(value(objective, "Joy9")), "Missing R3 objective upgrade: " + old);
            check("Duck".equals(value(objective, "Joy2")), "Objective upgrade changed crouch");
            check("ActivateTranslator".equals(value(objective, "Joy5")), "Objective upgrade changed translator");
            check(objective.equals(ControllerObjectiveProfile.migrate(objective)), "Objective upgrade repeats");
            String cleared = Unreal226Config.upsert(objective, "Engine.Input", "Joy9", "");
            check(cleared.equals(ControllerObjectiveProfile.migrate(cleared)), "Later R3 removal was overwritten");
        }
        String customObjective = "[Engine.Input]\nJoy9=InventoryActivate\n";
        check("InventoryActivate".equals(value(ControllerObjectiveProfile.migrate(customObjective), "Joy9")), "Custom R3 action overwritten");
        String oldGold = Unreal226Config.upsert(migrated, "Engine.Input", "Joy9", "CenterView");
        oldGold = Unreal226Config.upsert(oldGold, "Android", "PersonalControllerProfile", "1");
        check("ActivateHint".equals(value(ControllerObjectiveProfile.migrate(oldGold), "Joy9")), "Existing Gold profile did not get objective");
        String personalBlank = GoldControllerProfile.importPersonal(migrated, "[Engine.Input]\nJoy9=\n", profile, stock);
        check("ActivateHint".equals(value(ControllerObjectiveProfile.migrate(personalBlank), "Joy9")), "Blank v200 import disabled objective");
        String cycling = "[Engine.Input]\nJoy10=InventoryPrevious\nJoy11=InventoryNext\nJoyPovLeft=PrevWeapon\nJoyPovRight=NextWeapon\nJoy1=Jump\nJoy2=Duck\nJoy9=ActivateHint\nF5=NextWeapon\nJoy3=GetWeapon UnrealShare.ASMD\n";
        String swapped = ControllerInventoryProfile.migrate(cycling, profile);
        check("PrevWeapon".equals(value(swapped,"Joy10")), "Personal shoulder previous action not swapped");
        check("NextWeapon".equals(value(swapped,"Joy11")), "Personal shoulder next action not swapped");
        check("InventoryPrevious".equals(value(swapped,"JoyPovLeft")), "D-pad previous action not swapped");
        check("InventoryNext".equals(value(swapped,"JoyPovRight")), "D-pad next action not swapped");
        check("Jump".equals(value(swapped,"Joy1")) && "Duck".equals(value(swapped,"Joy2")) && "ActivateHint".equals(value(swapped,"Joy9")), "Unrelated actions changed");
        check("NextWeapon".equals(value(swapped,"F5")), "Keyboard cycling was changed");
        check("GetWeapon UnrealShare.ASMD".equals(value(swapped,"Joy3")), "Specific weapon selection was changed");
        check(swapped.equals(ControllerInventoryProfile.migrate(swapped,profile)), "Weapon/item swap repeats");
        check("InventoryPrevious | OnRelease NextWeapon".equals(ControllerInventoryProfile.swapAction("PrevWeapon | OnRelease InventoryNext")), "Compound action swap failed");
        for (boolean goldNew : new boolean[]{false,true}) {
            for (boolean v200New : new boolean[]{false,true}) {
                String source = v200New ? swapped : cycling;
                String target = goldNew ? ControllerInventoryProfile.migrate(migrated,profile) : migrated;
                target = GoldControllerProfile.importPersonal(target,source,profile,stock);
                target = ControllerInventoryProfile.migrate(target,profile);
                check("PrevWeapon".equals(value(target,"Joy10")), "Import double-swapped shoulder: " + goldNew + "/" + v200New);
                check("InventoryNext".equals(value(target,"JoyPovRight")), "Import double-swapped D-pad");
            }
        }
        check("0.5".equals(value(Unreal226Config.displaySettings("",.5f,1.6f),"UIScaleMultiplier")), "Smaller size is clamped away");
        String paths = "[Core.System]\r\nPaths=..\\Maps\\*.unr\r\nSavePath=..\\Save\r\nName=Keep\\This\r\n";
        paths = Unreal226Config.fixPathSeparators(paths);
        check(paths.contains("Paths=../Maps/*.unr") && paths.contains("SavePath=../Save"), "Windows paths were not normalized");
        check(paths.contains("Name=Keep\\This"), "Unrelated value was changed");
        String display = Unreal226Config.displaySettings("[Android]\nUIScale=2.000000\nClassicMenu=True\n", 1f, 1.6f);
        check("0".equals(value(display, "UIScale")), "Old fixed scale was not migrated to automatic");
        check("1.6".equals(value(display, "Gamma")), "Gamma default missing");
        check(display.equals(Unreal226Config.displaySettings(display, 1f, 1.6f)), "Display migration repeats");
        String customScale = Unreal226Config.displaySettings("[android]\nUIScale=3.5\n", 1.25f, 2.2f);
        check("3.5".equals(value(customScale, "UIScale")), "Custom scale was overwritten");
        check("1.25".equals(value(customScale, "UIScaleMultiplier")), "Size selection lost");
        String pcAudio="[Galaxy.GalaxyAudioSubsystem]\nMusicVolume=160\nSoundVolume=200\nOutputRate=22050Hz\nEffectsChannels=16\nUseStereo=True\nUseDigitalMusic=True\n";
        String audio=GoldAudioProfile.migrate(pcAudio);
        check("16".equals(value(audio,"Channels")), "No effect voices");
        check(audio.contains("[Audio.GenericAudioSubsystem]"), "Missing Android audio section");
        check(audio.equals(GoldAudioProfile.migrate(audio)), "Audio migration repeats");
        String silent=pcAudio+"[Audio.GenericAudioSubsystem]\nChannels=0\nMusicVolume=0\nSoundVolume=0\nUseStereo=False\nUseDigitalMusic=False\nOutputRate=8000Hz\n";
        String repaired=GoldAudioProfile.migrate(silent);
        check(repaired.contains("Channels=16") && !repaired.contains("Channels=0"), "Silent native defaults not repaired");
        String muted=pcAudio+"[Audio.GenericAudioSubsystem]\nChannels=16\nMusicVolume=0\nSoundVolume=0\n";
        check(GoldAudioProfile.migrate(muted).contains("MusicVolume=0"), "Intentional mute overwritten");
        check(GoldAudioProfile.migrate(muted).contains("SoundVolume=0"), "Intentional effects mute overwritten");
        System.out.println("PASS: " + verified + " defaults, personal v200 bindings, Gold customization preservation, shared R3 objective upgrade, weapon/item swap and imports, display/gamma, paths and audio migration");
    }
}
