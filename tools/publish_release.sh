#!/usr/bin/env bash
# Retry transient API/upload failures, resuming an existing draft after an HTTP 502.
set -euo pipefail
tag="$1"
sha="$2"
title="$3"
notes="$4"
shift 4
assets=("$@")
if [ "${#assets[@]}" -eq 0 ]; then
  echo 'No APK assets supplied' >&2
  exit 1
fi
publish() {
  if ! gh release view "$tag" >/dev/null 2>&1; then
    gh release create "$tag" --draft --target "$sha" --title "$title" --notes-file "$notes" || return 1
  fi
  # A failed upload must never make an incomplete release public.
  gh release upload "$tag" "${assets[@]}" --clobber || return 1
  gh release edit "$tag" --draft=false --latest --title "$title" --notes-file "$notes"
}
for attempt in 1 2 3 4; do
  if publish; then exit 0; fi
  if [ "$attempt" -lt 4 ]; then
    echo "Release publication failed (attempt $attempt/4); retrying in 15 seconds" >&2
    sleep 15
  fi
done
echo "Release publication failed after 4 attempts; APKs remain in the workflow artifacts" >&2
exit 1
