"""One-off edit: add Unreal 226 native members and layout registrations to Engine226.

Usage: apply_226_layouts.py <Engine226/Source> <generated layouts226.txt>
"""
import io
import re
import sys

src, generated = sys.argv[1], sys.argv[2]


def read(path):
    data = io.open(path, encoding="latin-1", newline="").read()
    return data, ("\r\n" if "\r\n" in data else "\n")


def write(path, data):
    io.open(path, "w", encoding="latin-1", newline="").write(data)


# Script members Unreal 226 has and the v400 C++ classes lack.
NEW_MEMBERS = {
    "AGameInfo": ["BITFIELD bLocalLogQuery:1 GCC_PACK(4);", "FString LocalBatcherURL;",
                  "FString LocalBatcherParams;", "FString LocalStatsURL;", "FString WorldBatcherURL;",
                  "FString WorldBatcherParams;", "FString WorldStatsURL;"],
    "AGameReplicationInfo": ["BITFIELD ShowMOTD:1 GCC_PACK(4);"],
    "APlayerReplicationInfo": ["FLOAT Spree;"],
    "AWeapon": ["BITFIELD bTossedOut:1 GCC_PACK(4);"],
    "AZoneInfo": ["FString LocationStrings[4];"],
    "AMover": ["INT ServerUpdate;"],
    "ANavigationPoint": ["BYTE PathDescription;"],
    "APlayerPawn": ["INT NetSpeed;", "INT LanSpeed;", "FLOAT KbdAccel;", "FString CantChangeNameMsg;"],
    "ASavedMove": ["BITFIELD bSent:1 GCC_PACK(4);"],
}
# Registrations appended to the existing StaticConstructors.
EXTRA_REGS = {
    "AGameInfo": ["UT99_LAYOUT_BOOL(bLocalLogQuery)", "UT99_LAYOUT_FIELD(LocalBatcherURL,StrProperty)",
                  "UT99_LAYOUT_FIELD(LocalBatcherParams,StrProperty)", "UT99_LAYOUT_FIELD(LocalStatsURL,StrProperty)",
                  "UT99_LAYOUT_FIELD(WorldBatcherURL,StrProperty)", "UT99_LAYOUT_FIELD(WorldBatcherParams,StrProperty)",
                  "UT99_LAYOUT_FIELD(WorldStatsURL,StrProperty)"],
    "AMover": ["UT99_LAYOUT_FIELD(ServerUpdate,IntProperty)"],
    "ANavigationPoint": ["UT99_LAYOUT_FIELD(PathDescription,ByteProperty)"],
    "APlayerPawn": ["UT99_LAYOUT_FIELD(NetSpeed,IntProperty)", "UT99_LAYOUT_FIELD(LanSpeed,IntProperty)",
                    "UT99_LAYOUT_FIELD(KbdAccel,FloatProperty)", "UT99_LAYOUT_FIELD(CantChangeNameMsg,StrProperty)"],
    "ASavedMove": ["UT99_LAYOUT_BOOL(bSent)"],
}
# Classes that get a complete registration from the generated file.
FULL = {"HUD": "AHUD", "GameReplicationInfo": "AGameReplicationInfo",
        "PlayerReplicationInfo": "APlayerReplicationInfo", "Weapon": "AWeapon",
        "ZoneInfo": "AZoneInfo", "Player": "UPlayer"}

gen = {}
current = None
for line in io.open(generated, encoding="latin-1"):
    line = line.rstrip()
    if line.startswith("// "):
        current = line[3:]
        gen[current] = []
    elif line.strip():
        gen[current].append(line.strip())

# 1. Members and StaticConstructor declarations in EngineClasses.h.
path = src + "/Engine/Inc/EngineClasses.h"
h, nl = read(path)
for cls in sorted(set(NEW_MEMBERS) | {v for v in FULL.values() if v.startswith("A")}):
    m = re.search(r"class ENGINE_API %s : [^\n]+\n\{.*?^\};" % cls, h, re.S | re.M)
    assert m, cls
    block = m.group()
    lines = ["    " + d for d in NEW_MEMBERS.get(cls, [])]
    if "void StaticConstructor();" not in block:
        lines.append("    void StaticConstructor();")
    marker = "    DECLARE_CLASS(%s," % cls
    assert marker in block, cls
    add = "#if defined(PLATFORM_ANDROID)" + nl + nl.join(lines) + nl + "#endif" + nl
    h = h[:m.start()] + block.replace(marker, add + marker, 1) + h[m.end():]
write(path, h)

# 2. UPlayer StaticConstructor declaration.
path = src + "/Engine/Inc/UnPlayer.h"
p, nl = read(path)
anchor = "\tDECLARE_ABSTRACT_CLASS(UPlayer"
if anchor not in p:
    anchor = p[p.index("DECLARE_ABSTRACT_CLASS(UPlayer") - 1:p.index("DECLARE_ABSTRACT_CLASS(UPlayer")] + "DECLARE_ABSTRACT_CLASS(UPlayer"
i = p.index("DECLARE_ABSTRACT_CLASS(UPlayer")
eol = p.index(nl, i) + len(nl)
p = p[:eol] + "#if defined(PLATFORM_ANDROID)" + nl + "\tvoid StaticConstructor();" + nl + "#endif" + nl + p[eol:]
write(path, p)

# 3. Registrations.
path = src + "/Engine/Src/UnModernActorLayouts.inc"
inc, nl = read(path)
for cls, regs in EXTRA_REGS.items():
    head = "void %s::StaticConstructor()" % cls
    i = inc.index(head)
    end = inc.index(nl + "}", i)
    inc = inc[:end] + nl + nl.join("    " + r for r in regs) + inc[end:]
for script, cls in FULL.items():
    body = [
        "// Unreal Gold 226 layout: members are matched by name, not position.",
        "void %s::StaticConstructor()" % cls,
        "{",
        "    typedef %s FLayoutClass;" % cls,
        "    BYTE Before[sizeof(FLayoutClass)];",
    ] + ["    " + r for r in gen[script]] + ["}"]
    inc = inc.rstrip() + nl + nl + nl.join(body) + nl
write(path, inc)

# 4. Console: TimeDemo member and registration.
path = src + "/Engine/Inc/UnCon.h"
c, nl = read(path)
i = c.index("DECLARE_CLASS(UConsole")
c = c[:i] + "#if defined(PLATFORM_ANDROID)" + nl + "\tclass UObject* TimeDemo;" + nl + "#endif" + nl + "\t" + c[i:].lstrip("\t")
write(path, c)
path = src + "/Engine/Src/UnCon.cpp"
c, nl = read(path)
i = c.index("void UConsole::StaticConstructor()")
end = c.index(nl + "}", i)
c = c[:end] + nl + "    UT99RegisterNativeFieldLayout(GetClass(),TEXT(\"TimeDemo\"),TEXT(\"ObjectProperty\"),STRUCT_OFFSET(FLayoutClass,TimeDemo),sizeof(TimeDemo),0,UT99NativeFieldCapacity(TimeDemo));" + c[end:]
write(path, c)
print("done")
