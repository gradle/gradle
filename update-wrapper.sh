#!/usr/bin/env bash

repo='dist-snapshots'

# .children[-2] selects the one before the last which is the all distro
latestSnapshotPath=`
    http get https://repo.gradle.org/gradle/api/storage/$repo/ \
        | jq -r '[ .children[] | select(.uri | startswith("/gradle-kotlin-dsl-")) ][-2].uri'`

find . -name gradle-wrapper.properties \
    | xargs perl -p -i -e "s|^(distributionUrl=.*$repo).*|\$1$latestSnapshotPath|"

find . -name gradle-wrapper.properties \
    | xargs git commit -m"Update wrappers to latest snapshot distro"
