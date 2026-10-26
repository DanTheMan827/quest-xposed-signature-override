#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
classes=$(mktemp -d "${TMPDIR:-/tmp}/signature-core.XXXXXX")
trap 'rm -rf "$classes"' EXIT HUP INT TERM
java com.sun.tools.javac.Main --release 8 -d "$classes" \
    "$project_dir/app/src/main/java/com/example/signinginfooverride/OverrideFiles.java" \
    "$project_dir/app/src/test/java/com/example/signinginfooverride/OverrideFilesTest.java"
java -Djava.io.tmpdir="$classes" -cp "$classes:$project_dir/app/src/test/resources" \
    com.example.signinginfooverride.OverrideFilesTest
