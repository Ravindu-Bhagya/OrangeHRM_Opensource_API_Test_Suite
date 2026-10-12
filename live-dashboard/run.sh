#!/usr/bin/env bash
# Run the suite and keep Maven's console where the live view can tail it.
#
#   live-dashboard/run.sh                 # whole suite
#   live-dashboard/run.sh -Dtest=PimTest  # extra args pass through to mvn
#
# Without this wrapper the suite still runs fine and the dashboard still tracks
# it — the console panel just shows a stream rebuilt from the results instead of
# Maven's real output.
set -o pipefail
cd "$(dirname "$0")/.."
mkdir -p target
mvn -B test "$@" 2>&1 | tee target/live-run.log
