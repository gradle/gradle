/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.build

import com.google.common.base.Charsets
import groovy.json.JsonSlurper
import org.gradle.util.GradleVersion
import org.gradle.util.VersionNumber

class ReleasedVersionsFromVersionControl implements ReleasedVersions {
    private static final List<String> BANNED_VERSIONS = []
    private lowestInterestingVersion = GradleVersion.version("0.8")
    private lowestTestedVersion = GradleVersion.version("1.0")

    ReleasedVersion mostRecentRelease
    List<ReleasedVersion> allVersions
    List<ReleasedVersion> testedVersions
    ReleasedVersion mostRecentSnapshot

    ReleasedVersionsFromVersionControl(File releasedVersionsFile, File currentBaseVersionFile) {
        def currentBaseVersion = GradleVersion.version(currentBaseVersionFile.text.trim())
        def json = new JsonSlurper().parse(releasedVersionsFile, Charsets.UTF_8.name())
        mostRecentSnapshot = ReleasedVersion.fromMap(json.latestReleaseSnapshot)
        def mostRecentRc = ReleasedVersion.fromMap(json.latestRc)
        List<ReleasedVersion> finalReleases = json.finalReleases.collect {
            ReleasedVersion.fromMap(it)
        }.sort {
            it.version
        }.reverse()
        def latestFinalRelease = finalReleases.head()
        def latestNonFinalRelease = [mostRecentSnapshot, mostRecentRc].findAll { it.version > latestFinalRelease.version }.sort { it.buildTimeStamp }.reverse().find()
        allVersions = ([latestNonFinalRelease] + finalReleases).findAll().findAll { it.version >= lowestInterestingVersion && it.version.baseVersion < currentBaseVersion}
        testedVersions = allVersions.findAll { it.version >= lowestTestedVersion }
        mostRecentRelease = allVersions.head()
    }

    @Override
    List<String> getAllVersions() {
        return allVersions*.version*.version
    }

    @Override
    String getMostRecentRelease() {
        return mostRecentRelease.version.version
    }

    @Override
    String getMostRecentSnapshot() {
        return mostRecentSnapshot.version.version
    }

    List<String> getTestedVersions(boolean selection=false) {
        //Only use latest patch release of each Gradle version
        List<VersionNumber> versions = (testedVersions*.version*.version - BANNED_VERSIONS)
            .collect { VersionNumber.parse(it) }
            .groupBy { "$it.major.$it.minor" }
            .collectEntries { k, v -> [k, { v.sort(); [v[-1]]}() as Set]}
            .values().flatten()

        if (selection) {
            //Limit to first and last release of each major version
            versions = versions
                .groupBy { it.major }
                .collectEntries { k, v -> [k, { v.sort(); [v[0], v[-1]]}() as Set]}
                .values().flatten()
        }

        versions.sort().collect {
            // reformat according to our versioning scheme, since toString() would typically convert 1.0 to 1.0.0
            // The call of toString is required to avoid GString cast exceptions when used from Kotlin/Java.
            "$it.major.${it.minor}${it.micro>0?'.'+it.micro:''}${it.qualifier? '-' + it.qualifier:''}".toString()
        }
    }
}
