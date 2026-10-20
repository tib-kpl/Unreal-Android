"""Minimal Unreal Engine 1 package reader (package versions 60-69).

Reads names, imports and exports, then the declarations of classes, structs,
functions and properties (not the bytecode). Used to compare the native
interface that different game releases expect from the engine.
"""
import struct
import sys

CPF_Parm = 0x80
CPF_OutParm = 0x100
CPF_ReturnParm = 0x400
CPF_Net = 0x20
FUNC_Net = 0x40
FUNC_Native = 0x400
RF_HasStack = 0x02000000

PROPERTY_CLASSES = {
    "ByteProperty", "IntProperty", "BoolProperty", "FloatProperty",
    "ObjectProperty", "NameProperty", "StringProperty", "StrProperty",
    "ClassProperty", "ArrayProperty", "StructProperty", "VectorProperty",
    "RotatorProperty", "FixedArrayProperty", "MapProperty", "PointerProperty",
    "ButtonProperty",
}


class Reader:
    def __init__(self, data, pos=0):
        self.d = data
        self.p = pos

    def u8(self):
        v = self.d[self.p]
        self.p += 1
        return v

    def u16(self):
        v = struct.unpack_from("<H", self.d, self.p)[0]
        self.p += 2
        return v

    def i32(self):
        v = struct.unpack_from("<i", self.d, self.p)[0]
        self.p += 4
        return v

    def u32(self):
        v = struct.unpack_from("<I", self.d, self.p)[0]
        self.p += 4
        return v

    def u64(self):
        v = struct.unpack_from("<Q", self.d, self.p)[0]
        self.p += 8
        return v

    def idx(self):
        b = self.u8()
        neg = b & 0x80
        v = b & 0x3F
        if b & 0x40:
            shift = 6
            while True:
                b = self.u8()
                v |= (b & 0x7F) << shift
                shift += 7
                if not (b & 0x80):
                    break
        return -v if neg else v


class Package:
    def __init__(self, path):
        self.path = path
        self.d = open(path, "rb").read()
        r = Reader(self.d)
        tag = r.u32()
        if tag != 0x9E2A83C1:
            raise ValueError("not a package: " + path)
        fv = r.u32()
        self.ver = fv & 0xFFFF
        self.lic = fv >> 16
        self.flags = r.u32()
        nc, no, ec, eo, ic, io = (r.u32() for _ in range(6))
        self.names = []
        r.p = no
        for _ in range(nc):
            if self.ver >= 64:
                n = r.idx()
                s = self.d[r.p:r.p + n]
                r.p += n
                s = s.rstrip(b"\0")
            else:
                e = self.d.index(b"\0", r.p)
                s = self.d[r.p:e]
                r.p = e + 1
            r.u32()
            self.names.append(s.decode("latin-1"))
        self.imports = []
        r.p = io
        for _ in range(ic):
            cp, cn = r.idx(), r.idx()
            pkg = r.i32()
            on = r.idx()
            self.imports.append({"classpkg": self.names[cp], "class": self.names[cn],
                                 "outer": pkg, "name": self.names[on]})
        self.exports = []
        r.p = eo
        for _ in range(ec):
            cls, sup = r.idx(), r.idx()
            pkg = r.i32()
            on = r.idx()
            fl = r.u32()
            size = r.idx()
            off = r.idx() if size > 0 else 0
            self.exports.append({"class": cls, "super": sup, "outer": pkg,
                                 "name": self.names[on], "flags": fl,
                                 "size": size, "offset": off})

    def obj_name(self, ref):
        if ref > 0:
            return self.exports[ref - 1]["name"]
        if ref < 0:
            return self.imports[-ref - 1]["name"]
        return "None"

    def class_name(self, e):
        c = e["class"]
        return "Class" if c == 0 else self.obj_name(c)

    def path(self, ref):
        parts = []
        while ref:
            parts.append(self.obj_name(ref))
            ref = (self.exports[ref - 1]["outer"] if ref > 0
                   else self.imports[-ref - 1]["outer"])
        return ".".join(reversed(parts))

    # --- field parsing -------------------------------------------------
    def _object_header(self, e, r):
        if e["flags"] & RF_HasStack:
            node = r.idx()
            r.idx()
            r.u64()
            r.i32()
            if node:
                r.idx()
        if self.class_name(e) != "Class":
            # Intrinsic field classes have no script properties: just "None".
            while True:
                n = r.idx()
                if self.names[n] == "None":
                    break
                raise ValueError("unexpected tagged property in field " + e["name"])

    def field(self, i):
        """Parse export i (0-based) as a UField subclass."""
        e = self.exports[i]
        r = Reader(self.d, e["offset"])
        self._object_header(e, r)
        out = {"name": e["name"], "type": self.class_name(e)}
        out["super"] = r.idx()
        out["next"] = r.idx()
        t = out["type"]
        if t in PROPERTY_CLASSES:
            out["dim"] = r.i32()
            out["pflags"] = r.u32()
            out["category"] = self.names[r.idx()]
            if out["pflags"] & CPF_Net:
                r.u16()
            if t == "ByteProperty":
                out["enum"] = self.obj_name(r.idx())
            elif t in ("ObjectProperty", "ClassProperty"):
                out["pclass"] = self.obj_name(r.idx())
                if t == "ClassProperty":
                    out["meta"] = self.obj_name(r.idx())
            elif t == "StructProperty":
                out["struct"] = self.obj_name(r.idx())
            elif t in ("ArrayProperty", "FixedArrayProperty"):
                out["inner"] = r.idx()
            return out
        if t in ("Class", "State", "Function", "Struct"):
            out["scripttext"] = r.idx()
            out["children"] = r.idx()
            out["friendly"] = self.names[r.idx()]
            if t == "Function":
                end = e["offset"] + e["size"]
                if self.ver <= 63:
                    flags = struct.unpack_from("<I", self.d, end - 4)[0]
                    base = end - 12
                    if flags & FUNC_Net:
                        flags = struct.unpack_from("<I", self.d, end - 6)[0]
                        base = end - 14
                    out["native"] = struct.unpack_from("<H", self.d, base + 2)[0]
                    out["prec"] = self.d[base + 5]
                else:
                    flags = struct.unpack_from("<I", self.d, end - 4)[0]
                    base = end - 7
                    if flags & FUNC_Net:
                        flags = struct.unpack_from("<I", self.d, end - 6)[0]
                        base = end - 9
                    out["native"] = struct.unpack_from("<H", self.d, base)[0]
                    out["prec"] = self.d[base + 2]
                out["fflags"] = flags
        return out

    def children(self, struct_ref):
        """Yield parsed child fields of a struct/class/function export ref."""
        f = self.field(struct_ref - 1)
        ref = f["children"]
        seen = 0
        while ref > 0 and seen < 100000:
            c = self.field(ref - 1)
            c["ref"] = ref
            yield c
            ref = c["next"]
            seen += 1

    def classes(self):
        for i, e in enumerate(self.exports):
            if self.class_name(e) == "Class":
                yield i + 1, e

    def prop_type(self, p):
        t = p["type"]
        if t == "ByteProperty":
            return "byte" + ("(%s)" % p["enum"] if p.get("enum", "None") != "None" else "")
        if t == "ObjectProperty":
            return p["pclass"] + "*"
        if t == "ClassProperty":
            return "class<%s>" % p["meta"]
        if t == "StructProperty":
            return "struct " + p["struct"]
        if t in ("ArrayProperty", "FixedArrayProperty"):
            inner = self.field(p["inner"] - 1) if p["inner"] > 0 else None
            return "array<%s>" % (self.prop_type(inner) if inner else "?")
        return t.replace("Property", "").lower()


def describe(path):
    pkg = Package(path)
    out = {"ver": pkg.ver, "lic": pkg.lic, "classes": {}, "natives": []}
    for ref, e in pkg.classes():
        cls = {"super": pkg.obj_name(e["super"]), "props": [], "funcs": []}
        for c in pkg.children(ref):
            if c["type"] in PROPERTY_CLASSES:
                cls["props"].append((c["name"], pkg.prop_type(c), c["dim"], c["pflags"]))
            elif c["type"] == "Function":
                params = []
                for p in pkg.children(c["ref"]):
                    if p["type"] in PROPERTY_CLASSES and p["pflags"] & CPF_Parm:
                        params.append((p["name"], pkg.prop_type(p), p["pflags"]))
                fn = {"name": c["name"], "native": c["native"], "flags": c["fflags"],
                      "prec": c["prec"], "params": params}
                cls["funcs"].append(fn)
                if c["fflags"] & FUNC_Native:
                    out["natives"].append((c["native"], e["name"], c["name"], c["fflags"], params))
        out["classes"][e["name"]] = cls
    return out


if __name__ == "__main__":
    for path in sys.argv[1:]:
        info = describe(path)
        print(path, "ver", info["ver"], "lic", info["lic"], "classes", len(info["classes"]),
              "native funcs", len(info["natives"]))
