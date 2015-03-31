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

import org.gradle.api.Incubating
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.plugins.StartScriptGenerator
import org.gradle.api.internal.plugins.UnixStartScriptGenerator
import org.gradle.api.internal.plugins.WindowsStartScriptGenerator
import org.gradle.api.tasks.*
import org.gradle.jvm.application.scripts.ScriptGenerator
import org.gradle.util.GUtil

/**
 * <p>A {@link org.gradle.api.Task} for creating OS-specific start scripts. It provides default implementations for
 * generating start scripts for Unix and Windows runtime environments.</p>
 *
 * Example:
 *
 * <pre autoTested=''>
 * task createStartScripts(type: CreateStartScripts) {
 *   outputDir = file('build/sample')
 *   mainClassName = 'org.gradle.test.Main'
 *   applicationName = 'myApp'
 *   classpath = files('path/to/some.jar')
 * }
 * </pre>
 * <p>The standard start script generation logic can be changed by assigning custom start script generator classes that implement the interface
 * {@link org.gradle.jvm.application.scripts.ScriptGenerator}. {@code ScriptGenerator} requires you to implement a single method. The parameter of type
 * {@link org.gradle.jvm.application.scripts.JavaAppStartScriptGenerationDetails} represents the data e.g. classpath, application name. The parameter of type
 * {@code java.io.Writer} writes to the target start script file.</p>
 *
 * Example:
 *
 * <pre autoTested=''>
 * task createStartScripts(type: CreateStartScripts) {
 *   unixStartScriptGenerator = new CustomUnixStartScriptGenerator()
 *   windowsStartScriptGenerator = new CustomWindowsStartScriptGenerator()
 * }
 *
 * class CustomUnixStartScriptGenerator implements ScriptGenerator {
 *   void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
 *     // implementation
 *   }
 * }
 *
 * class CustomWindowsStartScriptGenerator implements ScriptGenerator {
 *   void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
 *     // implementation
 *   }
 * }
 * </pre>
 * <p>Providing a custom start script generator is powerful. Sometimes changing the underlying template used for the script generation is good enough.
 * For that purpose the default implementations of {@link org.gradle.jvm.application.scripts.ScriptGenerator} also implement the interface {@link org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator}.
 * The method {@link org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator#setTemplate(org.gradle.api.resources.TextResource)} can be used to provide a custom template. Within the template files
 * the following variables can be used: {@code applicationName}, {@code optsEnvironmentVar}, {@code exitEnvironmentVar}, {@code mainClassName}, {@code defaultJvmOpts}, {@code appNameSystemProperty},
 * {@code appHomeRelativePath} and {@code classpath}.</p>
 *
 * Example:
 *
 * <pre>
 * task createStartScripts(type: CreateStartScripts) {
 *   unixStartScriptGenerator.template = resources.text.fromFile('customUnixStartScript.txt')
 *   windowsStartScriptGenerator.template = resources.text.fromFile('customWindowsStartScript.txt')
 * }
 * </pre>
 */
public class CreateStartScripts extends ConventionTask {
    /**
     * The directory to write the scripts into.
     */
    @OutputDirectory
    File outputDir

    /**
     * The main classname used to start the Java application.
     */
    @Input
    String mainClassName

    /**
     * The application's default JVM options. Defaults to an empty list.
     */
    @Input
    @Optional
    Iterable<String> defaultJvmOpts = []

    /**
     * The application's name.
     */
    @Input
    String applicationName

    /**
     * The environment variable to use to provide additional options to the JVM.
     */
    @Input
    @Optional
    String optsEnvironmentVar

    /**
     * The environment variable to use to control exit value (Windows only).
     */
    @Input
    @Optional
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

    /**
     * Returns the exit environment variable.
     */
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

    /**
     * Returns the full path to the Unix script. The target directory is represented by the output directory,
     * the file name is the application name without a file extension.
     */
    @OutputFile
    File getUnixScript() {
        return new File(getOutputDir(), getApplicationName())
    }

    /**
     * Returns the full path to the Windows script. The target directory is represented by the output directory,
     * the file name is the application name plus the file extension .bat.
     */
    @OutputFile
    File getWindowsScript() {
        return new File(getOutputDir(), "${getApplicationName()}.bat")
    }

    /**
     * The Unix start script generator. Defaults to a standard implementation.
     */
    @Incubating
    ScriptGenerator unixStartScriptGenerator = new UnixStartScriptGenerator()

    /**
     * The Windows start script generator. Defaults to a standard implementation.
     */
    @Incubating
    ScriptGenerator windowsStartScriptGenerator = new WindowsStartScriptGenerator()

    @TaskAction
    void generate() {
        def generator = new StartScriptGenerator(unixStartScriptGenerator, windowsStartScriptGenerator)
        generator.applicationName = getApplicationName()
        generator.mainClassName = getMainClassName()
        generator.defaultJvmOpts = getDefaultJvmOpts()
        generator.optsEnvironmentVar = getOptsEnvironmentVar()
        generator.exitEnvironmentVar = getExitEnvironmentVar()
        generator.classpath = getClasspath().collect { "lib/${it.name}".toString() }
        generator.scriptRelPath = "bin/${getUnixScript().name}".toString()
        generator.generateUnixScript(getUnixScript())
        generator.generateWindowsScript(getWindowsScript())
    }
}
