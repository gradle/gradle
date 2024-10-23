/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

private const val VOLUME_NAME = "AndroidStudioForGradle"

@DisableCachingByDefault(because = "Not worth caching")
abstract class ExtractAndroidStudioTask @Inject constructor(
    private val execOps: ExecOperations,
    private val fsOps: FileSystemOperations,
    private val fileOps: FileOperations
) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val androidStudioRuntime: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun extract() {
        val androidStudioDistribution = androidStudioRuntime.singleFile
        when {
            androidStudioDistribution.name.endsWith(".dmg") -> extractDmg(androidStudioDistribution)
            else -> extractZipOrTar(androidStudioDistribution)
        }
    }

    fun extractZipOrTar(androidStudioDistribution: File) {
        fsOps.copy {
            val src = when {
                androidStudioDistribution.name.endsWith(".tar.gz") -> fileOps.tarTree(androidStudioDistribution)
                else -> fileOps.zipTree(androidStudioDistribution)
            }

            from(src) {
                eachFile {
                    // Remove top folder when unzipping, that way we get rid of Android Studio.app folder that can cause issues on Mac
                    // where MacOS would kill the Android Studio process right after start, issue: https://github.com/gradle/gradle-profiler/issues/469
                    @Suppress("SpreadOperator")
                    relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
                }
            }

            into(project.layout.buildDirectory.dir(ANDROID_STUDIO_INSTALL_PATH))
        }
    }

    fun extractDmg(androidStudioDistribution: File) {
        val volumeDir = "/Volumes/$VOLUME_NAME"
        val srcDir = "/Volumes/$VOLUME_NAME/Android Studio.app"
        require(!File(srcDir).exists()) {
            "The directory $srcDir already exists. Please unmount it via `hdiutil detach $volumeDir`."
        }

        try {
            execOps.exec {
                commandLine("hdiutil", "attach", androidStudioDistribution.absolutePath, "-mountpoint", volumeDir)
            }

            outputDir.get().asFile.mkdirs()
            execOps.exec {
                commandLine("cp", "-r", "$volumeDir/Android Studio.app/Contents", outputDir.get().asFile.absolutePath)
            }
        } finally {
            execOps.exec {
                commandLine("hdiutil", "detach", volumeDir)
            }
        }
    }
}
