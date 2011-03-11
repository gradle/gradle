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
package org.gradle.api.tasks.application;


import groovy.text.SimpleTemplateEngine
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GUtil

/**
 * <p>A {@link org.gradle.api.Task} for creating OS dependent startScripts.</p>
 *
 * @author Rene Groeschke
 */
class CreateStartScripts extends ConventionTask {

    /**
     * The directory to write the scripts into.
     */
    File outputDir

    /**
     * The application's main class
     */
    @Input
    String mainClassName

    /**
     * The application's name
     */
    @Input
    String applicationName

    String optsEnvironmentVar

    String exitEnvironmentVar

    /**
     * The classpath for the application.
     */
    @InputFiles
    FileCollection classpath;

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
    public File getBatScript() {
        return new File(getOutputDir(), "${getApplicationName()}.bat")
    }

    @OutputFile
    public File getBashScript() {
        return new File(getOutputDir(), getApplicationName())
    }

    @TaskAction
    public void generate() {
        getOutputDir().mkdirs();

        //ref all files in classpath
        def unixLibPath = "\$APP_HOME/lib/"
        StringBuffer unixClasspath = new StringBuffer();

        def windowsLibPath = "%APP_HOME%\\lib\\"
        StringBuffer windowsClasspath = new StringBuffer();

        classpath.each {
            unixClasspath << "$unixLibPath${it.name}:"
            windowsClasspath << "$windowsLibPath${it.name};"
        }

        generateLinuxStartScript([
                applicationName: getApplicationName(),
                optsEnvironmentVar: getOptsEnvironmentVar(),
                mainClassName: getMainClassName(),
                classpath: unixClasspath])
        generateWindowsStartScript([
                applicationName: getApplicationName(),
                optsEnvironmentVar: getOptsEnvironmentVar(),
                exitEnvironmentVar: getExitEnvironmentVar(),
                mainClassName: getMainClassName(),
                classpath: windowsClasspath])
    }

    void generateWindowsStartScript(def binding) {
        def engine = new SimpleTemplateEngine();
        String windowsTemplate = CreateStartScripts.getResourceAsStream('windowsStartScript.txt').text
        String windowsOutput = engine.createTemplate(windowsTemplate).make(binding)

        def windowsScript = getBatScript()
        windowsScript.withWriter {writer ->
            writer.write(transformIntoWindowsNewLines(windowsOutput))
        }
    }

    void generateLinuxStartScript(def binding) {
        def engine = new SimpleTemplateEngine();
        String unixTemplate = CreateStartScripts.getResourceAsStream('unixStartScript.txt').text
        def linuxOutput = engine.createTemplate(unixTemplate).make(binding)

        def unixScript = getBashScript();
        unixScript.withWriter {writer ->
            writer.write(linuxOutput)
        }
    }

    static String transformIntoWindowsNewLines(String s) {
        StringWriter writer = new StringWriter()
        s.toCharArray().each {c ->
            if (c == '\n') {
                writer.write('\r')
                writer.write('\n')
            } else if (c != '\r') {
                writer.write(c)
            }
        }
        writer.toString()
    }
}
