"""Verify the native libraries and Gold patch required to launch both engines."""
import argparse
from pathlib import Path
from zipfile import ZipFile


def check(path):
    with ZipFile(path) as apk:
        required = [f"lib/{abi}/{name}" for abi in ("armeabi-v7a", "arm64-v8a")
                    for name in ("libSDL2.so", "libUnreal.so", "libUnreal226.so", "libopenal.so", "libxmp.so")]
        missing = set(required) - set(apk.namelist())
        if missing:
            raise ValueError(f"{path}: missing required libraries: {', '.join(sorted(missing))}")
        for name in required:
            if apk.read(name)[:4] != b"\x7fELF":
                raise ValueError(f"{path}: invalid native library: {name}")
        patch = apk.read("assets/gold_patches/System/UnrealGoldAndroid.u")
        if patch[:4] != bytes.fromhex("c1832a9e") or int.from_bytes(patch[4:6], "little") != 68:
            raise ValueError(f"{path}: invalid Gold 226 console package")
    print(f"PASS {path.name}: v200 + Gold engines, audio libraries and console patch, both ABIs")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", nargs="+", type=Path)
    for path in parser.parse_args().apk:
        check(path)


if __name__ == "__main__":
    main()
