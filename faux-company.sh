#!/usr/bin/env bash
# Print random companies with the faux-person library, to stdout or to a file.
#
#   ./faux-company.sh -n 5 --seed 42   # to stdout
#   ./faux-company.sh -o companies.json -n 1000 -f json   # to a file
#
# Run ./faux-company.sh --help for the options. faux-common.sh does the work.
prog="faux-company.sh"
main_class="org.tauasa.faux.cli.FauxCompanyCli"
what="companies"
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/faux-common.sh"
