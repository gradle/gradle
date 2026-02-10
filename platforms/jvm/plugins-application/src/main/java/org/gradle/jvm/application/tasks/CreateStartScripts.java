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
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.plugins.AppEntryPoint;
import org.gradle.api.internal.plugins.MainClass;
import org.gradle.api.internal.plugins.MainModule;
import org.gradle.api.internal.plugins.StartScriptGenerator;
import org.gradle.api.internal.plugins.UnixStartScriptGenerator;
import org.gradle.api.internal.plugins.WindowsStartScriptGenerator;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.internal.jvm.DefaultModularitySpec;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.jvm.application.scripts.JavaAppStartScriptGenerationDetails;
import org.gradle.jvm.application.scripts.ScriptGenerator;
import org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator;
import org.gradle.util.internal.GUtil;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.util.Collections;
import java.util.stream.Collectors;

import static org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType.GETTER;

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
 * The default generators are of the type {@link TemplateBasedScriptGenerator}, with default templates.
 * This templates can be changed via the {@link TemplateBasedScriptGenerator#setTemplate(TextResource)} method.
 * <p>
 * The default implementations used by this task use <a href="https://docs.groovy-lang.org/latest/html/documentation/template-engines.html#_simpletemplateengine">Groovy's SimpleTemplateEngine</a>
 * to parse the template, with the following variables available:
 * <ul>
 * <li>{@code applicationName} - See {@link JavaAppStartScriptGenerationDetails#getApplicationName()}.</li>
 * <li>{@code gitRef} - See {@link JavaAppStartScriptGenerationDetails#getGitRef()}.</li>
 * <li>{@code optsEnvironmentVar} - See {@link JavaAppStartScriptGenerationDetails#getOptsEnvironmentVar()}.</li>
 * <li>{@code exitEnvironmentVar} - See {@link JavaAppStartScriptGenerationDetails#getExitEnvironmentVar()}.</li>
 * <li>{@code moduleEntryPoint} - The module entry point, or {@code null} if none. Will also include the main class name if present, in the form {@code [moduleName]/[className]}.</li>
 * <li>{@code mainClassName} - The main class name, or usually {@code ""} if none. For legacy reasons, this may be set to {@code --module [moduleEntryPoint]} when using a main module.
 * This behavior should not be relied upon and may be removed in a future release.</li>
 * <li>{@code entryPointArgs} - The arguments to be used on the command-line to enter the application, as a joined string. It should be inserted before the program arguments.</li>
 * <li>{@code defaultJvmOpts} - See {@link JavaAppStartScriptGenerationDetails#getDefaultJvmOpts()}.</li>
 * <li>{@code appNameSystemProperty} - See {@link JavaAppStartScriptGenerationDetails#getAppNameSystemProperty()}.</li>
 * <li>{@code appHomeRelativePath} - The path, relative to the script's own path, of the app home.</li>
 * <li>{@code classpath} - See {@link JavaAppStartScriptGenerationDetails#getClasspath()}. It is already encoded as a joined string.</li>
 * <li>{@code modulePath} (different capitalization) - See {@link JavaAppStartScriptGenerationDetails#getModulePath()}. It is already encoded as a joined string.</li>
 * </ul>
 * <p>
 * The encoded paths expect a variable named {@code APP_HOME} to be present in the script, set to the application home directory which can be resolved using {@code appHomeRelativePath}.
 * </p>
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
public abstract class CreateStartScripts extends ConventionTask {

    private final Property<String> mainModule;
    private final Property<String> mainClass;
    private final Property<String> optsEnvironmentVar;
    private final Property<String> exitEnvironmentVar;
    private final Property<String> executableDir;
    private final ModularitySpec modularity;
    private ScriptGenerator unixStartScriptGenerator = new UnixStartScriptGenerator();
    private ScriptGenerator windowsStartScriptGenerator = new WindowsStartScriptGenerator();
    private final DirectoryProperty outputDir;
    private final Property<String> applicationName;

    public CreateStartScripts() {
        this.mainModule = getObjectFactory().property(String.class);
        this.mainClass = getObjectFactory().property(String.class);
        getGitRef().convention("HEAD");
        this.modularity = getObjectFactory().newInstance(DefaultModularitySpec.class);
        this.applicationName = getObjectFactory().property(String.class);
        this.outputDir = getObjectFactory().directoryProperty();
        this.exitEnvironmentVar = getObjectFactory().property(String.class).convention(getApplicationName().map(appName -> GUtil.toConstant(appName) + "_EXIT_CONSOLE"));
        this.optsEnvironmentVar = getObjectFactory().property(String.class).convention(getApplicationName().map(appName -> GUtil.toConstant(appName) + "_OPTS"));
        this.executableDir = getObjectFactory().property(String.class).convention("bin");
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Inject
    protected abstract JavaModuleDetector getJavaModuleDetector();

    /**
     * The environment variable to use to provide additional options to the JVM.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public Property<String> getOptsEnvironmentVar() {
        return optsEnvironmentVar;
    }

    /**
     * The environment variable to use to control exit value (Windows only).
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public Property<String> getExitEnvironmentVar() {
        return exitEnvironmentVar;
    }

    /**
     * Returns the full path to the Unix script. The target directory is represented by the output directory, the file name is the application name without a file extension.
     * TODO: This should be Provider[RegularFile], but we don't support such upgrade with @ReplacesEagerProperty
     */
    @Internal
    @ReplacesEagerProperty(replacedAccessors = @ReplacedAccessor(value = GETTER, name = "getUnixScript"))
    public RegularFileProperty getUnixScript() {
        return getObjectFactory().fileProperty().value(
            getOutputDir().zip(getApplicationName(), Directory::file)
        );
    }

    /**
     * Returns the full path to the Windows script. The target directory is represented by the output directory, the file name is the application name plus the file extension .bat.
     * TODO: This should be Provider[RegularFile], but we don't support such upgrade with @ReplacesEagerProperty
     */
    @Internal
    @ReplacesEagerProperty(replacedAccessors = @ReplacedAccessor(value = GETTER, name = "getWindowsScript"))
    public RegularFileProperty getWindowsScript() {
        return getObjectFactory().fileProperty().value(
            getOutputDir().zip(getApplicationName(), (outputDir, applicationName) -> outputDir.file(applicationName + ".bat"))
        );
    }

    /**
     * The directory to write the scripts into.
     */
    @OutputDirectory
    @ReplacesEagerProperty
    public DirectoryProperty getOutputDir() {
        return outputDir;
    }

    /**
     * The directory to write the scripts into in the distribution.
     *
     * @since 4.5
     */
    @Input
    @ReplacesEagerProperty
    public Property<String> getExecutableDir() {
        return executableDir;
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
     * @since 6.4
     */
    @Optional
    @Input
    public Property<String> getMainClass() {
        return mainClass;
    }

    /**
     * The application's default JVM options. Defaults to an empty list.
     */
    @Optional
    @Input
    @ReplacesEagerProperty(originalType = Iterable.class)
    public abstract ListProperty<String> getDefaultJvmOpts();

    /**
     * The application's name.
     */
    @Input
    @Optional
    @ReplacesEagerProperty
    public Property<String> getApplicationName() {
        return applicationName;
    }

    /**
     * The Git revision or tag.
     *
     * @since 9.4.0
     */
    @Incubating
    @Optional
    @Input
    public abstract Property<String> getGitRef();

    /**
     * The class path for the application.
     */
    @Classpath
    @Optional
    @ReplacesEagerProperty
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * Returns the module path handling for executing the main class.
     *
     * @since 6.4
     */
    @Nested
    public ModularitySpec getModularity() {
        return modularity;
    }

    /**
     * The UNIX-like start script generator.
     * <p>
     * Defaults to an implementation of {@link TemplateBasedScriptGenerator}.
     */
    @Nested
    public ScriptGenerator getUnixStartScriptGenerator() {
        return unixStartScriptGenerator;
    }

    public void setUnixStartScriptGenerator(ScriptGenerator unixStartScriptGenerator) {
        this.unixStartScriptGenerator = unixStartScriptGenerator;
    }

    /**
     * The Windows start script generator.
     * <p>
     * Defaults to an implementation of {@link TemplateBasedScriptGenerator}.
     */
    @Nested
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
        generator.setApplicationName(getApplicationName().get());
        generator.setGitRef(getGitRef().get());
        generator.setEntryPoint(getEntryPoint());
        generator.setDefaultJvmOpts(getDefaultJvmOpts().get());
        generator.setOptsEnvironmentVar(getOptsEnvironmentVar().get());
        generator.setExitEnvironmentVar(getExitEnvironmentVar().get());
        generator.setClasspath(getRelativePath(javaModuleDetector.inferClasspath(mainModule.isPresent(), getClasspath())));
        generator.setModulePath(getRelativePath(javaModuleDetector.inferModulePath(mainModule.isPresent(), getClasspath())));
        String executableDir = getExecutableDir().getOrNull();
        if (StringUtils.isEmpty(executableDir)) {
            generator.setScriptRelPath(getUnixScript().getAsFile().get().getName());
        } else {
            generator.setScriptRelPath(executableDir + "/" + getUnixScript().getAsFile().get().getName());
        }
        generator.generateUnixScript(getUnixScript().getAsFile().get());
        generator.generateWindowsScript(getWindowsScript().getAsFile().get());
    }

    private AppEntryPoint getEntryPoint() {
        if (mainModule.isPresent()) {
            return new MainModule(mainModule.get(), mainClass.getOrNull());
        }
        return new MainClass(mainClass.getOrElse(""));
    }

    /**
     * TODO: Remove with Gradle 9, we anyway track classpath via {@link #getClasspath()}, this looks unnecessary
     */
    @Input
    @ToBeReplacedByLazyProperty(unreported = true, comment = "Skipped for report since method is protected")
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
