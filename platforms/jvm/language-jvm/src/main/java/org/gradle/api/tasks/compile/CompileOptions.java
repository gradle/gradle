/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.tasks.compile;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.util.internal.CollectionUtils;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Main options for Java compilation.
 */
public abstract class CompileOptions extends AbstractOptions {
    private static final long serialVersionUID = 0;

    private DebugOptions debugOptions;

    private boolean fork;

    private ForkOptions forkOptions;

    private FileCollection bootstrapClasspath;

    private List<String> compilerArgs = Lists.newArrayList();
    private final List<CommandLineArgumentProvider> compilerArgumentProviders = Lists.newArrayList();
    private FileCollection sourcepath;

    private final Property<Boolean> incrementalAfterFailure;
    private final Property<String> javaModuleVersion;
    private final Property<String> javaModuleMainClass;
    private final Property<Integer> release;

    private final DirectoryProperty generatedSourceOutputDirectory;

    private final DirectoryProperty headerOutputDirectory;

    @Inject
    public CompileOptions(ObjectFactory objectFactory) {
        this.javaModuleVersion = objectFactory.property(String.class);
        this.javaModuleMainClass = objectFactory.property(String.class);
        this.generatedSourceOutputDirectory = objectFactory.directoryProperty();
        this.headerOutputDirectory = objectFactory.directoryProperty();
        this.release = objectFactory.property(Integer.class);
        this.incrementalAfterFailure = objectFactory.property(Boolean.class);
        this.forkOptions = objectFactory.newInstance(ForkOptions.class);
        this.debugOptions = new DebugOptions();
        this.getFailOnError().convention(true);
        this.getVerbose().convention(false);
        this.getListFiles().convention(false);
        this.getDeprecation().convention(false);
        this.getWarnings().convention(true);
        this.getDebug().convention(true);
        this.getIncremental().convention(true);
    }

    /**
     * Sets whether to fail the build when compilation fails. Defaults to {@code true}.
     *
     * @since 8.5
     */
    @Input
    @Incubating
    @UpgradedProperty(originalType = boolean.class)
    public abstract Property<Boolean> getFailOnError();

    /**
     * TODO: Add deprecation warning
     *
     * @since 8.5
     */
    @Incubating
    @ReplacedBy("failOnError")
    public Property<Boolean> getIsFailOnError() {
        return getFailOnError();
    }

    /**
     * Tells whether to produce verbose output. Defaults to {@code false}.
     *
     * @since 8.5
     */
    @Console
    @Incubating
    @UpgradedProperty(originalType = boolean.class)
    public abstract Property<Boolean> getVerbose();

    /**
     * TODO: Add deprecation warning
     *
     * @since 8.5
     */
    @Incubating
    @ReplacedBy("verbose")
    public Property<Boolean> getIsVerbose() {
        return getVerbose();
    }

    /**
     * Tells whether to log the files to be compiled. Defaults to {@code false}.
     *
     * @since 8.5
     */
    @Console
    @Incubating
    @UpgradedProperty(originalType = boolean.class)
    public abstract Property<Boolean> getListFiles();

    /**
     * TODO: Add deprecation warning
     *
     * @since 8.5
     */
    @Incubating
    @ReplacedBy("listFiles")
    public Property<Boolean> getIsListFiles() {
        return getListFiles();
    }

    /**
     * Tells whether to log details of usage of deprecated members or classes. Defaults to {@code false}.
     *
     * @since 8.5
     */
    @Console
    @Incubating
    @UpgradedProperty(originalType = boolean.class)
    public abstract Property<Boolean> getDeprecation();

    /**
     * TODO: Add deprecation warning
     *
     * @since 8.5
     */
    @Incubating
    @ReplacedBy("deprecation")
    public Property<Boolean> getIsDeprecation() {
        return getDeprecation();
    }

    /**
     * Tells whether to log warning messages. The default is {@code true}.
     *
     * @since 8.5
     */
    @Console
    @Incubating
    @UpgradedProperty(originalType = boolean.class)
    public abstract Property<Boolean> getWarnings();

    /**
     * TODO: Add deprecation warning
     *
     * @since 8.5
     */
    @Incubating
    @ReplacedBy("warnings")
    public Property<Boolean> getIsWarnings() {
        return getWarnings();
    }

    /**
     * Returns the character encoding to be used when reading source files. Defaults to {@code null}, in which
     * case the platform default encoding will be used.
     */
    @Optional
    @Input
    @UpgradedProperty
    public abstract Property<String> getEncoding();

    /**
     * Tells whether to include debugging information in the generated class files. Defaults
     * to {@code true}. See {@link DebugOptions#getDebugLevel()} for which debugging information will be generated.
     *
     * @since 8.5
     */
    @Input
    @Incubating
    @UpgradedProperty(originalType = boolean.class)
    public abstract Property<Boolean> getDebug();

    /**
     * TODO: Add deprecation warning
     *
     * @since 8.5
     */
    @Incubating
    @ReplacedBy("debug")
    public Property<Boolean> getIsDebug() {
        return getDebug();
    }

    /**
     * Returns options for generating debugging information.
     */
    @Nested
    public DebugOptions getDebugOptions() {
        return debugOptions;
    }

    /**
     * Sets options for generating debugging information.
     */
    public void setDebugOptions(DebugOptions debugOptions) {
        this.debugOptions = debugOptions;
    }

    /**
     * Tells whether to run the compiler in its own process. Note that this does
     * not necessarily mean that a new process will be created for each compile task.
     * Defaults to {@code false}.
     */
    @Input
    public boolean isFork() {
        return fork;
    }

    /**
     * Sets whether to run the compiler in its own process. Note that this does
     * not necessarily mean that a new process will be created for each compile task.
     * Defaults to {@code false}.
     */
    public void setFork(boolean fork) {
        this.fork = fork;
    }

    /**
     * Returns options for running the compiler in a child process.
     */
    @Nested
    public ForkOptions getForkOptions() {
        return forkOptions;
    }

    /**
     * Sets options for running the compiler in a child process.
     */
    public void setForkOptions(ForkOptions forkOptions) {
        this.forkOptions = forkOptions;
    }

    /**
     * Returns the bootstrap classpath to be used for the compiler process. Defaults to {@code null}.
     *
     * @since 4.3
     */
    @Nullable
    @Optional
    @CompileClasspath
    public FileCollection getBootstrapClasspath() {
        return bootstrapClasspath;
    }

    /**
     * Sets the bootstrap classpath to be used for the compiler process. Defaults to {@code null}.
     *
     * @since 4.3
     */
    public void setBootstrapClasspath(@Nullable FileCollection bootstrapClasspath) {
        this.bootstrapClasspath = bootstrapClasspath;
    }

    /**
     * Returns the extension dirs to be used for the compiler process. Defaults to {@code null}.
     */
    @Optional
    @Input
    @UpgradedProperty
    public abstract Property<String> getExtensionDirs();

    /**
     * Returns any additional arguments to be passed to the compiler.
     * Defaults to the empty list.
     *
     * Compiler arguments not supported by the DSL can be added here.
     *
     * For example, it is possible to pass the {@code --enable-preview} option that was added in newer Java versions:
     * <pre><code>compilerArgs.add("--enable-preview")</code></pre>
     *
     * Note that if {@code --release} is added then {@code -target} and {@code -source}
     * are ignored.
     */
    @Input
    public List<String> getCompilerArgs() {
        return compilerArgs;
    }

    /**
     * Returns all compiler arguments, added to the {@link #getCompilerArgs()} or the {@link #getCompilerArgumentProviders()} property.
     *
     * @since 4.5
     */
    @Internal
    public List<String> getAllCompilerArgs() {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.addAll(CollectionUtils.stringize(getCompilerArgs()));
        for (CommandLineArgumentProvider compilerArgumentProvider : getCompilerArgumentProviders()) {
            builder.addAll(compilerArgumentProvider.asArguments());
        }
        return builder.build();
    }

    /**
     * Compiler argument providers.
     *
     * @since 4.5
     */
    @Nested
    public List<CommandLineArgumentProvider> getCompilerArgumentProviders() {
        return compilerArgumentProviders;
    }

    /**
     * Sets any additional arguments to be passed to the compiler.
     * Defaults to the empty list.
     */
    public void setCompilerArgs(List<String> compilerArgs) {
        this.compilerArgs = compilerArgs;
    }

    /**
     * Convenience method to set {@link ForkOptions} with named parameter syntax.
     * Calling this method will set {@code fork} to {@code true}.
     */
    public CompileOptions fork(Map<String, Object> forkArgs) {
        fork = true;
        forkOptions.define(forkArgs);
        return this;
    }

    /**
     * Convenience method to set {@link DebugOptions} with named parameter syntax.
     * Calling this method will set {@code debug} to {@code true}.
     */
    public CompileOptions debug(Map<String, Object> debugArgs) {
        getDebug().set(true);
        debugOptions.define(debugArgs);
        return this;
    }

    /**
     * informs whether to use incremental compilation feature.
     *
     * @since 8.5
     */
    @Internal
    @Incubating
    @UpgradedProperty(originalType = boolean.class, fluentSetter = true)
    public abstract Property<Boolean> getIncremental();

    /**
     * TODO: Add deprecation warning
     *
     * @since 8.5
     */
    @Incubating
    @ReplacedBy("incremental")
    public Property<Boolean> getIsIncremental() {
        return getIncremental();
    }

    /**
     * Used to enable or disable incremental compilation after a failure.
     * <p>
     * By default, incremental compilation after a failure is enabled for Java and Groovy.
     * It has no effect for Scala. It has no effect if incremental compilation is not enabled.
     * <p>
     * When the Java command line compiler is used, i.e. when a custom java home is passed to forkOptions.javaHome or javac is passed to forkOptions.executable,
     * this optimization is automatically disabled, since the compiler is not invoked via the compiler API.
     *
     * @since 7.6
     */
    @Input
    @Optional
    @Incubating
    public Property<Boolean> getIncrementalAfterFailure() {
        return incrementalAfterFailure;
    }

    /**
     * The source path to use for the compilation.
     * <p>
     * The source path indicates the location of source files that <i>may</i> be compiled if necessary.
     * It is effectively a complement to the class path, where the classes to be compiled against are in source form.
     * It does <b>not</b> indicate the actual primary source being compiled.
     * <p>
     * The source path feature of the Java compiler is rarely needed for modern builds that use dependency management.
     * <p>
     * The default value for the source path is {@code null}, which indicates an <i>empty</i> source path.
     * Note that this is different to the default value for the {@code -sourcepath} option for {@code javac}, which is to use the value specified by {@code -classpath}.
     * If you wish to use any source path, it must be explicitly set.
     *
     * @return the source path
     * @see #setSourcepath(FileCollection)
     */
    @Optional
    @Nullable
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    public FileCollection getSourcepath() {
        return sourcepath;
    }

    /**
     * Sets the source path to use for the compilation.
     *
     * @param sourcepath the source path
     */
    public void setSourcepath(@Nullable FileCollection sourcepath) {
        this.sourcepath = sourcepath;
    }

    /**
     * Returns the classpath to use to load annotation processors. This path is also used for annotation processor discovery.
     *
     * @return The annotation processor path, or {@code null} if annotation processing is disabled.
     * @since 3.4
     */
    @Optional
    @Classpath
    @UpgradedProperty
    public abstract ConfigurableFileCollection getAnnotationProcessorPath();

    /**
     * Configures the Java language version for this compile task ({@code --release} compiler flag).
     * <p>
     * If set, it will take precedences over the {@link AbstractCompile#getSourceCompatibility()} and {@link AbstractCompile#getTargetCompatibility()} settings.
     * <p>
     * This option is only taken into account by the {@link org.gradle.api.tasks.compile.JavaCompile} task.
     *
     * @since 6.6
     */
    @SuppressWarnings("JavadocReference")
    @Input
    @Optional
    public Property<Integer> getRelease() {
        return release;
    }


    /**
     * Set the version of the Java module.
     *
     * @since 6.4
     */
    @Optional
    @Input
    public Property<String> getJavaModuleVersion() {
        return javaModuleVersion;
    }

    /**
     * Set the main class of the Java module, if the module is supposed to be executable.
     *
     * @since 6.4
     */
    @Optional
    @Input
    public Property<String> getJavaModuleMainClass() {
        return javaModuleMainClass;
    }

    /**
     * Returns the directory to place source files generated by annotation processors.
     *
     * @since 6.3
     */
    @Optional
    @OutputDirectory
    public DirectoryProperty getGeneratedSourceOutputDirectory() {
        return generatedSourceOutputDirectory;
    }

    /**
     * Returns the directory to place source files generated by annotation processors.
     *
     * @since 4.3
     *
     * @deprecated Use {@link #getGeneratedSourceOutputDirectory()} instead. This method will be removed in Gradle 9.0.
     */
    @Nullable
    @Deprecated
    @ReplacedBy("generatedSourceOutputDirectory")
    public File getAnnotationProcessorGeneratedSourcesDirectory() {
        DeprecationLogger.deprecateProperty(CompileOptions.class, "annotationProcessorGeneratedSourcesDirectory")
            .replaceWith("generatedSourceOutputDirectory")
            .willBeRemovedInGradle9()
            .withDslReference()
            .nagUser();

        return generatedSourceOutputDirectory.getAsFile().getOrNull();
    }

    /**
     * Sets the directory to place source files generated by annotation processors.
     *
     * @since 4.3
     *
     * @deprecated Use {@link #getGeneratedSourceOutputDirectory()}.set() instead. This method will be removed in Gradle 9.0.
     */
    @Deprecated
    public void setAnnotationProcessorGeneratedSourcesDirectory(@Nullable File file) {
        DeprecationLogger.deprecateProperty(CompileOptions.class, "annotationProcessorGeneratedSourcesDirectory")
            .replaceWith("generatedSourceOutputDirectory")
            .willBeRemovedInGradle9()
            .withDslReference()
            .nagUser();

        this.generatedSourceOutputDirectory.set(file);
    }

    /**
     * Sets the directory to place source files generated by annotation processors.
     *
     * @since 4.3
     *
     * @deprecated Use {@link #getGeneratedSourceOutputDirectory()}.set() instead. This method will be removed in Gradle 9.0.
     */
    @Deprecated
    public void setAnnotationProcessorGeneratedSourcesDirectory(Provider<File> file) {
        DeprecationLogger.deprecateProperty(CompileOptions.class, "annotationProcessorGeneratedSourcesDirectory")
            .replaceWith("generatedSourceOutputDirectory")
            .willBeRemovedInGradle9()
            .withDslReference()
            .nagUser();

        this.generatedSourceOutputDirectory.fileProvider(file);
    }

    /**
     * If this option is set to a non-null directory, it will be passed to the Java compiler's `-h` option, prompting it to generate native headers to that directory.
     *
     * @since 4.10
     */
    @Optional
    @OutputDirectory
    public DirectoryProperty getHeaderOutputDirectory() {
        return headerOutputDirectory;
    }

}
