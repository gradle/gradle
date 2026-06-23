/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild.packaging.support

import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import java.io.File
import java.io.FileInputStream


/**
 * Shared utility for parsing Maven POM files to extract license and parent metadata.
 *
 * Used both at configuration time (to discover parent POM coordinates for pre-resolution)
 * and at task execution time (to extract license names from the pre-resolved POM files).
 */
object PomLicenseUtils {

    /** Coordinates of a Maven `<parent>` declaration inside a POM. */
    data class ParentCoordinates(val groupId: String, val artifactId: String, val version: String)

    data class PomInfo(
        val groupId: String,
        val artifactId: String,
        val version: String?,
        val licenseName: String?,
        val licenseUrl: String?,
        /** Non-null only when the POM declares a `<parent>` block with complete coordinates. */
        val parent: ParentCoordinates?,
    )

    /**
     * Parses [pomFile] and returns a [PomInfo], or `null` if the file cannot be parsed or
     * lacks the mandatory `groupId` / `artifactId` coordinates.
     */
    fun parsePom(pomFile: File): PomInfo? {
        val model = runCatching {
            FileInputStream(pomFile).use { stream -> MavenXpp3Reader().read(stream) }
        }.getOrNull() ?: return null

        val groupId = model.groupId ?: model.parent?.groupId ?: return null
        val artifactId = model.artifactId ?: return null
        val version = model.version ?: model.parent?.version

        val firstLicense = model.licenses.firstOrNull()
        val licenseName = firstLicense?.name?.trim()?.takeIf { it.isNotBlank() }
        val licenseUrl = firstLicense?.url?.trim()?.takeIf { it.isNotBlank() }

        val parent = model.parent?.let { p ->
            val pg = p.groupId
            val pa = p.artifactId
            val pv = p.version
            if (pg != null && pa != null && pv != null) ParentCoordinates(pg, pa, pv) else null
        }

        return PomInfo(
            groupId = groupId,
            artifactId = artifactId,
            version = version,
            licenseName = licenseName,
            licenseUrl = licenseUrl,
            parent = parent,
        )
    }
}
