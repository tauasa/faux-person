#!/usr/bin/env bash
# Print random people with the faux-person CLI, to stdout or to a file.
#
#   ./faux-person.sh -n 5 --seed 42                 # to stdout
#   ./faux-person.sh -o people.json -n 1000 -f json  # to a file
#
# Builds the jar with Maven on first use. Every other option goes to the CLI;
# run ./faux-person.sh --help for the list. Set JAVA_OPTS to pass JVM options.
set -euo pipefail

dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
output=""
rebuild=0
args=()

usage() {
  cat <<'EOF'
usage: faux-person.sh [-o FILE] [--rebuild] [CLI options]

  -o, --output FILE    write the people to FILE instead of stdout
      --rebuild        rebuild the jar with Maven before running
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    -o|--output)
      [ $# -ge 2 ] || { echo "faux-person.sh: $1 needs a file name" >&2; exit 2; }
      output="$2"; shift 2 ;;
    --output=*) output="${1#*=}"; shift ;;
    --rebuild) rebuild=1; shift ;;
    -h|--help) usage; echo; args+=("--help"); shift ;;
    *) args+=("$1"); shift ;;
  esac
done

find_jar() {
  local j
  for j in "$dir"/target/faux-person-*.jar; do
    case "$j" in
      *-sources.jar|*-javadoc.jar) ;;
      *) [ -f "$j" ] && { echo "$j"; return 0; } ;;
    esac
  done
  return 1
}

if [ "$rebuild" -eq 1 ] || ! jar="$(find_jar)"; then
  command -v mvn >/dev/null || { echo "faux-person.sh: Maven (mvn) is needed to build the jar" >&2; exit 1; }
  echo "faux-person.sh: building the jar..." >&2
  mvn -q -B -f "$dir/pom.xml" -DskipTests package >&2
  jar="$(find_jar)" || { echo "faux-person.sh: build finished but no jar in $dir/target" >&2; exit 1; }
fi

# ${args[@]+...} keeps "set -u" happy on bash 3.2 (macOS) when there are no args.
if [ -n "$output" ]; then
  # Write to a temp file first, so a failed run doesn't leave a half-written file.
  tmp="$(mktemp "${output}.XXXXXX")"
  trap 'rm -f "$tmp"' EXIT
  java ${JAVA_OPTS:-} -jar "$jar" ${args[@]+"${args[@]}"} > "$tmp"
  mv "$tmp" "$output"
  trap - EXIT
  echo "faux-person.sh: wrote $output" >&2
else
  exec java ${JAVA_OPTS:-} -jar "$jar" ${args[@]+"${args[@]}"}
fi
