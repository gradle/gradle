/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.tasks.application

import groovy.text.SimpleTemplateEngine
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GUtil
import org.gradle.util.TextUtil

/**
 * <p>A {@link org.gradle.api.Task} for creating OS dependent start scripts.</p>
 *
 * @author Rene Groeschke
 */
class CreateStartScripts extends ConventionTask {

    /**
     * The directory to write the scripts into.
     */
    File outputDir

    /**
     * The application's main class.
     */
    @Input
    String mainClassName

    /**
     * The application's name.
     */
    @Input
    String applicationName

    String optsEnvironmentVar

    String exitEnvironmentVar

    /**
     * The class path for the application.
     */
    @InputFiles
    FileCollection classpath

    /**
     * Returns the name of the application's OPTS environment variable.
     */
    @Input
    String getOptsEnvironmentVar() {
        if (optsEnvironmentVar) {
            return optsEnvironmentVar
        }
        if (!getApplicationName()) {
            return null
        }
        return "${GUtil.toConstant(getApplicationName())}_OPTS"
    }

    @Input
    String getExitEnvironmentVar() {
        if (exitEnvironmentVar) {
            return exitEnvironmentVar
        }
        if (!getApplicationName()) {
            return null
        }
        return "${GUtil.toConstant(getApplicationName())}_EXIT_CONSOLE"
    }

    @OutputFile
    File getUnixScript() {
        return new File(getOutputDir(), getApplicationName())
    }

    @OutputFile
    File getWindowsScript() {
        return new File(getOutputDir(), "${getApplicationName()}.bat")
    }

    @TaskAction
    void generate() {
        getOutputDir().mkdirs()

        //ref all files in classpath
        def unixLibPath = "\$APP_HOME/lib/"
        def unixClassPath = new StringBuffer()

        def windowsLibPath = "%APP_HOME%\\lib\\"
        def windowsClassPath = new StringBuffer()

        classpath.each {
            unixClassPath << "$unixLibPath${it.name}:"
            windowsClassPath << "$windowsLibPath${it.name};"
        }

        generateUnixScript(
                applicationName: getApplicationName(),
                optsEnvironmentVar: getOptsEnvironmentVar(),
                mainClassName: getMainClassName(),
                classpath: unixClassPath)
        generateWindowsScript(
                applicationName: getApplicationName(),
                optsEnvironmentVar: getOptsEnvironmentVar(),
                exitEnvironmentVar: getExitEnvironmentVar(),
                mainClassName: getMainClassName(),
                classpath: windowsClassPath)
    }

    private void generateUnixScript(Map binding) {
        generateScript('unixStartScript.txt', binding, TextUtil.unixLineSeparator, getUnixScript())
    }

    private void generateWindowsScript(Map binding) {
        generateScript('windowsStartScript.txt', binding, TextUtil.windowsLineSeparator, getWindowsScript())
    }

    private void generateScript(String templateName, Map binding, String lineSeparator, File outputFile) {
        def engine = new SimpleTemplateEngine()
        def templateText = CreateStartScripts.getResourceAsStream(templateName).text
        def output = engine.createTemplate(templateText).make(binding)
        def nativeOutput = TextUtil.convertLineSeparators(output as String, lineSeparator)
        outputFile.write(nativeOutput)
    }
}
