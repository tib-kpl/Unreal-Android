"""Per-class differences of property order, natives and events between two packages.

Usage: class_diff.py <target.u> <reference.u>
Only classes present in both packages and declared native are compared.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import ue1pkg  # noqa: E402

FUNC_Event = 0x800
FUNC_Native = 0x400
CLASS_Native = 0x80  # v400 CLASS_Native flag is not stored per class; use natives/C++ heuristics


def summarize(path):
    pkg = ue1pkg.Package(path)
    info = ue1pkg.describe(path)
    out = {}
    for name, c in info["classes"].items():
        events = [f["name"] for f in c["funcs"] if f["flags"] & FUNC_Event]
        natives = [f["name"] for f in c["funcs"] if f["flags"] & FUNC_Native]
        out[name.lower()] = {"name": name, "props": c["props"], "events": events, "natives": natives}
    return out


def main():
    a, b = summarize(sys.argv[1]), summarize(sys.argv[2])
    for k in sorted(a):
        if k not in b:
            continue
        ca, cb = a[k], b[k]
        pa = [p[0].lower() for p in ca["props"]]
        pb = [p[0].lower() for p in cb["props"]]
        ea, eb = set(ca["events"]), set(cb["events"])
        na, nb = set(ca["natives"]), set(cb["natives"])
        if pa == pb and ea == eb and na == nb:
            continue
        print("== %s" % ca["name"])
        if pa != pb:
            print("  props target: %s" % ", ".join("%s:%s%s" % (p[0], p[1], "[%d]" % p[2] if p[2] > 1 else "")
                                                   for p in ca["props"]))
            print("  only in reference: %s" % ", ".join(p for p in pb if p not in pa))
        if ea != eb:
            print("  events +%s -%s" % (sorted(ea - eb), sorted(eb - ea)))
        if na != nb:
            print("  natives +%s -%s" % (sorted(na - nb), sorted(nb - na)))


if __name__ == "__main__":
    main()
