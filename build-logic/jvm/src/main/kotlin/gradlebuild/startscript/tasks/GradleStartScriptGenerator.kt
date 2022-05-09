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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.internal.plugins.DefaultTemplateBasedStartScriptGenerator
import org.gradle.api.internal.plugins.StartScriptGenerator
import org.gradle.api.internal.plugins.StartScriptTemplateBindingFactory
import org.gradle.api.internal.resources.FileCollectionBackedTextResource
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.util.internal.TextUtil
import java.nio.charset.StandardCharsets
import javax.inject.Inject


@CacheableTask
abstract class GradleStartScriptGenerator : DefaultTask() {

    @get:Internal
    abstract val launcherJar: ConfigurableFileCollection

    @get:Input
    val launcherJarName: String
        get() = launcherJar.singleFile.name

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val unixScriptTemplate: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val windowsScriptTemplate: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val startScriptsDir: DirectoryProperty

    @get:Inject
    internal
    abstract val temporaryFileProvider: TemporaryFileProvider

    @TaskAction
    fun generate() {
        logging.captureStandardOutput(LogLevel.INFO)
        val generator = StartScriptGenerator(createUnixStartScriptGenerator(), createWindowsStartScriptGenerator())
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

    private
    fun createUnixStartScriptGenerator() = DefaultTemplateBasedStartScriptGenerator(
        TextUtil.getUnixLineSeparator(),
        StartScriptTemplateBindingFactory.unix(),
        FileCollectionBackedTextResource(temporaryFileProvider, unixScriptTemplate, StandardCharsets.UTF_8)
    )

    private
    fun createWindowsStartScriptGenerator() = DefaultTemplateBasedStartScriptGenerator(
        TextUtil.getWindowsLineSeparator(),
        StartScriptTemplateBindingFactory.windows(),
        FileCollectionBackedTextResource(temporaryFileProvider, windowsScriptTemplate, StandardCharsets.UTF_8)
    )
}
