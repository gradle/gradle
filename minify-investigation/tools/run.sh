#!/bin/bash
# Runs ProGuard on a rules file, timestamping each line of its output.
# usage: WORK=<dir with jars/ and jars/pg/> run.sh rules.pro [extra ProGuard args...]
tools=$(dirname "$0")
rm -rf "${WORK:?}/out"; mkdir -p "$WORK/out"
start=$EPOCHREALTIME
java -Xmx3g -Dwork="$WORK" -cp "$WORK/jars/pg/*" proguard.ProGuard "@$1" "${@:2}" 2>&1 | python3 -I "$tools/ts.py"
python3 -c "print('TOTAL %.1fs' % ($EPOCHREALTIME - $start))"
