#!/bin/sh
# Builds the user manual as a PDF: pandoc turns docs/manual.md into Typst, Typst sets it.
# Usage: docs/manual/build.sh [output.pdf]   (default: manual.pdf in the current directory)
# Needs pandoc 3.7 or later and typst on the PATH. Libertinus Serif is built into Typst.
set -eu

out=${1:-manual.pdf}
case $out in
  /*) ;;
  *) out=$(pwd)/$out ;;
esac

docs=$(cd "$(dirname "$0")/.." && pwd)
cd "$docs"

pandoc manual.md \
  --from markdown \
  --pdf-engine=typst \
  --resource-path=. \
  -V mainfont="Libertinus Serif" \
  -o "$out"
