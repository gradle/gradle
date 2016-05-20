#!/usr/bin/env bash
#
# This script updates all gradle wrappers in this repository to the
# latest custom gradle-script-kotlin snapshot distribution published
# to repo.gradle.org in order to make the latest code changes available
# to the samples.
#
# The overall workflowis as follows:
#
#  1. A change is made here, in gradle/gradle-script-kotlin:master
#
#  2. This triggers a CI build that in turn publishes a snapshot
#     gradle-script-kotlin jar to repo.gradle.org/libs-snapshot-local
#
#  3. The successful completion of that CI build triggers a distribution
#     CI build that builds gradle/gradle:gradle-script-kotlin, including
#     the newly-published gradle-script-kotlin snapshot jar, and then
#     publishes the result as a new gradle-script-kotlin distribution
#     zip in repo.gradle.org/dist-snapshots
#
#  4. All of the above is automated. The execution of this script is not,
#     or at least is not yet. When a new snapshot distribution zip has
#     been published, simply run this script from the root of the
#     repository. It will determine the path to the latest snapshot
#     distribution zip, and will then update the `distributionUrl`
#     property in all gradle-wrapper.properties files and commit the
#     result.
#
# See https://builds.gradle.org/project.html?projectId=GradleScriptKotlin
# for complete details.

repo='dist-snapshots'

latestSnapshotPath=`
    http get https://repo.gradle.org/gradle/api/storage/$repo/ \
        | jq -r '(.children|last).uri'`

find . -name gradle-wrapper.properties \
    | xargs perl -p -i -e "s|^(distributionUrl=.*$repo).*|\$1$latestSnapshotPath|"

find . -name gradle-wrapper.properties \
    | xargs git commit -m"Update wrappers to latest snapshot distro"
