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

package gradlebuild.integrationtests.ide

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes.ArtifactType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformRepositoriesExtension
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform
import org.jetbrains.intellij.platform.gradle.providers.AndroidStudioDownloadLinkValueSource
import org.jetbrains.intellij.platform.gradle.services.ExtractorService
import java.io.File
import java.nio.file.Files
import java.util.Properties

abstract class IdeProvisioningExtension {
    abstract val androidStudioInstallPath: DirectoryProperty
}

class IdeProvisioningPlugin : Plugin<Project> {

    companion object {
        private const val VERSIONS_FILE = "gradle/dependency-management/smoke-tested-ides.properties"
        private const val ANDROID_STUDIO_VERSION_KEY = "android.studio"
        private const val INTELLIJ_IDEA_VERSION_KEY = "intellij.idea"
        private const val ANDROID_STUDIO_ARCHIVE_CONFIGURATION = "androidStudioArchive"
        private const val INTELLIJ_IDEA_ARCHIVE_CONFIGURATION = "intellijIdeaArchive"

        fun ideArchivesProvider(project: Project): IdeArchivesProvider = project.objects.newInstance<IdeArchivesProvider>().apply {
            androidStudioArchive.from(project.configurations.named(ANDROID_STUDIO_ARCHIVE_CONFIGURATION))
            intellijIdeaArchive.from(project.configurations.named(INTELLIJ_IDEA_ARCHIVE_CONFIGURATION))
        }

        private fun loadProperties(file: File): Properties {
            require(file.exists()) { "IDE versions file not found: $file" }
            return Properties().apply {
                file.inputStream().use { load(it) }
            }
        }
    }

    override fun apply(target: Project) {
        with(target) {
            val versions = loadProperties(rootDir.resolve(VERSIONS_FILE))
            val androidStudioVersion = versions.requireKey(ANDROID_STUDIO_VERSION_KEY)
            val intellijIdeaVersion = versions.requireKey(INTELLIJ_IDEA_VERSION_KEY)

            IntelliJPlatformRepositoriesExtension.register(project, repositories)
            repositories.intellijPlatform {
                androidStudioInstallers()
                jetbrainsIdeInstallers()
            }

            val androidStudioArchive = configurations.register(ANDROID_STUDIO_ARCHIVE_CONFIGURATION) { isCanBeConsumed = false }
            val intellijIdeaArchive = configurations.register(INTELLIJ_IDEA_ARCHIVE_CONFIGURATION) { isCanBeConsumed = false }

            dependencies {
                add(androidStudioArchive.name, androidStudioDependencyCoordinates(androidStudioVersion))
                add(intellijIdeaArchive.name, intellijIdeaInstallerCoordinates(intellijIdeaVersion))
            }

            val extractor = gradle.sharedServices.registerIfAbsent("intellijExtractorService", ExtractorService::class.java) {}

            val extractAndroidStudio = tasks.register<ExtractIde>("extractAndroidStudio") {
                service.set(extractor)
                archive.from(androidStudioArchive)
                targetDir.set(layout.buildDirectory.dir("android-studio"))
            }

            extensions.create<IdeProvisioningExtension>("ideProvisioning").apply {
                androidStudioInstallPath.set(extractAndroidStudio.flatMap { it.targetDir })
            }
        }
    }

    private fun Project.androidStudioDependencyCoordinates(asVersion: String): Provider<String> {
        val installer = IntelliJPlatformType.AndroidStudio.installer!!
        val downloadLink = providers.of(AndroidStudioDownloadLinkValueSource::class) {
            parameters {
                androidStudioUrl.set("https://jb.gg/android-studio-releases-list.xml")
                androidStudioVersion.set(asVersion)
            }
        }
        return downloadLink.map { link ->
            val parts = link.split('/')
            val fileName = parts.last()
            val downloadVersion = parts[parts.size - 2]
            val (classifier, extension) = fileName
                .substringAfter("${installer.artifactId}-")
                .substringAfter("$downloadVersion-")
                .split(".", limit = 2)
            "${installer.groupId}:${installer.artifactId}:$downloadVersion:$classifier@$extension"
        }
    }

    private fun intellijIdeaInstallerCoordinates(version: String): String {
        val installer = IntelliJPlatformType.IntellijIdeaUltimate.installer!!
        val os = OperatingSystem.current()
        val arch = System.getProperty("os.arch").takeIf { it == "aarch64" }
        val (extension, classifier) = when {
            os.isWindows -> ArtifactType.ZIP to "win"
            os.isLinux -> ArtifactType.TAR_GZ to arch
            os.isMacOsX -> ArtifactType.DMG to arch
            else -> error("Unsupported operating system for IntelliJ IDEA installer: $os")
        }
        val classifierPart = classifier?.let { ":$it" } ?: ""
        return "${installer.groupId}:${installer.artifactId}:$version$classifierPart@$extension"
    }

    private fun Properties.requireKey(key: String): String =
        getProperty(key) ?: error("Missing '$key' in $VERSIONS_FILE")
}

@DisableCachingByDefault(because = "Not worth caching")
abstract class ExtractIde : DefaultTask() {
    @get:Internal
    abstract val service: Property<ExtractorService>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archive: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val targetDir: DirectoryProperty

    @TaskAction
    fun run() {
        val target = targetDir.get().asFile.toPath()
        target.toFile().deleteRecursively()
        Files.createDirectories(target)
        service.get().extract(archive.singleFile.toPath(), target)
    }
}

abstract class IdeArchivesProvider : CommandLineArgumentProvider {
    @get: InputFiles
    @get: PathSensitive(PathSensitivity.NONE)
    abstract val androidStudioArchive: ConfigurableFileCollection

    @get: InputFiles
    @get: PathSensitive(PathSensitivity.NONE)
    abstract val intellijIdeaArchive: ConfigurableFileCollection

    override fun asArguments(): Iterable<String> = listOf(
        "-Dandroid.studio.archive=${androidStudioArchive.singleFile.absolutePath}",
        "-Dintellij.idea.archive=${intellijIdeaArchive.singleFile.absolutePath}"
    )
}
