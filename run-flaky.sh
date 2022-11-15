#!/bin/bash

RUNS=100

for i in $(seq -f "%5.0f" $RUNS); do
    printf "RUN (%3s / %s)\n" $i $RUNS
    ./gradlew --quiet --console=plain :dependency-management:embeddedIntegTest --rerun --tests "org.gradle.integtests.resolve.transform.ArtifactTransformParallelIntegrationTest.transformations are applied in parallel for a mix of external and file dependency artifacts" || break
    echo "--------------------------------------------------------------------------------"
done
