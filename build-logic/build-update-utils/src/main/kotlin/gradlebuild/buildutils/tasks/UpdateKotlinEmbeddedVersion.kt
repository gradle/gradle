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
import org.gradle.api.tasks.UntrackedTask
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory


/**
 * Fetches the latest Kotlin version and updates embedded Kotlin version. It also updates the Kotlin version in all folders passed
 * in UpdateKotlinEmbeddedVersion#foldersWithFilesToUpdateKotlinVersion.
 *
 * Never up-to-date, non-cacheable.
 */
@UntrackedTask(because = "Modifies source code")
abstract class UpdateKotlinEmbeddedVersion : DefaultTask() {

    companion object {
        val SUPPORTED_FILE_FORMATS = setOf(".java", ".groovy", ".kt", ".kts", ".gradle", ".properties", ".adoc")
        val IGNORED_FOLDERS = setOf("build", ".git", ".idea", ".gradle")
        val KOTLIN_EMBEDDED_VERSION_REGEX = Regex("val\\s*kotlinVersion\\s*=\\s*\"([-0-9.a-zA-Z]+)\"")
        const val FILE_WITH_EMBEDDED_VERSION = "build-logic/dependency-modules/src/main/kotlin/gradlebuild/modules/extension/ExternalModulesExtension.kt"
    }

    @get:Internal
    abstract val nextVersion: Property<String>

    @get:Internal
    abstract val foldersWithFilesToUpdateKotlinVersion: SetProperty<String>

    @get:Internal
    abstract val ignorePathsThatContains: SetProperty<String>

    @get:Internal
    abstract val rootDir: DirectoryProperty

    @TaskAction
    fun action() {
        val currentVersion = getCurrentVersion()
        val nextVersion = getNextVersion()
        rootDir.file(FILE_WITH_EMBEDDED_VERSION).get().asFile.updateKotlinVersion(currentVersion, nextVersion)
        getAdditionalFolders()
            .flatMap { it.findFilesForUpdate() }
            .forEach { it.updateKotlinVersion(currentVersion, nextVersion) }
    }

    private
    fun getCurrentVersion(): String {
        val fileWithEmbeddedVersion = rootDir.file(FILE_WITH_EMBEDDED_VERSION).get().asFile
        return KOTLIN_EMBEDDED_VERSION_REGEX.find(fileWithEmbeddedVersion.readText())?.groupValues?.get(1)
            ?: throw IllegalStateException("Could not find current Kotlin version in $fileWithEmbeddedVersion")
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
    fun getAdditionalFolders(): Set<File> {
        return foldersWithFilesToUpdateKotlinVersion.get().map {
            rootDir.file(it).get().asFile
        }.toSet()
    }

    private
    fun File.findFilesForUpdate(): List<File> {
        return this.walk(direction = FileWalkDirection.TOP_DOWN).onEnter {
            !IGNORED_FOLDERS.contains(it.name)
        }
            .filter { it.isFile }
            .filter { f -> SUPPORTED_FILE_FORMATS.any { f.name.endsWith(it) } && ignorePathsThatContains.get().none { f.path.contains(it) } }
            .toList()
    }

    private
    fun File.updateKotlinVersion(currentVersion: String, nextVersion: String) {
        val content = this.readText()
        if (content.contains(currentVersion)) {
            this.writeText(content.replace(currentVersion, nextVersion))
        }
    }
}
