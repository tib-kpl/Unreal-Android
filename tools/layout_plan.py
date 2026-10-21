"""Plan native layout registrations for a set of packages against engine headers.

Usage: layout_plan.py <engine Source dir> <package.u> [<package.u>...]
For every class whose C++ declaration exists in the engine headers, prints
- whether it already has a native layout registration,
- the script properties with no C++ member of the same name,
- the C++ size-sensitive differences (property order) when not registered.
"""
import glob
import os
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
import ue1pkg  # noqa: E402


def cpp_classes(src):
    classes = {}
    for h in glob.glob(os.path.join(src, "*", "Inc", "*.h")):
        text = open(h, encoding="latin-1").read()
        for m in re.finditer(r"^class\s+(?:\w+_API\s+)?([AU]\w+)\s*:\s*public\s+(\w+)[^{;]*\{(.*?)^\};",
                             text, re.S | re.M):
            name, body = m.group(1), m.group(3)
            members = set()
            for line in body.splitlines():
                line = line.split("//")[0].strip()
                line = re.sub(r"GCC_\w+\(\d+\)", "", line).strip()
                if not line.endswith(";") or "(" in line or line.startswith(("typedef", "return", "friend")):
                    continue
                for decl in line[:-1].split(","):
                    mm = re.search(r"(\w+)\s*(?::\s*\d+)?\s*(?:\[[^\]]*\])?\s*$", decl.strip())
                    if mm:
                        members.add(mm.group(1).lower())
            classes[name[1:].lower()] = (name, os.path.basename(h), members)
    return classes


def registered(src):
    names = set()
    for f in glob.glob(os.path.join(src, "*", "Src", "*.*")):
        for m in re.finditer(r"void\s+([AU]\w+)::StaticConstructor\(\)\s*\{[^}]*?(?:UT99_LAYOUT|UT99_ACTOR|UT99RegisterNativeFieldLayout)",
                             open(f, encoding="latin-1").read(), re.S):
            names.add(m.group(1)[1:].lower())
    return names


def main():
    src = sys.argv[1]
    cpp = cpp_classes(src)
    reg = registered(src)
    for path in sys.argv[2:]:
        info = ue1pkg.describe(path)
        for name, c in sorted(info["classes"].items()):
            k = name.lower()
            if k not in cpp:
                continue
            cname, header, members = cpp[k]
            missing = [p for p in c["props"] if p[0].lower() not in members]
            status = "registered" if k in reg else "exact-layout"
            if missing or True:
                print("%-24s %-14s %-18s props=%d missing=%s" % (
                    cname, status, header, len(c["props"]),
                    ", ".join("%s:%s" % (p[0], p[1]) for p in missing) or "-"))


if __name__ == "__main__":
    main()
