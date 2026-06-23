/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.util.internal.VersionNumber
import org.jetbrains.annotations.VisibleForTesting

/**
 * Fetch the latest Kotlin versions and write a properties file.
 * Never up-to-date, non-cacheable.
 */
@UntrackedTask(because = "Not worth tracking")
abstract class UpdateKotlinVersions : AbstractVersionsUpdateTask() {

    @get:Internal
    abstract val minimumSupported: Property<String>

    @get:Internal
    abstract val compatibilityDocFile: RegularFileProperty

    @TaskAction
    fun action() =
        fetchLatestKotlinVersions().let { latestKotlinVersions ->
            updateProperties {
                setProperty("latests", latestKotlinVersions.joinToString(","))
            }
            updateCompatibilityDoc(
                compatibilityDocFile,
                "Gradle is tested with Kotlin",
                latestKotlinVersions.first(),
                latestKotlinVersions.last()
            )
        }

    private
    fun fetchLatestKotlinVersions() =
        fetchAndSelectKotlinVersions(
            minimumSupported.get(),
            "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/maven-metadata.xml"
        )

    private
    fun fetchAndSelectKotlinVersions(minimumSupported: String, mavenMetadataUrl: String): List<String> {
        return selectVersionsFrom(minimumSupported, fetchVersionsFromMavenMetadata(mavenMetadataUrl))
    }

    companion object {
        @VisibleForTesting
        @JvmStatic
        fun selectVersionsFrom(minimumSupported: String, allVersions: List<String>): List<String> {
            require(minimumSupported in allVersions) {
                "Minimum supported '$minimumSupported' was not found in available versions: $allVersions"
            }

            val minimumSupportedVersion = VersionNumber.parse(minimumSupported)
            val versionsByMinor = allVersions
                .map { VersionNumber.parse(it) }
                .filter { it >= minimumSupportedVersion }
                .sortedDescending()
                .groupBy { it.major to it.minor }
                .toSortedMap(compareBy({ it.first }, { it.second }))
            val currentMinor = versionsByMinor.lastKey()

            return buildList {
                versionsByMinor.forEach { (minor, versionsOfMinor) ->
                    if (minor == currentMinor) {
                        // Current minor: from the newest patch down, emit the best version per patch.
                        // VersionNumber's natural ordering covers it: stable (null) > RC3 > RC2 > RC > Beta5 > ... > Beta1 > Beta.
                        val versionsByPatch = versionsOfMinor
                            .groupBy { it.micro }
                            .toSortedMap()
                        for (patch in versionsByPatch.keys.reversed()) {
                            val best = versionsByPatch.getValue(patch).first()
                            add(best)
                            if (best.qualifier == null) break
                        }
                    } else {
                        // Not current minor: just take the latest stable and (if exists) the latest RC/Beta if it's newer than the stable.
                        val latestStable = versionsOfMinor.firstOrNull { it.qualifier == null }
                        if (latestStable != null) {
                            add(latestStable)
                            val latest = versionsOfMinor.first()
                            if (latest != latestStable) {
                                add(latest)
                            }
                        }
                    }
                }
                add(minimumSupportedVersion)
            }.distinct().sorted().map { it.toString() }
        }
    }
}
