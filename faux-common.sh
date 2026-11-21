# Shared by faux-person.sh, faux-name.sh and faux-company.sh. Not run on its own:
# each script sets these three, then sources this file with its arguments.
#
#   prog        the script's name, for messages ("faux-name.sh")
#   main_class  the CLI class to run ("org.tauasa.faux.cli.FauxNameCli")
#   what        what it prints, for the usage text ("names")
#
# Builds the jar with Maven on first use, and again whenever pom.xml or a file
# under src/ is newer than the jar. Every option except -o and --rebuild goes to
# the CLI. Set JAVA_OPTS to pass JVM options.
set -euo pipefail

dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
output=""
rebuild=0
args=()

usage() {
  cat <<EOF
usage: $prog [-o FILE] [--rebuild] [CLI options]

  -o, --output FILE    write the $what to FILE instead of stdout
      --rebuild        rebuild the jar with Maven before running (it also
                       rebuilds by itself when the source is newer than the jar)
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    -o|--output)
      [ $# -ge 2 ] || { echo "$prog: $1 needs a file name" >&2; exit 2; }
      output="$2"; shift 2 ;;
    --output=*) output="${1#*=}"; shift ;;
    --rebuild) rebuild=1; shift ;;
    -h|--help) usage; echo; args+=("--help"); shift ;;
    *) args+=("$1"); shift ;;
  esac
done

# The most recently built jar, so an old version left in target/ never wins.
find_jar() {
  local j best=""
  for j in "$dir"/target/faux-person-*.jar; do
    case "$j" in
      *-sources.jar|*-javadoc.jar) continue ;;
    esac
    [ -f "$j" ] || continue
    if [ -z "$best" ] || [ "$j" -nt "$best" ]; then best="$j"; fi
  done
  [ -n "$best" ] && echo "$best"
}

# True when pom.xml or anything under src/ changed after the jar was built.
stale() {
  [ -n "$(find "$dir/pom.xml" "$dir/src" -newer "$1" -print -quit 2>/dev/null)" ]
}

if [ -n "$output" ] && [ -d "$output" ]; then
  echo "$prog: $output is a directory; give a file name" >&2
  exit 2
fi

jar="$(find_jar || true)"
if [ "$rebuild" -eq 1 ] || [ -z "$jar" ] || stale "$jar"; then
  command -v mvn >/dev/null || { echo "$prog: Maven (mvn) is needed to build the jar" >&2; exit 1; }
  echo "$prog: building the jar..." >&2
  mvn -q -B -f "$dir/pom.xml" -DskipTests package >&2
  jar="$(find_jar || true)"
  [ -n "$jar" ] || { echo "$prog: build finished but no jar in $dir/target" >&2; exit 1; }
fi

# ${args[@]+...} keeps "set -u" happy on bash 3.2 (macOS) when there are no args.
if [ -n "$output" ]; then
  # Write to a temp file first, so a failed run doesn't leave a half-written file.
  # "--" so a file name starting with "-" isn't read as an option.
  tmp="$(mktemp -- "${output}.XXXXXX")"
  trap 'rm -f "$tmp"' EXIT
  # mktemp makes the file 0600; give it the mode a plain "> file" would.
  chmod "$(printf '%o' $(( 0666 & ~0$(umask) )))" "$tmp"
  java ${JAVA_OPTS:-} -cp "$jar" "$main_class" ${args[@]+"${args[@]}"} > "$tmp"
  mv -- "$tmp" "$output"
  trap - EXIT
  echo "$prog: wrote $output" >&2
else
  exec java ${JAVA_OPTS:-} -cp "$jar" "$main_class" ${args[@]+"${args[@]}"}
fi
