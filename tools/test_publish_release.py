"""Exercise interrupted GitHub release publication using a fake gh, without network access."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
bash = shutil.which("bash")
if os.name == "nt":
    bash = "C:/Program Files/Git/bin/bash.exe"

fixture = r'''
gh() {
  printf '%s\n' "$*" >> "$CASE/log"
  case "$2" in
    view) test -f "$CASE/draft" ;;
    create)
      touch "$CASE/draft"
      # Simulate a 502 after the server has already created the draft.
      return 1 ;;
    upload)
      if [ "$PERMANENT" = 1 ]; then return 1; fi
      if [ ! -f "$CASE/upload-failed" ]; then touch "$CASE/upload-failed"; return 1; fi
      touch "$CASE/uploaded" ;;
    edit)
      test -f "$CASE/uploaded" || return 90
      if [ ! -f "$CASE/edit-failed" ]; then touch "$CASE/edit-failed"; return 1; fi
      touch "$CASE/published" ;;
    *) return 99 ;;
  esac
}
sleep() { :; }
'''

with tempfile.TemporaryDirectory(prefix="publish-release-test-") as work:
    work = Path(work)
    stub = work / "fixture.sh"
    stub.write_text(fixture, encoding="utf-8", newline="\n")
    for permanent in (False, True):
        case = work / str(permanent)
        case.mkdir()
        env = dict(os.environ, CASE=case.as_posix(), PERMANENT=str(int(permanent)))
        result = subprocess.run([bash, "-c", 'source "$1"; source "$2" v1 sha title notes normal.apk automotive.apk',
                                 "test", str(stub), str(root / "tools/publish_release.sh")], env=env,
                                capture_output=True, text=True)
        log = (case / "log").read_text()
        assert log.count("release create") == 1, log
        assert log.count("release upload") == 3, log
        assert (case / "published").exists() == (not permanent), result.stderr
        assert (result.returncode == 0) == (not permanent), result.stderr
print("Release publication retry tests passed")
