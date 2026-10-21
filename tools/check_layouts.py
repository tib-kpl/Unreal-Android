"""Check native layout registrations against the properties of packages.

Usage: check_layouts.py <engine Source dir> <package.u> [<package.u>...]
For every class with a StaticConstructor layout registration, prints script
properties that have no registration, or whose registered type differs.
"""
import glob
import os
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
import ue1pkg  # noqa: E402

REG = re.compile(r"(?:UT99_LAYOUT_FIELD|UT99_ACTOR_FIELD)\((\w+),(\w+)\)|"
                 r"(?:UT99_LAYOUT_BOOL|UT99_ACTOR_BOOL)\((\w+)\)|"
                 r"UT99_ACTOR_ALIAS\(\w+,(\w+),(\w+)\)|UT99_LAYOUT_ALIAS\(\w+,(\w+),(\w+)\)|"
                 r"UT99RegisterNativeFieldLayout\(GetClass\(\),TEXT\(\"(\w+)\"\),TEXT\(\"(\w+)\"\)")


def registrations(src):
    regs = {}
    files = glob.glob(os.path.join(src, "*", "Src", "*.cpp")) + glob.glob(os.path.join(src, "*", "Src", "*.inc"))
    texts = {os.path.basename(f): open(f, encoding="latin-1").read() for f in files}
    for name, text in texts.items():
        for m in re.finditer(r"void\s+[AU](\w+)::StaticConstructor\(\)\s*\{(.*?)^\}", text, re.S | re.M):
            cls, body = m.group(1).lower(), m.group(2)
            # Inline the generated include used by AActor.
            for inc in re.findall(r'#include "(\w+\.inc)"', body):
                body += texts.get(inc, "")
            fields = regs.setdefault(cls, {})
            for r in REG.finditer(body):
                g = r.groups()
                if g[0]:
                    fields.setdefault(g[0].lower(), set()).add(g[1])
                elif g[2]:
                    fields.setdefault(g[2].lower(), set()).add("BoolProperty")
                elif g[3]:
                    fields.setdefault(g[3].lower(), set()).add(g[4])
                elif g[5]:
                    fields.setdefault(g[5].lower(), set()).add(g[6])
                elif g[7]:
                    fields.setdefault(g[7].lower(), set()).add(g[8])
    return regs


def main():
    regs = registrations(sys.argv[1])
    for path in sys.argv[2:]:
        pkg = ue1pkg.Package(path)
        for ref, e in pkg.classes():
            k = e["name"].lower()
            if not regs.get(k):
                continue
            problems = []
            for c in pkg.children(ref):
                if c["type"] not in ue1pkg.PROPERTY_CLASSES:
                    continue
                t = regs[k].get(c["name"].lower())
                if t is None:
                    problems.append("missing %s:%s" % (c["name"], c["type"]))
                elif c["type"] not in t:
                    problems.append("type %s: script %s, C++ %s" % (c["name"], c["type"], "/".join(sorted(t))))
            if problems:
                print("%s: %s" % (e["name"], "; ".join(problems)))


if __name__ == "__main__":
    main()
