/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.build

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.internal.plugins.StartScriptGenerator
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@CacheableTask
@CompileStatic
abstract class GradleStartScriptGenerator extends DefaultTask {

    @Internal
    File startScriptsDir

    @Internal
    abstract ConfigurableFileCollection getLauncherBootstrapClasspathFiles()

    @Input
    List<String> getLauncherBootstrapClasspath() {
        return launcherBootstrapClasspathFiles.collect { "lib/${it.name}".toString() }
    }

    @OutputFile
    File getShellScript() {
        return new File(startScriptsDir, "gradle")
    }

    @OutputFile
    File getBatchFile() {
        return new File(startScriptsDir, "gradle.bat")
    }

    @TaskAction
    def generate() {
        logging.captureStandardOutput(LogLevel.INFO)
        def generator = new StartScriptGenerator()
        generator.applicationName = 'Gradle'
        generator.optsEnvironmentVar = 'GRADLE_OPTS'
        generator.exitEnvironmentVar = 'GRADLE_EXIT_CONSOLE'
        generator.mainClassName = 'org.gradle.launcher.GradleMain'
        generator.scriptRelPath = 'bin/gradle'
        generator.classpath = launcherBootstrapClasspath
        generator.appNameSystemProperty = 'org.gradle.appname'
        generator.defaultJvmOpts = ["-Xmx64m", "-Xms64m"]
        generator.generateUnixScript(shellScript)
        generator.generateWindowsScript(batchFile)
    }
}
