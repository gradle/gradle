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

package gradlebuild.buildutils.tasks

import com.google.gson.Gson
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.net.URI

/**
 * Fetches the latest released versions of Android Studio and IntelliJ IDEA,
 * updates `smoke-tested-ides.properties` and the verification-metadata.xml checksums.
 */
@UntrackedTask(because = "Not worth tracking")
abstract class UpdateSmokeTestedIdeVersions : AbstractVersionsUpdateTask() {

    @get:Internal
    abstract val verificationMetadataFile: RegularFileProperty

    @TaskAction
    fun update() {
        val androidStudio = fetchLatestAndroidStudioRelease()
        val intellij = fetchLatestIntelliJIdeaRelease()

        updateProperties {
            setProperty("android.studio", androidStudio.version)
            setProperty("intellij.idea", intellij.version)
        }

        updateVerificationMetadata(androidStudio, intellij)
    }

    private
    fun fetchLatestAndroidStudioRelease(): IdeRelease {
        val releaseItem = createSecureDocumentBuilder()
            .parse(ANDROID_STUDIO_RELEASES_URL)
            .getElementsByTagName("item")
            .asSequence()
            .firstOrNull { it.text("channel") in setOf("Release", "Patch") }
            ?: error("No Release/Patch channel item found in Android Studio releases XML")

        val version = releaseItem.text("version")
        val artifacts = releaseItem
            .getElementsByTagName("download")
            .asSequence()
            .map { it.text("link").substringAfterLast('/') to it.text("checksum") }
            .filter { (filename, _) -> AS_PLATFORM_SUFFIXES.any { filename.endsWith(it) } }
            .map { (filename, sha) ->
                // Ivy artifact name includes the version: android-studio-{version}-{rest of CDN filename}
                ArtifactChecksum("android-studio-$version-${filename.removePrefix("android-studio-")}", sha)
            }
            .toList()

        require(artifacts.size == AS_PLATFORM_SUFFIXES.size) {
            "Expected ${AS_PLATFORM_SUFFIXES.size} platform artifacts but got ${artifacts.size}"
        }

        return IdeRelease("com.google.android.studio", "android-studio", version, artifacts)
    }

    private
    fun fetchLatestIntelliJIdeaRelease(): IdeRelease {
        val version = Gson()
            .fromJson(URI(INTELLIJ_PRODUCTS_URL).toURL().readText(), Array<IntelliJProduct>::class.java)
            .firstOrNull()?.releases?.firstOrNull { it.type == "release" }?.version
            ?: error("No release version found for IntelliJ IDEA")

        val artifacts = IJ_PLATFORM_SUFFIXES.map { suffix ->
            val fileName = "ideaIU-$version$suffix"
            val sha = URI("$INTELLIJ_DOWNLOAD_URL/$fileName.sha256").toURL().readText().trim().substringBefore(' ')
            ArtifactChecksum(fileName, sha)
        }

        return IdeRelease("idea", "ideaIU", version, artifacts)
    }

    private
    fun updateVerificationMetadata(vararg releases: IdeRelease) {
        val file = verificationMetadataFile.get().asFile
        val lines = file.readLines().toMutableList()

        for (release in releases.sortedByDescending { it.group }) {
            removeComponent(lines, release.group, release.name)
            insertComponent(lines, release)
        }

        file.writeText(lines.joinToString("\n", postfix = "\n"))
    }

    private
    fun removeComponent(lines: MutableList<String>, group: String, name: String) {
        val marker = """<component group="$group" name="$name""""
        val start = lines.indexOfFirst { it.contains(marker) }.takeIf { it >= 0 } ?: return
        val end = start + lines.drop(start).indexOfFirst { it.contains("</component>") }
        lines.subList(start, end + 1).clear()
    }

    private
    fun insertComponent(lines: MutableList<String>, release: IdeRelease) {
        val insertIdx = lines
            .indexOfFirst { line ->
                line.contains("<component group=\"") && line.substringAfter("group=\"").substringBefore("\"") > release.group
            }.takeIf { it >= 0 }
            ?: lines.indexOfFirst { it.contains("</components>") }

        require(insertIdx >= 0) { "Could not find </components> in verification-metadata.xml" }

        val block = buildComponentXml(release)
        lines.addAll(insertIdx, block)
    }

    private
    fun buildComponentXml(release: IdeRelease): List<String> = buildList {
        add("""      <component group="${release.group}" name="${release.name}" version="${release.version}">""")
        release.artifacts.sortedBy { it.fileName }.forEach { (fileName, sha256) ->
            add("""         <artifact name="$fileName">""")
            add("""            <sha256 value="$sha256" origin="Generated by UpdateSmokeTestedIdeVersions" reason="Artifact is not signed"/>""")
            add("""         </artifact>""")
        }
        add("""      </component>""")
    }

    private data class ArtifactChecksum(
        val fileName: String,
        val sha256: String,
    )

    private data class IdeRelease(
        val group: String,
        val name: String,
        val version: String,
        val artifacts: List<ArtifactChecksum>,
    )

    private data class IntelliJProduct(
        val code: String,
        val releases: List<IntelliJProductRelease>,
    )

    private data class IntelliJProductRelease(
        val type: String,
        val version: String,
    )

    companion object {
        private const val ANDROID_STUDIO_RELEASES_URL = "https://jb.gg/android-studio-releases-list.xml"
        private const val INTELLIJ_PRODUCTS_URL = "https://data.services.jetbrains.com/products?code=IU&fields=code,releases.type,releases.version"
        private const val INTELLIJ_DOWNLOAD_URL = "https://download.jetbrains.com/idea"

        private val AS_PLATFORM_SUFFIXES = setOf(
            "linux.tar.gz",
            "mac.dmg",
            "mac_arm.dmg",
            "windows.zip",
        )

        private val IJ_PLATFORM_SUFFIXES = listOf(
            ".tar.gz",
            "-aarch64.tar.gz",
            ".dmg",
            "-aarch64.dmg",
            ".win.zip",
        )

        private fun NodeList.asSequence(): Sequence<Element> =
            (0 until length).asSequence().map { item(it) as Element }

        private fun Element.text(tag: String): String =
            getElementsByTagName(tag).item(0).textContent
    }
}
