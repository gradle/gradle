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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.writeLines


/**
 * Fetch the latest Kotlin version and updates embedded Kotlin version in all subprojects and build logic.
 * Never up-to-date, non-cacheable.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class UpdateKotlinEmbeddedVersion : DefaultTask() {

    companion object {
        val SUPPORTED_FILE_FORMATS = setOf(".java", ".groovy", ".kt", ".kts", ".gradle", ".properties", ".adoc")
        val IGNORED_FOLDERS = setOf("build", ".git", ".idea", ".gradle")
        const val EXTERNAL_MODULES_PATH = "build-logic/dependency-modules/src/main/kotlin/gradlebuild/modules/extension/ExternalModulesExtension.kt"
        val KOTLIN_EMBEDDED_VERSION_REGEX = Regex("val\\s*kotlinVersion\\s*=\\s*\"([-0-9.a-zA-Z]+)\"")
        enum class LineChange {
            UNCHANGED,
            CHANGED
        }
    }

    @get:Internal
    abstract val nextVersion: Property<String>

    @get:Internal
    abstract val ignorePathsThatContain: SetProperty<String>

    @get:Internal
    abstract val ignoreLinesThatContain: SetProperty<String>

    @get:Internal
    abstract val rootDir: DirectoryProperty

    @TaskAction
    fun action() {
        val currentVersion = getCurrentVersion()
        val nextVersion = getNextVersion()
        getSubprojectsAndBuildLogicFolders()
            .flatMap { it.findFilesForUpdate() }
            .forEach { it.updateKotlinVersion(currentVersion, nextVersion) }
    }

    private
    fun getCurrentVersion(): String {
        val externalModules = rootDir.file(EXTERNAL_MODULES_PATH).get().asFile
        return KOTLIN_EMBEDDED_VERSION_REGEX.find(externalModules.readText())?.groupValues?.get(1)
            ?: throw IllegalStateException("Could not find current Kotlin version in $externalModules")
    }

    private
    fun getNextVersion(): String {
        if (nextVersion.isPresent) {
            return nextVersion.get()
        }
        val dbf = DocumentBuilderFactory.newInstance()
        return dbf.fetchLatest("https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/maven-metadata.xml")
    }

    private
    fun DocumentBuilderFactory.fetchLatest(mavenMetadataUrl: String): String {
        return fetchVersionsFromMavenMetadata(mavenMetadataUrl)
            .groupBy { it.split("-").first() }
            .map { (_, versions) -> versions.first() }.maxOf { it }
    }

    private
    fun getSubprojectsAndBuildLogicFolders(): List<File> {
        return rootDir.get().asFile.listFiles().orEmpty().filter {
            it.name.startsWith("subprojects") || it.name.startsWith("build-logic")
        }.toList()
    }

    private
    fun File.findFilesForUpdate(): List<File> {
        return this.walk(direction = FileWalkDirection.TOP_DOWN).onEnter {
            !IGNORED_FOLDERS.contains(it.name)
        }
            .filter { it.isFile }
            .filter { f ->
                SUPPORTED_FILE_FORMATS.any { f.name.endsWith(it) } && ignorePathsThatContain.get().none { f.path.contains(it) }
            }.toList()
    }

    private
    fun File.updateKotlinVersion(currentVersion: String, nextVersion: String) {
        val content = this.readLines().map { line ->
            if (line.contains(currentVersion) && ignoreLinesThatContain.get().none { line.contains(it) }) {
                LineChange.CHANGED to line.replace(currentVersion, nextVersion)
            } else {
                LineChange.UNCHANGED to line
            }
        }
        if (content.any { it.first == LineChange.CHANGED }) {
            this.toPath().writeLines(content.map { it.second })
        }
    }
}
