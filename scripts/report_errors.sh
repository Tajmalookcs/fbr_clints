#!/usr/bin/env bash
# Turns compiler / test errors in a Gradle log into one GitHub annotation,
# so they can be read without opening the full log.
log="$1"
msg=$(grep -E -A3 '^e: |error:|FAILED|AssertionError|Exception|What went wrong' "$log" \
  | grep -v '^--$' | head -n 120 | cut -c1-300)
[ -z "$msg" ] && msg=$(tail -n 60 "$log")
msg="${msg//'%'/'%25'}"
msg="${msg//$'\r'/}"
msg="${msg//$'\n'/'%0A'}"
echo "::error title=Build errors::$msg"
