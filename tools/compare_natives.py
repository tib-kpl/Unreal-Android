"""Compare the native functions and native class layouts of two sets of packages.

Usage: compare_natives.py <target dir> <reference dir> [packages...]
Prints the native functions the target needs that the reference lacks (by
class and name), and native-index differences.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import ue1pkg  # noqa: E402


def load(folder, packages):
    natives, classes = {}, {}
    for p in packages:
        path = os.path.join(folder, p + ".u")
        if not os.path.isfile(path):
            continue
        info = ue1pkg.describe(path)
        for idx, cls, fn, flags, params in info["natives"]:
            natives[(cls.lower(), fn.lower())] = (idx, cls, fn, params)
        for name, c in info["classes"].items():
            classes[name.lower()] = (p, name, c)
    return natives, classes


def main():
    target, ref = sys.argv[1], sys.argv[2]
    packages = sys.argv[3:] or ["Core", "Engine"]
    tn, tc = load(target, packages)
    rn, rc = load(ref, packages)
    missing = [v for k, v in tn.items() if k not in rn]
    renumbered = [(v, rn[k][0]) for k, v in tn.items() if k in rn and rn[k][0] != v[0]]
    print("target natives", len(tn), "reference natives", len(rn))
    print("\n== natives missing in reference (%d)" % len(missing))
    for idx, cls, fn, params in sorted(missing, key=lambda x: (x[1], x[0])):
        print("  %-18s %-28s native(%d)  %s" % (cls, fn, idx, ", ".join(p[1] for p in params)))
    print("\n== natives with another index in reference (%d)" % len(renumbered))
    for (idx, cls, fn, _), ridx in sorted(renumbered, key=lambda x: x[0][1]):
        print("  %-18s %-28s %d (ref %d)" % (cls, fn, idx, ridx))
    print("\n== classes missing in reference")
    print("  " + " ".join(sorted(v[1] for k, v in tc.items() if k not in rc)))
    print("\n== property layout differences of shared classes")
    for k, (pkg, name, c) in sorted(tc.items()):
        if k not in rc:
            continue
        a = [(p[0].lower(), p[1], p[2]) for p in c["props"]]
        b = [(p[0].lower(), p[1], p[2]) for p in rc[k][2]["props"]]
        if a != b:
            added = [p for p in a if p not in b]
            removed = [p for p in b if p not in a]
            print("  %s.%s: +%d -%d" % (pkg, name, len(added), len(removed)))
            for p in added:
                print("      + %s %s%s" % (p[1], p[0], "[%d]" % p[2] if p[2] > 1 else ""))
            for p in removed:
                print("      - %s %s%s" % (p[1], p[0], "[%d]" % p[2] if p[2] > 1 else ""))


if __name__ == "__main__":
    main()
