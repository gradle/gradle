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
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject


@CacheableTask
abstract class GradleStartScriptGenerator : DefaultTask() {

    @get:Internal
    abstract val launcherJar: ConfigurableFileCollection

    @get:Input
    val launcherJarName: String
        get() = launcherJar.singleFile.name

    @get:Internal
    abstract val agentJars: ConfigurableFileCollection

    @get:Input
    val agentJarNames: List<String>
        get() = agentJars.files.map { it.name }

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

        val unixScriptFile = startScriptsDir.file("gradle").get().asFile
        generator.generateUnixScript(unixScriptFile)
        unixScriptFile.injectAgentOptions(TextUtil.getUnixLineSeparator())

        val windowsScriptFile = startScriptsDir.file("gradle.bat").get().asFile
        generator.generateWindowsScript(windowsScriptFile)
        windowsScriptFile.injectAgentOptions(TextUtil.getWindowsLineSeparator())
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

    /**
     * Modifies the start script injecting -javaagent flags. The start script file is updated in-place by appending Java agent switches to 'DEFAULT_JVM_OPTS' variable declaration.
     */
    private
    fun File.injectAgentOptions(separator: String) {
        if (agentJarNames.isEmpty()) {
            return
        }
        var replacementsCount = 0
        // readLines eats EOLs, so we need to use postfix to make sure the last line ends with EOL too.
        writeBytes(readLines().joinToString(separator = separator, postfix = separator) { line ->
            when {
                // We assume that the declaration is not empty.
                line.startsWith("DEFAULT_JVM_OPTS='") && line.endsWith('\'') -> {
                    ++replacementsCount
                    // Use shell's string concatenation: '...'"..." glues contents of quoted and double-quoted strings together.
                    // The result would be something like DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'" \"-javaagent:$APP_HOME/lib/agents/foobar.jar\""
                    line + getAgentOptions("\$APP_HOME").joinToString(separator = " ", prefix = "\" ", postfix = "\"") {
                        // Wrap the agent switch in double quotes, as the expanded APP_HOME may contain spaces.
                        // The joined line is enclosed in double quotes too, so double quotes here must be escaped.
                        "\\\"$it\\\""
                    }
                }

                line.startsWith("set DEFAULT_JVM_OPTS=") -> {
                    ++replacementsCount
                    // The result would be something like set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m" "-javaagent:%APP_HOME%/lib/agents/foobar.jar"
                    line + getAgentOptions("%APP_HOME%").joinToString(separator = " ", prefix = " \"", postfix = "\"")
                }

                else -> line
            }
        }.toByteArray(StandardCharsets.UTF_8))
        if (replacementsCount != 1) {
            throw IllegalArgumentException("The script file produced by the default start script doesn't match expected layout")
        }
    }

    private
    fun getAgentOptions(appHomeVar: String) = agentJarNames.map { "-javaagent:$appHomeVar/lib/agents/$it" }
}
