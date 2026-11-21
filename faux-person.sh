#!/usr/bin/env bash
# Print random people with the faux-person library, to stdout or to a file.
#
#   ./faux-person.sh -n 5 --seed 42   # to stdout
#   ./faux-person.sh -o people.json -n 1000 -f json   # to a file
#
# Run ./faux-person.sh --help for the options. faux-common.sh does the work.
prog="faux-person.sh"
main_class="org.tauasa.faux.cli.FauxPersonCli"
what="people"
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/faux-common.sh"
