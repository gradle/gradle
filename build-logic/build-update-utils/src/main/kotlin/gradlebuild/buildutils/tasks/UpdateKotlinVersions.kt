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

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.jetbrains.annotations.VisibleForTesting
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory


/**
 * Fetch the latest Kotlin versions and write a properties file.
 * Never up-to-date, non-cacheable.
 */
@UntrackedTask(because = "Not worth tracking")
abstract class UpdateKotlinVersions : DefaultTask() {

    @get:Internal
    abstract val comment: Property<String>

    @get:Internal
    abstract val minimumSupported: Property<String>

    @get:Internal
    abstract val propertiesFile: RegularFileProperty

    @get:Internal
    abstract val compatibilityDocFile: RegularFileProperty

    @TaskAction
    fun action() =
        fetchLatestKotlinVersions().let { latestKotlinVersions ->
            updateProperties(latestKotlinVersions)
            updateCompatibilityDoc(latestKotlinVersions)
        }

    private
    fun fetchLatestKotlinVersions() =
        DocumentBuilderFactory.newInstance().fetchFirstAndLatestsOfEachMinor(
            minimumSupported.get(),
            "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/maven-metadata.xml"
        )

    private
    fun updateProperties(latestKotlinVersions: List<String>) =
        Properties().run {
            setProperty("latests", latestKotlinVersions.joinToString(","))
            store(
                propertiesFile.get().asFile,
                comment.get()
            )
        }

    private
    fun updateCompatibilityDoc(latestKotlinVersions: List<String>) {
        val docFile = compatibilityDocFile.get().asFile
        val linePrefix = "Gradle is tested with Kotlin"
        var lineFound = false
        docFile.writeText(
            docFile.readLines().joinToString(separator = "\n", postfix = "\n") { line ->
                if (line.startsWith(linePrefix)) {
                    lineFound = true
                    "$linePrefix ${latestKotlinVersions.first()} through ${latestKotlinVersions.last()}."
                } else {
                    line
                }
            }
        )
        require(lineFound) {
            "File '$docFile' does not contain the expected Kotlin compatibility line"
        }
    }

    private
    fun DocumentBuilderFactory.fetchFirstAndLatestsOfEachMinor(minimumSupported: String, mavenMetadataUrl: String): List<String> {
        return selectVersionsFrom(minimumSupported, fetchVersionsFromMavenMetadata(mavenMetadataUrl))
    }

    companion object {
        @VisibleForTesting
        @JvmStatic
        fun selectVersionsFrom(minimumSupported: String, allVersions: List<String>): List<String> {
            val versionsByMinor = allVersions
                .groupBy { it.take(3) } // e.g. 1.9
                .toSortedMap()
            val latests = buildList {
                versionsByMinor.entries.forEachIndexed { idx, entry ->
                    // Earliest stable of the minor
                    val versionsOfMinor = entry.value
                    add(versionsOfMinor.lastOrNull { !it.contains("-") })
                    if (idx < versionsByMinor.size - 1) {
                        // Latest of the previous minor
                        add(versionsOfMinor.first())
                    } else {
                        // Current minor
                        val versionsByPatch = versionsOfMinor
                            .groupBy { it.take(5) } // e.g. 1.9.2(x)
                            .toSortedMap()
                        for (key in versionsByPatch.keys.reversed()) {
                            val versionsOfPatch = versionsByPatch.getValue(key)
                            if (versionsOfPatch.any { !it.contains("-") }) {
                                add(versionsOfPatch.first { !it.contains("-") })
                                break
                            }
                            if (versionsOfPatch.any { it.contains("-RC") }) {
                                add(versionsOfPatch.firstOrNull { it.contains("-RC") })
                            } else if (versionsOfPatch.any { it.contains("-Beta") }) {
                                add(versionsOfPatch.firstOrNull { it.contains("-Beta") })
                            }
                        }
                    }
                }
                add(minimumSupported)
            }.filterNotNull().distinct().sorted()
            return latests.subList(latests.indexOf(minimumSupported), latests.size)
        }
    }
}
