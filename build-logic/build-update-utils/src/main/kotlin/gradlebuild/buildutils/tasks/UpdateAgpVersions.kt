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

package gradlebuild.buildutils.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.internal.util.PropertiesUtils
import org.gradle.util.internal.VersionNumber
import org.w3c.dom.Element
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory


/**
 * Fetch the latest AGP versions and write a properties file.
 * Never up-to-date, non-cacheable.
 */
@UntrackedTask(because = "Not worth tracking")
abstract class UpdateAgpVersions : DefaultTask() {

    @get:Internal
    abstract val comment: Property<String>

    @get:Internal
    abstract val minimumSupportedMinor: Property<String>

    @get:Internal
    abstract val fetchNightly: Property<Boolean>

    @get:Internal
    abstract val propertiesFile: RegularFileProperty

    @get:Internal
    abstract val compatibilityDocFile: RegularFileProperty

    @TaskAction
    fun fetch() =
        fetchLatestAgpVersions().let { (latests, nightly) ->
            updateProperties(latests, nightly)
            updateCompatibilityDoc(latests)
        }

    private
    fun fetchLatestAgpVersions(): Pair<List<String>, String?> {
        val dbf = DocumentBuilderFactory.newInstance()
        val latests = dbf.fetchLatests(
            minimumSupportedMinor.get(),
            "https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml"
        )
        val nightly =
            if (fetchNightly.get()) dbf.fetchNightly()
            else null
        return latests to nightly
    }

    private
    fun updateProperties(latests: List<String>, nightly: String?) =
        Properties().run {
            setProperty("latests", latests.joinToString(","))
            if (nightly != null) {
                setProperty("nightly", nightly)
            }
            store(
                propertiesFile.get().asFile,
                comment.get()
            )
        }

    private
    fun updateCompatibilityDoc(latestAgpVersions: List<String>) {
        val docFile = compatibilityDocFile.get().asFile
        val linePrefix = "Gradle is tested with Android Gradle Plugin"
        var lineFound = false
        docFile.writeText(
            docFile.readLines().joinToString(separator = "\n", postfix = "\n") { line ->
                if (line.startsWith(linePrefix)) {
                    lineFound = true
                    "$linePrefix ${latestAgpVersions.firstBaseVersion} through ${latestAgpVersions.lastBaseVersion}."
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
    val List<String>.firstBaseVersion: String
        get() = VersionNumber.parse(first()).minorBaseVersion

    private
    val List<String>.lastBaseVersion: String
        get() = map { VersionNumber.parse(it) }
            .last { it.qualifier == null || it.qualifier?.startsWith("rc") == true }
            .minorBaseVersion

    private
    val VersionNumber.minorBaseVersion: String
        get() = "$major.$minor"

    private
    fun DocumentBuilderFactory.fetchLatests(minimumSupported: String, mavenMetadataUrl: String): List<String> {
        var latests = fetchVersionsFromMavenMetadata(mavenMetadataUrl)
            .groupBy { it.take(3) }
            .map { (_, versions) -> versions.first() }
        latests = (latests + minimumSupported).sorted()
        latests = latests.subList(latests.indexOf(minimumSupported) + 1, latests.size)
        return latests
    }

    private
    fun DocumentBuilderFactory.fetchNightly(): String =
        fetchVersionsFromMavenMetadata("https://repo.gradle.org/gradle/ext-snapshots-local/com/android/tools/build/gradle/maven-metadata.xml")
            .first()
}


internal
fun DocumentBuilderFactory.fetchVersionsFromMavenMetadata(url: String): List<String> =
    newDocumentBuilder()
        .parse(url)
        .getElementsByTagName("version").let { versions ->
            (0 until versions.length)
                .map { idx -> (versions.item(idx) as Element).textContent }
                .reversed()
        }


internal
fun Properties.store(file: java.io.File, comment: String? = null) {
    PropertiesUtils.store(this, file, comment, Charsets.ISO_8859_1, "\n")
}
