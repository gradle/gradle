/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.jvm.application.tasks;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.api.Incubating;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.plugins.StartScriptGenerator;
import org.gradle.api.internal.plugins.UnixStartScriptGenerator;
import org.gradle.api.internal.plugins.WindowsStartScriptGenerator;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.application.scripts.ScriptGenerator;
import org.gradle.util.GUtil;

import java.io.File;

/**
 * Creates start scripts for launching JVM applications.
 * <p>
 * Example:
 * <pre autoTested=''>
 * task createStartScripts(type: CreateStartScripts) {
 *   outputDir = file('build/sample')
 *   mainClassName = 'org.gradle.test.Main'
 *   applicationName = 'myApp'
 *   classpath = files('path/to/some.jar')
 * }
 * </pre>
 * <p>
 * Note: the Gradle {@code "application"} plugin adds a pre-configured task of this type named {@code "createStartScripts"}.
 * <p>
 * The task generates separate scripts targeted at Microsoft Windows environments and UNIX-like environments (e.g. Linux, Mac OS X).
 * The actual generation is implemented by the {@link #getWindowsStartScriptGenerator()} and {@link #getUnixStartScriptGenerator()} properties, of type {@link ScriptGenerator}.
 * <p>
 * Example:
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
 * <p>
 * The default generators are of the type {@link org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator}, with default templates.
 * This templates can be changed via the {@link org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator#setTemplate(org.gradle.api.resources.TextResource)} method.
 * <p>
 * The default implementations used by this task use <a href="http://docs.groovy-lang.org/latest/html/documentation/template-engines.html#_simpletemplateengine">Groovy's SimpleTemplateEngine</a>
 * to parse the template, with the following variables available:
 * <p>
 * <ul>
 * <li>{@code applicationName}</li>
 * <li>{@code optsEnvironmentVar}</li>
 * <li>{@code exitEnvironmentVar}</li>
 * <li>{@code mainClassName}</li>
 * <li>{@code defaultJvmOpts}</li>
 * <li>{@code appNameSystemProperty}</li>
 * <li>{@code appHomeRelativePath}</li>
 * <li>{@code classpath}</li>
 * </ul>
 * <p>
 * Example:
 * <p>
 * <pre>
 * task createStartScripts(type: CreateStartScripts) {
 *   unixStartScriptGenerator.template = resources.text.fromFile('customUnixStartScript.txt')
 *   windowsStartScriptGenerator.template = resources.text.fromFile('customWindowsStartScript.txt')
 * }
 * </pre>
 */
public class CreateStartScripts extends ConventionTask {

    private File outputDir;
    private String mainClassName;
    private Iterable<String> defaultJvmOpts = Lists.newLinkedList();
    private String applicationName;
    private String optsEnvironmentVar;
    private String exitEnvironmentVar;
    private FileCollection classpath;
    private ScriptGenerator unixStartScriptGenerator = new UnixStartScriptGenerator();
    private ScriptGenerator windowsStartScriptGenerator = new WindowsStartScriptGenerator();

    /**
     * The environment variable to use to provide additional options to the JVM.
     */
    @Input
    @Optional
    public String getOptsEnvironmentVar() {
        if (GUtil.isTrue(optsEnvironmentVar)) {
            return optsEnvironmentVar;
        }

        if (!GUtil.isTrue(getApplicationName())) {
            return null;
        }

        return GUtil.toConstant(getApplicationName()) + "_OPTS";
    }

    /**
     * The environment variable to use to control exit value (Windows only).
     */
    @Input
    @Optional
    public String getExitEnvironmentVar() {
        if (GUtil.isTrue(exitEnvironmentVar)) {
            return exitEnvironmentVar;
        }

        if (!GUtil.isTrue(getApplicationName())) {
            return null;
        }

        return GUtil.toConstant(getApplicationName()) + "_EXIT_CONSOLE";
    }

    /**
     * Returns the full path to the Unix script. The target directory is represented by the output directory, the file name is the application name without a file extension.
     */
    @Internal
    public File getUnixScript() {
        return new File(getOutputDir(), getApplicationName());
    }

    /**
     * Returns the full path to the Windows script. The target directory is represented by the output directory, the file name is the application name plus the file extension .bat.
     */
    @Internal
    public File getWindowsScript() {
        return new File(getOutputDir(), getApplicationName() + ".bat");
    }

    /**
     * The directory to write the scripts into.
     */
    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * The main classname used to start the Java application.
     */
    @Input
    public String getMainClassName() {
        return mainClassName;
    }

    public void setMainClassName(String mainClassName) {
        this.mainClassName = mainClassName;
    }

    /**
     * The application's default JVM options. Defaults to an empty list.
     */
    @Input
    @Optional
    public Iterable<String> getDefaultJvmOpts() {
        return defaultJvmOpts;
    }

    public void setDefaultJvmOpts(Iterable<String> defaultJvmOpts) {
        this.defaultJvmOpts = defaultJvmOpts;
    }

    /**
     * The application's name.
     */
    @Input
    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public void setOptsEnvironmentVar(String optsEnvironmentVar) {
        this.optsEnvironmentVar = optsEnvironmentVar;
    }

    public void setExitEnvironmentVar(String exitEnvironmentVar) {
        this.exitEnvironmentVar = exitEnvironmentVar;
    }

    /**
     * The class path for the application.
     */
    @Classpath
    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    /**
     * The UNIX-like start script generator.
     * <p>
     * Defaults to an implementation of {@link org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator}.
     */
    @Incubating
    @Internal
    public ScriptGenerator getUnixStartScriptGenerator() {
        return unixStartScriptGenerator;
    }

    public void setUnixStartScriptGenerator(ScriptGenerator unixStartScriptGenerator) {
        this.unixStartScriptGenerator = unixStartScriptGenerator;
    }

    /**
     * The Windows start script generator.
     * <p>
     * Defaults to an implementation of {@link org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator}.
     */
    @Incubating
    @Internal
    public ScriptGenerator getWindowsStartScriptGenerator() {
        return windowsStartScriptGenerator;
    }

    public void setWindowsStartScriptGenerator(ScriptGenerator windowsStartScriptGenerator) {
        this.windowsStartScriptGenerator = windowsStartScriptGenerator;
    }

    @TaskAction
    public void generate() {
        StartScriptGenerator generator = new StartScriptGenerator(unixStartScriptGenerator, windowsStartScriptGenerator);
        generator.setApplicationName(getApplicationName());
        generator.setMainClassName(getMainClassName());
        generator.setDefaultJvmOpts(getDefaultJvmOpts());
        generator.setOptsEnvironmentVar(getOptsEnvironmentVar());
        generator.setExitEnvironmentVar(getExitEnvironmentVar());
        generator.setClasspath(getRelativeClasspath());
        generator.setScriptRelPath("bin/" + getUnixScript().getName());
        generator.generateUnixScript(getUnixScript());
        generator.generateWindowsScript(getWindowsScript());
    }

    @Internal
    private Iterable<String> getRelativeClasspath() {
        return Iterables.transform(getClasspath().getFiles(), new Function<File, String>() {
            @Override
            public String apply(File input) {
                return "lib/" + input.getName();
            }
        });
    }

}
