"""Test Gold INI migrations with the actual Java implementation (JDK + Android SDK)."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--android-jar", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    java_home = Path(os.environ["JAVA_HOME"]) if os.environ.get("JAVA_HOME") else None
    suffix = ".exe" if os.name == "nt" else ""
    javac = str(java_home / "bin" / ("javac" + suffix)) if java_home else shutil.which("javac")
    java = str(java_home / "bin" / ("java" + suffix)) if java_home else shutil.which("java")
    sdk = Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", str(Path.home() / "AppData/Local/Android/Sdk"))))
    jars = sorted(sdk.glob("platforms/android-*/android.jar"))
    android = args.android_jar or (jars[-1] if jars else None)
    if not javac or not java or not android:
        parser.error("Set JAVA_HOME and ANDROID_HOME, or pass --android-jar")
    sources = root / "source/app/src/main/java/com/ast/unreal"
    with tempfile.TemporaryDirectory(prefix="gold-config-test-") as directory:
        work = Path(directory)
        (work / "Unreal226Activity.java").write_text('package com.ast.unreal; class Unreal226Activity { static final String INI_NAME="Android226.ini", USER_INI_NAME="Android226User.ini"; }')
        (work / "UnrealGold.java").write_text('package com.ast.unreal; class UnrealGold { static void installConsolePatch(android.content.Context context, java.io.File system) throws java.io.IOException {} }')
        subprocess.run([javac, "-cp", str(android), "-d", str(work), str(sources / "Unreal226Config.java"),
                        str(sources / "GoldControllerProfile.java"), str(sources / "GoldAudioProfile.java"), str(work / "Unreal226Activity.java"),
                        str(work / "UnrealGold.java"), str(root / "tools/tests/GoldConfigTest.java")], check=True)
        subprocess.run([java, "-cp", str(work) + os.pathsep + str(android), "com.ast.unreal.GoldConfigTest",
                        str(root / "source/app/src/main/assets/ue1_config/User.ini"),
                        str(root / "tools/tests/GoldDefUser.ini")], check=True)


if __name__ == "__main__":
    main()
