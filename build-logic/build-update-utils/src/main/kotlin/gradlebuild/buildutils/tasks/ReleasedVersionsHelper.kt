/*
 * Copyright 2025 the original author or authors.
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

package gradlebuild.buildutils.tasks

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import gradlebuild.buildutils.model.ReleasedVersion
import gradlebuild.buildutils.model.ReleasedVersions
import org.gradle.util.GradleVersion
import java.io.File


fun bumpPatchVersion(version: String): String {
    val parts = version.split(".")
    require(parts.size == 3 && parts.all { it.toIntOrNull() != null }) {
        "Version '$version' is not a valid x.y.z version."
    }
    return "${parts[0]}.${parts[1]}.${parts[2].toInt() + 1}"
}


fun updateReleasedVersionFile(releasedVersionsFile: File, currentReleasedVersion: ReleasedVersion) {
    val releasedVersions = releasedVersionsFile.reader().use {
        Gson().fromJson(it, ReleasedVersions::class.java)
    }
    val newReleasedVersions = updateReleasedVersions(currentReleasedVersion, releasedVersions)
    releasedVersionsFile.writeText(GsonBuilder().setPrettyPrinting().create().toJson(newReleasedVersions))
}


fun updateReleasedVersions(currentReleasedVersion: ReleasedVersion, releasedVersions: ReleasedVersions) =
    ReleasedVersions(
        if (currentReleasedVersion.gradleVersion().isSnapshot) {
            newerVersion(currentReleasedVersion, releasedVersions.latestReleaseSnapshot)
        } else {
            releasedVersions.latestReleaseSnapshot
        },
        if (!currentReleasedVersion.gradleVersion().isSnapshot && !currentReleasedVersion.gradleVersion().finalRelease()) {
            newerVersion(currentReleasedVersion, releasedVersions.latestRc)
        } else {
            releasedVersions.latestRc
        },
        if (currentReleasedVersion.gradleVersion().finalRelease()) {
            (releasedVersions.finalReleases + currentReleasedVersion).sortedBy { it.gradleVersion() }.reversed()
        } else {
            releasedVersions.finalReleases
        }
    )


private
fun newerVersion(releasedVersion: ReleasedVersion, other: ReleasedVersion) =
    if (releasedVersion.gradleVersion() > other.gradleVersion()) releasedVersion else other


private
fun GradleVersion.finalRelease() = this == baseVersion
