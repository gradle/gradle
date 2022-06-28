/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.identity.extension

import com.google.gson.Gson
import gradlebuild.identity.model.ReleasedVersions
import org.gradle.api.file.RegularFile
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber


class ReleasedVersionsDetails(currentBaseVersion: GradleVersion, releasedVersionsFile: RegularFile) {
    val allPreviousVersions: List<GradleVersion>
    val mostRecentRelease: GradleVersion
    val mostRecentSnapshot: GradleVersion

    val allTestedVersions: List<GradleVersion>

    val mainTestedVersions: List<GradleVersion>

    init {
        val lowestInterestingVersion = GradleVersion.version("0.8")
        val lowestTestedVersion = GradleVersion.version("1.0")

        val releasedVersions = releasedVersionsFile.asFile.reader().use {
            Gson().fromJson(it, ReleasedVersions::class.java)
        }

        val latestFinalRelease = releasedVersions.finalReleases.first()
        val latestRelease = listOf(releasedVersions.latestReleaseSnapshot, releasedVersions.latestRc).filter { it.gradleVersion() > latestFinalRelease.gradleVersion() }.maxByOrNull { it.buildTimeStamp() } ?: latestFinalRelease
        val previousVersions = (listOf(latestRelease) + releasedVersions.finalReleases).filter { it.gradleVersion() >= lowestInterestingVersion && it.gradleVersion().baseVersion < currentBaseVersion }.distinct()
        allPreviousVersions = previousVersions.map { it.gradleVersion() }
        mostRecentRelease = previousVersions.first().gradleVersion()
        mostRecentSnapshot = releasedVersions.latestReleaseSnapshot.gradleVersion()

        val testedVersions = previousVersions.filter { it.gradleVersion() >= lowestTestedVersion }
        // Only use latest patch release of each Gradle version
        allTestedVersions = testedVersions.map { VersionNumber.parse(it.gradleVersion().version) }
            .groupBy { "${it.major}.${it.minor}" }
            .map { (_, v) -> v.maxOrNull()!!.format() }

        // Limit to first and last release of each major version
        mainTestedVersions = testedVersions.map { VersionNumber.parse(it.gradleVersion().version) }
            .groupBy { it.major }
            .map { (_, v) -> listOf(v.minOrNull()!!.format(), v.maxOrNull()!!.format()) }.flatten()
    }

    private
    fun VersionNumber.format() =
        // reformat according to our versioning scheme, since toString() would typically convert 1.0 to 1.0.0
        GradleVersion.version("$major.${minor}${if (micro > 0) ".$micro" else ""}${if (qualifier != null) "-$qualifier" else ""}")
}
