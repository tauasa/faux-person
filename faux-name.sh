#!/usr/bin/env bash
# Print random names with the faux-person library, to stdout or to a file.
#
#   ./faux-name.sh -n 5 --seed 42 -r rus   # to stdout
#   ./faux-name.sh -o names.json -n 1000 -f json   # to a file
#
# Run ./faux-name.sh --help for the options. faux-common.sh does the work.
prog="faux-name.sh"
main_class="org.tauasa.faux.cli.FauxNameCli"
what="names"
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/faux-common.sh"
