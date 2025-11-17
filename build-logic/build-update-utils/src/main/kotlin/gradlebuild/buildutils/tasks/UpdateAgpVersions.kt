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

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber
import org.jetbrains.annotations.VisibleForTesting
import org.jsoup.Jsoup

/**
 * Fetch the latest AGP versions and write a properties file.
 * Never up-to-date, non-cacheable.
 *
 * AGP major versions are aligned with Gradle major versions.
 * IOW, AGP X.y officially only supports Gradle X.z.
 *
 * This task leverages that alignment to automatically select which
 * versions of AGP we should test.
 */
@UntrackedTask(because = "Not worth tracking")
abstract class UpdateAgpVersions : AbstractVersionsUpdateTask() {

    @get:Internal
    abstract val currentGradleVersion: Property<GradleVersion>

    @get:Internal
    abstract val minimumSupported: Property<String>

    @get:Internal
    abstract val compatibilityDocFile: RegularFileProperty

    @TaskAction
    fun fetch() =
        fetchLatestAgpVersions().let { fetchedVersions ->
            updateProperties(fetchedVersions)
            updateCompatibilityDoc(fetchedVersions.latests)
        }

    private
    data class FetchedVersions(
        val latests: List<String>,
        val nightlyBuildId: String,
        val nightlyVersion: String,
        val aapt2Versions: List<String>,
        val buildToolsVersion: String
    )

    private
    fun fetchLatestAgpVersions(): FetchedVersions {
        val latests = fetchLatests(
            currentGradleVersion.get(),
            minimumSupported.orNull,
            "https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml"
        )
        val nightlyBuildId = fetchNightlyBuildId(
            "https://androidx.dev/studio/builds"
        )
        val nightlyVersion = fetchNightlyVersion(
            "https://androidx.dev/studio/builds/$nightlyBuildId/artifacts/artifacts/repository/com/android/application/com.android.application.gradle.plugin/maven-metadata.xml"
        )
        val aapt2Versions = fetchAapt2Versions(
            latests.toSet().plus(nightlyVersion),
            "https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/maven-metadata.xml"
        )
        val buildToolsVersion = fetchBuildToolsVersion(
            "https://developer.android.com/tools/releases/build-tools"
        )
        return FetchedVersions(latests, nightlyBuildId, nightlyVersion, aapt2Versions, buildToolsVersion)
    }

    private
    fun updateProperties(fetchedVersions: FetchedVersions) =
        updateProperties {
            setProperty("latests", fetchedVersions.latests.joinToString(","))
            setProperty("nightlyBuildId", fetchedVersions.nightlyBuildId)
            setProperty("nightlyVersion", fetchedVersions.nightlyVersion)
            setProperty("aapt2Versions", fetchedVersions.aapt2Versions.joinToString(","))
            setProperty("buildToolsVersion", fetchedVersions.buildToolsVersion)
        }

    private
    fun updateCompatibilityDoc(latestAgpVersions: List<String>) =
        updateCompatibilityDoc(
            compatibilityDocFile,
            "Gradle is tested with Android Gradle Plugin",
            latestAgpVersions.firstBaseVersion,
            latestAgpVersions.last()
        )

    private
    val List<String>.firstBaseVersion: String
        get() = VersionNumber.parse(first()).minorBaseVersion

    private
    val VersionNumber.minorBaseVersion: String
        get() = "$major.$minor"

    private
    fun fetchLatests(currentGradleVersion: GradleVersion, minimumSupported: String?, mavenMetadataUrl: String): List<String> {
        return selectVersionsFrom(currentGradleVersion, minimumSupported?.let { VersionNumber.parse(it) }, fetchVersionsFromMavenMetadata(mavenMetadataUrl))
    }

    private
    fun fetchAapt2Versions(agpVersions: Set<String>, mavenMetadataUrl: String): List<String> {
        return fetchVersionsFromMavenMetadata(mavenMetadataUrl)
            .filter { version -> version.substringBeforeLast("-") in agpVersions }
            .sortedBy { VersionNumber.parse(it) }
    }

    private
    fun fetchNightlyBuildId(buildListUrl: String): String =
        Jsoup.connect(buildListUrl)
            .get()
            .select("main li a")
            .first()!!
            .text()

    private
    fun fetchNightlyVersion(mavenMetadataUrl: String): String =
        fetchVersionsFromMavenMetadata(mavenMetadataUrl)
            .single()

    private
    fun fetchBuildToolsVersion(buildToolsUrl: String): String =
        Jsoup.connect(buildToolsUrl)
            .get()
            .select("section:has(> h3#kts)")
            .first()
            ?.text()
            ?.lines()
            ?.firstOrNull { it.contains("buildToolsVersion = ") }
            ?.substringAfter("buildToolsVersion = ")
            ?.trim('"', ' ')
            ?: error("Couldn't find buildToolsVersion on $buildToolsUrl")

    companion object {

        @VisibleForTesting
        @JvmStatic
        fun selectVersionsFrom(currentGradleVersion: GradleVersion, minimumSupported: VersionNumber?, allVersions: List<String>): List<String> {
            val allMinorLatests = allVersions.map { version ->
                VersionNumber.parse(version)
            }.sorted().groupBy { version ->
                VersionNumber.version(version.major, version.minor)
            }.map { (_, versions) ->
                versions.last()
            }
            val gradleMajor = VersionNumber.version(currentGradleVersion.majorVersion)
            val minimumFallback = when {

                allMinorLatests.any { it.major >= gradleMajor.major && it.isStable } -> {
                    validateMinimumSupported(minimumSupported, gradleMajor)
                    gradleMajor
                }

                else -> {
                    val gradlePreviousMajor = VersionNumber.version(gradleMajor.major - 1)
                    validateMinimumSupported(minimumSupported, gradlePreviousMajor)
                    allMinorLatests.last { it.major == gradlePreviousMajor.major && it.isStable }
                }
            }
            return allMinorLatests
                .applyMinimumSupported(minimumSupported ?: minimumFallback)
                .map { it.toString() }
        }

        private fun validateMinimumSupported(minimumSupported: VersionNumber?, minimumMinimum: VersionNumber) {
            if (minimumSupported != null) {
                require(minimumSupported >= minimumMinimum) {
                    "minimumSupported must be at least $minimumMinimum, was $minimumSupported"
                }
            }
        }

        private
        val VersionNumber.isStable: Boolean
            get() = qualifier == null

        private
        fun List<VersionNumber>.applyMinimumSupported(minimumSupported: VersionNumber?): List<VersionNumber> =
            when (minimumSupported) {
                null -> this
                else -> filter { it.baseVersion >= minimumSupported }
            }
    }
}
