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
import org.gradle.work.DisableCachingByDefault
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory


/**
 * Fetch the latest Kotlin versions and write a properties file.
 * Never up-to-date, non-cacheable.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class UpdateKotlinVersions : DefaultTask() {

    @get:Internal
    abstract val comment: Property<String>

    @get:Internal
    abstract val minimumSupported: Property<String>

    @get:Internal
    abstract val propertiesFile: RegularFileProperty

    @TaskAction
    fun action() {

        val dbf = DocumentBuilderFactory.newInstance()
        val properties = Properties().apply {

            val latests = dbf.fetchFirstAndLatestsOfEachMinor(
                minimumSupported.get(),
                "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/maven-metadata.xml"
            )
            setProperty("latests", latests.joinToString(","))
        }
        properties.store(
            propertiesFile.get().asFile,
            comment.get()
        )
    }

    private
    fun DocumentBuilderFactory.fetchFirstAndLatestsOfEachMinor(minimumSupported: String, mavenMetadataUrl: String): List<String> {
        val versionsByMinor = fetchVersionsFromMavenMetadata(mavenMetadataUrl)
            .groupBy { it.take(3) }
            .toSortedMap()
        val latests = buildList {
            versionsByMinor.entries.forEachIndexed { idx, entry ->
                add(entry.value.lastOrNull { !it.contains("-") })
                if (idx < versionsByMinor.size - 1) {
                    add(entry.value.first())
                } else {
                    add(entry.value.firstOrNull { !it.contains("-") })
                    add(entry.value.first())
                }
            }
            add(minimumSupported)
        }.filterNotNull().distinct().sorted()
        return latests.subList(latests.indexOf(minimumSupported), latests.size)
    }
}
