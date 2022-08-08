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

import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.plugins.StartScriptGenerator;
import org.gradle.api.internal.plugins.UnixStartScriptGenerator;
import org.gradle.api.internal.plugins.WindowsStartScriptGenerator;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.jvm.DefaultModularitySpec;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.jvm.application.scripts.ScriptGenerator;
import org.gradle.util.internal.GUtil;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Creates start scripts for launching JVM applications.
 * <p>
 * Example:
 * <pre class='autoTested'>
 * task createStartScripts(type: CreateStartScripts) {
 *   outputDir = file('build/sample')
 *   mainClass = 'org.gradle.test.Main'
 *   applicationName = 'myApp'
 *   classpath = files('path/to/some.jar')
 * }
 * </pre>
 * <p>
 * Note: the Gradle {@code "application"} plugin adds a pre-configured task of this type named {@code "startScripts"}.
 * <p>
 * The task generates separate scripts targeted at Microsoft Windows environments and UNIX-like environments (e.g. Linux, macOS).
 * The actual generation is implemented by the {@link #getWindowsStartScriptGenerator()} and {@link #getUnixStartScriptGenerator()} properties, of type {@link ScriptGenerator}.
 * <p>
 * Example:
 * <pre class='autoTested'>
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
 * The default implementations used by this task use <a href="https://docs.groovy-lang.org/latest/html/documentation/template-engines.html#_simpletemplateengine">Groovy's SimpleTemplateEngine</a>
 * to parse the template, with the following variables available:
 *
 * <ul>
 * <li>{@code applicationName}</li>
 * <li>{@code optsEnvironmentVar}</li>
 * <li>{@code exitEnvironmentVar}</li>
 * <li>{@code mainModule}</li>
 * <li>{@code mainClass}</li>
 * <li>{@code executableDir}</li>
 * <li>{@code defaultJvmOpts}</li>
 * <li>{@code appNameSystemProperty}</li>
 * <li>{@code appHomeRelativePath}</li>
 * <li>{@code classpath}</li>
 * </ul>
 * <p>
 * Example:
 * <pre>
 * task createStartScripts(type: CreateStartScripts) {
 *   unixStartScriptGenerator.template = resources.text.fromFile('customUnixStartScript.txt')
 *   windowsStartScriptGenerator.template = resources.text.fromFile('customWindowsStartScript.txt')
 * }
 * </pre>
 */
@DisableCachingByDefault(because = "Not worth caching")
public class CreateStartScripts extends ConventionTask {

    private File outputDir;
    private String executableDir = "bin";
    private final Property<String> mainModule;
    private final Property<String> mainClass;
    private Iterable<String> defaultJvmOpts = Lists.newLinkedList();
    private String applicationName;
    private String optsEnvironmentVar;
    private String exitEnvironmentVar;
    private FileCollection classpath;
    private final ModularitySpec modularity;
    private ScriptGenerator unixStartScriptGenerator = new UnixStartScriptGenerator();
    private ScriptGenerator windowsStartScriptGenerator = new WindowsStartScriptGenerator();

    public CreateStartScripts() {
        this.mainModule = getObjectFactory().property(String.class);
        this.mainClass = getObjectFactory().property(String.class);
        this.modularity = getObjectFactory().newInstance(DefaultModularitySpec.class);
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected JavaModuleDetector getJavaModuleDetector() {
        throw new UnsupportedOperationException();
    }

    /**
     * The environment variable to use to provide additional options to the JVM.
     */
    @Nullable
    @Optional
    @Input
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
    @Nullable
    @Optional
    @Input
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
    @Nullable
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(@Nullable File outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * The directory to write the scripts into in the distribution.
     * @since 4.5
     */
    @Input
    public String getExecutableDir() {
        return executableDir;
    }

    /**
     * The directory to write the scripts into in the distribution.
     * @since 4.5
     */
    public void setExecutableDir(String executableDir) {
        this.executableDir = executableDir;
    }

    /**
     * The main module name used to start the modular Java application.
     *
     * @since 6.4
     */
    @Optional
    @Input
    public Property<String> getMainModule() {
        return mainModule;
    }

    /**
     * The main class name used to start the Java application.
     *
     * Use this property instead of {@link #getMainClassName()} and {@link #setMainClassName(String)}.
     *
     * @since 6.4
     */
    @Optional
    @Input
    public Property<String> getMainClass() {
        return mainClass;
    }

    /**
     * The main class name used to start the Java application.
     */
    @ReplacedBy("mainClass")
    @Nullable
    @Deprecated
    public String getMainClassName() {
        DeprecationLogger.deprecateProperty(CreateStartScripts.class, "mainClassName")
            .replaceWith("mainClass")
            .willBeRemovedInGradle8()
            .withDslReference()
            .nagUser();

        return mainClass.getOrNull();
    }

    @Deprecated
    public void setMainClassName(@Nullable String mainClassName) {
        DeprecationLogger.deprecateProperty(CreateStartScripts.class, "mainClassName")
            .replaceWith("mainClass")
            .willBeRemovedInGradle8()
            .withDslReference()
            .nagUser();

        this.mainClass.set(mainClassName);
    }

    /**
     * The application's default JVM options. Defaults to an empty list.
     */
    @Nullable
    @Optional
    @Input
    public Iterable<String> getDefaultJvmOpts() {
        return defaultJvmOpts;
    }

    public void setDefaultJvmOpts(@Nullable Iterable<String> defaultJvmOpts) {
        this.defaultJvmOpts = defaultJvmOpts;
    }

    /**
     * The application's name.
     */
    @Nullable
    @Input
    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(@Nullable String applicationName) {
        this.applicationName = applicationName;
    }

    public void setOptsEnvironmentVar(@Nullable String optsEnvironmentVar) {
        this.optsEnvironmentVar = optsEnvironmentVar;
    }

    public void setExitEnvironmentVar(@Nullable String exitEnvironmentVar) {
        this.exitEnvironmentVar = exitEnvironmentVar;
    }

    /**
     * The class path for the application.
     */
    @Nullable
    @Classpath
    @Optional
    public FileCollection getClasspath() {
        return classpath;
    }

    /**
     * Returns the module path handling for executing the main class.
     *
     * @since 6.4
     */
    @Nested
    public ModularitySpec getModularity() {
        return modularity;
    }

    public void setClasspath(@Nullable FileCollection classpath) {
        this.classpath = classpath;
    }

    /**
     * The UNIX-like start script generator.
     * <p>
     * Defaults to an implementation of {@link org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator}.
     */
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
        JavaModuleDetector javaModuleDetector = getJavaModuleDetector();
        generator.setApplicationName(getApplicationName());
        generator.setMainClassName(fullMainArgument());
        generator.setDefaultJvmOpts(getDefaultJvmOpts());
        generator.setOptsEnvironmentVar(getOptsEnvironmentVar());
        generator.setExitEnvironmentVar(getExitEnvironmentVar());
        generator.setClasspath(getRelativePath(javaModuleDetector.inferClasspath(mainModule.isPresent(), getClasspath())));
        generator.setModulePath(getRelativePath(javaModuleDetector.inferModulePath(mainModule.isPresent(), getClasspath())));
        if (StringUtils.isEmpty(getExecutableDir())) {
            generator.setScriptRelPath(getUnixScript().getName());
        } else {
            generator.setScriptRelPath(getExecutableDir() + "/" + getUnixScript().getName());
        }
        generator.generateUnixScript(getUnixScript());
        generator.generateWindowsScript(getWindowsScript());
    }

    private String fullMainArgument() {
        String main = "";
        if (mainModule.isPresent()) {
            main += "--module ";
            main += mainModule.get();
            if (mainClass.isPresent()) {
                main += "/";
            }
        }
        if (mainClass.isPresent()) {
            main += mainClass.get();
        }
        return main;
    }

    @Input
    protected Iterable<String> getRelativeClasspath() {
        //a list instance is needed here, as org.gradle.internal.snapshot.ValueSnapshotter.processValue() does not support
        //serializing Iterators directly
        final FileCollection classpathNullable = getClasspath();
        if (classpathNullable == null) {
            return Collections.emptyList();
        }
        return getRelativePath(classpathNullable);
    }

    private Iterable<String> getRelativePath(FileCollection path) {
        return path.getFiles().stream().map(input -> "lib/" + input.getName()).collect(Collectors.toCollection(Lists::newArrayList));
    }

}
