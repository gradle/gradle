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
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

@DisableCachingByDefault(because = "Not worth caching")
abstract class ExtractIdeTask @Inject constructor(
    private val execOps: ExecOperations,
    private val fsOps: FileSystemOperations,
    private val fileOps: FileOperations
) : DefaultTask() {
    @get:Input
    abstract val dmgAppName: Property<String>

    @get:Input
    abstract val stripTopLevelDirectory: Property<Boolean>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val ideDistribution: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun extract() {
        val distributionFile = ideDistribution.singleFile
        when {
            distributionFile.name.endsWith(".dmg") -> extractDmg(distributionFile)
            else -> extractZipOrTar(distributionFile)
        }
    }

    fun extractZipOrTar(distribution: File) {
        fsOps.copy {
            val src = when {
                distribution.name.endsWith(".tar.gz") -> fileOps.tarTree(distribution)
                else -> fileOps.zipTree(distribution)
            }

            from(src) {
                if (stripTopLevelDirectory.get()) {
                    eachFile {
                        // Remove top folder when unzipping, that way we get rid of .app folder that can cause issues on Mac
                        // where MacOS would kill the process right after start, issue: https://github.com/gradle/gradle-profiler/issues/469
                        val newSegments = relativePath.segments.drop(1).toTypedArray()
                        if (newSegments.isEmpty()) {
                            exclude()
                        } else {
                            @Suppress("SpreadOperator")
                            relativePath = RelativePath(true, *newSegments)
                        }
                    }
                }
            }
            includeEmptyDirs = false

            into(outputDir)
        }
    }

    fun extractDmg(distribution: File) {
        val appName = dmgAppName.get()
        val volume = appName.replace(" ", "")
        val volumeDir = "/Volumes/$volume"
        val srcDir = "/Volumes/$volume/$appName"
        // A previous build may have been interrupted before the finally block could detach the volume.
        // Detach it automatically so the task can proceed without requiring manual intervention.
        if (File(srcDir).exists()) {
            execOps.exec {
                commandLine("hdiutil", "detach", volumeDir)
            }
        }

        try {
            execOps.exec {
                commandLine("hdiutil", "attach", distribution.absolutePath, "-mountpoint", volumeDir)
            }

            outputDir.get().asFile.mkdirs()
            val unpackResult = execOps.exec {
                commandLine("cp", "-r", "$volumeDir/$appName/Contents", outputDir.get().asFile.absolutePath)
                isIgnoreExitValue = true
            }
            if (unpackResult.exitValue != 0) {
                execOps.exec {
                    commandLine("ls", "-lF", volumeDir)
                }
                unpackResult.assertNormalExitValue()
            }
        } finally {
            execOps.exec {
                commandLine("hdiutil", "detach", volumeDir)
            }
        }
    }
}
