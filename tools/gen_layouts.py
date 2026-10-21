"""Generate native field layout registrations for classes of a package.

Usage: gen_layouts.py <package.u> <Class> [<Class>...]
Prints UT99_LAYOUT_FIELD/UT99_LAYOUT_BOOL lines with the script property types.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import ue1pkg  # noqa: E402


def main():
    pkg = ue1pkg.Package(sys.argv[1])
    wanted = {c.lower() for c in sys.argv[2:]}
    for ref, e in pkg.classes():
        if e["name"].lower() not in wanted:
            continue
        print("// %s" % e["name"])
        for c in pkg.children(ref):
            if c["type"] not in ue1pkg.PROPERTY_CLASSES:
                continue
            if c["type"] == "BoolProperty":
                print("    UT99_LAYOUT_BOOL(%s)" % c["name"])
            else:
                print("    UT99_LAYOUT_FIELD(%s,%s)" % (c["name"], c["type"]))


if __name__ == "__main__":
    main()
