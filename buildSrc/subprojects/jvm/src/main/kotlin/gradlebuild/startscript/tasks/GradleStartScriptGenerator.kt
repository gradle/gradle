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

package gradlebuild.startscript.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.internal.plugins.StartScriptGenerator
import org.gradle.api.logging.LogLevel
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction


@CacheableTask
abstract class GradleStartScriptGenerator : DefaultTask() {

    @get:Internal
    abstract val launcherJar: ConfigurableFileCollection

    @get:Input
    val launcherJarName: String
        get() = launcherJar.singleFile.name

    @get:OutputDirectory
    abstract val startScriptsDir: DirectoryProperty

    @TaskAction
    fun generate() {
        logging.captureStandardOutput(LogLevel.INFO)
        val generator = StartScriptGenerator()
        generator.setApplicationName("Gradle")
        generator.setOptsEnvironmentVar("GRADLE_OPTS")
        generator.setExitEnvironmentVar("GRADLE_EXIT_CONSOLE")
        generator.setMainClassName("org.gradle.launcher.GradleMain")
        generator.setScriptRelPath("bin/gradle")
        generator.setClasspath(listOf("lib/$launcherJarName"))
        generator.setAppNameSystemProperty("org.gradle.appname")
        generator.setDefaultJvmOpts(listOf("-Xmx64m", "-Xms64m"))
        generator.generateUnixScript(startScriptsDir.file("gradle").get().asFile)
        generator.generateWindowsScript(startScriptsDir.file("gradle.bat").get().asFile)
    }
}
