#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
classes=$(mktemp -d "${TMPDIR:-/tmp}/signature-core.XXXXXX")
trap 'rm -rf "$classes"' EXIT HUP INT TERM
java com.sun.tools.javac.Main --release 8 -d "$classes" \
    "$project_dir/app/src/main/java/com/example/signinginfooverride/OverrideFiles.java" \
    "$project_dir/app/src/test/java/com/example/signinginfooverride/OverrideFilesTest.java" \
    "$project_dir/app/src/main/java/com/example/signinginfooverride/PackageSwaps.java" \
    "$project_dir/app/src/main/java/com/example/signinginfooverride/TokenRequestSwap.java" \
    "$project_dir/app/src/test/java/com/example/signinginfooverride/PackageSwapsTest.java" \
    "$project_dir/app/src/main/java/com/example/signinginfooverride/EntitlementScope.java" \
    "$project_dir/app/src/main/java/com/example/signinginfooverride/AssetRoute.java" \
    "$project_dir/app/src/test/java/com/example/signinginfooverride/AssetRouteTest.java" \
    "$project_dir/app/src/main/java/com/example/signinginfooverride/AssetRecoveryGroups.java" \
    "$project_dir/app/src/test/java/com/example/signinginfooverride/AssetRecoveryGroupsTest.java" \
    "$project_dir/app/src/main/java/com/example/signinginfooverride/AssetWorkerMethods.java" \
    "$project_dir/app/src/test/java/com/example/signinginfooverride/AssetWorkerMethodsTest.java" \
    "$project_dir/app/src/test/java/com/example/signinginfooverride/EntitlementScopeTest.java" \
    "$project_dir/app/src/main/java/com/example/signinginfooverride/AppCatalog.java" \
    "$project_dir/app/src/main/java/com/example/signinginfooverride/CertificateLookup.java" \
    "$project_dir/app/src/test/java/com/example/signinginfooverride/AppCatalogTest.java" \
    "$project_dir/app/src/test/java/com/example/signinginfooverride/CertificateLookupTest.java" \
    "$project_dir/app/src/main/java/com/example/signinginfooverride/ContentCallPolicy.java" \
    "$project_dir/app/src/test/java/com/example/signinginfooverride/ContentCallPolicyTest.java"
java -Djava.io.tmpdir="$classes" -cp "$classes:$project_dir/app/src/test/resources" \
    com.example.signinginfooverride.OverrideFilesTest
java -cp "$classes" com.example.signinginfooverride.PackageSwapsTest
java -cp "$classes" com.example.signinginfooverride.EntitlementScopeTest
java -cp "$classes" com.example.signinginfooverride.AppCatalogTest
java -cp "$classes:$project_dir/app/src/test/resources" com.example.signinginfooverride.CertificateLookupTest
java -cp "$classes" com.example.signinginfooverride.ContentCallPolicyTest
java -cp "$classes" com.example.signinginfooverride.AssetRouteTest
java -cp "$classes" com.example.signinginfooverride.AssetRecoveryGroupsTest
java -cp "$classes" com.example.signinginfooverride.AssetWorkerMethodsTest
