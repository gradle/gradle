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
import org.gradle.api.Action;
import org.gradle.api.Incubating;
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
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacedDeprecation;
import org.gradle.internal.instrumentation.api.annotations.ReplacedDeprecation.RemovedIn;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.util.internal.CollectionUtils;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType.GETTER;
import static org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType.SETTER;
import static org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty.BinaryCompatibility.ACCESSORS_KEPT;

/**
 * Main options for Java compilation.
 */
@SuppressWarnings("deprecation")
public abstract class CompileOptions extends AbstractOptions {
    private static final long serialVersionUID = 0;

    private boolean failOnError = true;

    private boolean verbose;

    private boolean listFiles;

    private boolean deprecation;

    private boolean warnings = true;

    private String encoding;

    private boolean debug = true;

    private DebugOptions debugOptions;

    private boolean fork;

    private ForkOptions forkOptions;

    private FileCollection bootstrapClasspath;

    private String extensionDirs;

    private List<String> compilerArgs = new ArrayList<>();
    private final List<CommandLineArgumentProvider> compilerArgumentProviders = new ArrayList<>();

    private boolean incremental = true;

    private FileCollection sourcepath;

    private FileCollection annotationProcessorPath;

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
    }

    /**
     * Tells whether to fail the build when compilation fails. Defaults to {@code true}.
     */
    @Input
    @ToBeReplacedByLazyProperty
    public boolean isFailOnError() {
        return failOnError;
    }

    /**
     * Sets whether to fail the build when compilation fails. Defaults to {@code true}.
     */
    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    /**
     * Tells whether to produce verbose output. Defaults to {@code false}.
     */
    @Console
    @ToBeReplacedByLazyProperty
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets whether to produce verbose output. Defaults to {@code false}.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Tells whether to log the files to be compiled. Defaults to {@code false}.
     */
    @Console
    @ToBeReplacedByLazyProperty
    public boolean isListFiles() {
        return listFiles;
    }

    /**
     * Sets whether to log the files to be compiled. Defaults to {@code false}.
     */
    public void setListFiles(boolean listFiles) {
        this.listFiles = listFiles;
    }

    /**
     * Tells whether to log details of usage of deprecated members or classes. Defaults to {@code false}.
     */
    @Console
    @ToBeReplacedByLazyProperty
    public boolean isDeprecation() {
        return deprecation;
    }

    /**
     * Sets whether to log details of usage of deprecated members or classes. Defaults to {@code false}.
     */
    public void setDeprecation(boolean deprecation) {
        this.deprecation = deprecation;
    }

    /**
     * Tells whether to log warning messages. The default is {@code true}.
     */
    @Console
    @ToBeReplacedByLazyProperty
    public boolean isWarnings() {
        return warnings;
    }

    /**
     * Sets whether to log warning messages. The default is {@code true}.
     */
    public void setWarnings(boolean warnings) {
        this.warnings = warnings;
    }

    /**
     * Returns the character encoding to be used when reading source files. Defaults to {@code null}, in which
     * case the platform default encoding will be used.
     */
    @Nullable
    @Optional
    @Input
    @ToBeReplacedByLazyProperty
    public String getEncoding() {
        return encoding;
    }

    /**
     * Sets the character encoding to be used when reading source files. Defaults to {@code null}, in which
     * case the platform default encoding will be used.
     */
    public void setEncoding(@Nullable String encoding) {
        this.encoding = encoding;
    }

    /**
     * Tells whether to include debugging information in the generated class files. Defaults
     * to {@code true}. See {@link DebugOptions#getDebugLevel()} for which debugging information will be generated.
     */
    @Input
    @ToBeReplacedByLazyProperty
    public boolean isDebug() {
        return debug;
    }

    /**
     * Sets whether to include debugging information in the generated class files. Defaults
     * to {@code true}. See {@link DebugOptions#getDebugLevel()} for which debugging information will be generated.
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
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
     *
     * @deprecated Setting a new instance of this property is unnecessary. This method will be removed in Gradle 9.0. Use {@link #debugOptions(Action)} instead.
     */
    @Deprecated
    public void setDebugOptions(DebugOptions debugOptions) {
        DeprecationLogger.deprecateMethod(CompileOptions.class, "setDebugOptions(DebugOptions)")
            .replaceWith("debugOptions(Action)")
            .withContext("Setting a new instance of debugOptions is unnecessary.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_nested_properties_setters")
            .nagUser();
        this.debugOptions = debugOptions;
    }

    /**
     * Execute the given action against {@link #getDebugOptions()}.
     *
     * @since 8.11
     */
    public void debugOptions(Action<? super DebugOptions> action) {
        action.execute(debugOptions);
    }

    /**
     * Tells whether to run the compiler in its own process. Note that this does
     * not necessarily mean that a new process will be created for each compile task.
     * Defaults to {@code false}.
     */
    @Input
    @ToBeReplacedByLazyProperty
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
     *
     * @deprecated Setting a new instance of this property is unnecessary. This method will be removed in Gradle 9.0. Use {@link #forkOptions(Action)} instead.
     */
    @Deprecated
    public void setForkOptions(ForkOptions forkOptions) {
        DeprecationLogger.deprecateMethod(CompileOptions.class, "setForkOptions(ForkOptions)")
            .replaceWith("forkOptions(Action)")
            .withContext("Setting a new instance of forkOptions is unnecessary.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_nested_properties_setters")
            .nagUser();
        this.forkOptions = forkOptions;
    }

    /**
     * Execute the given action against {@link #getForkOptions()}.
     *
     * @since 8.11
     */
    public void forkOptions(Action<? super ForkOptions> action) {
        action.execute(forkOptions);
    }

    /**
     * Returns the bootstrap classpath to be used for the compiler process. Defaults to {@code null}.
     *
     * @since 4.3
     */
    @Nullable
    @Optional
    @CompileClasspath
    @ToBeReplacedByLazyProperty
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
    @Nullable
    @Optional
    @Input
    @ToBeReplacedByLazyProperty
    public String getExtensionDirs() {
        return extensionDirs;
    }

    /**
     * Sets the extension dirs to be used for the compiler process. Defaults to {@code null}.
     */
    public void setExtensionDirs(@Nullable String extensionDirs) {
        this.extensionDirs = extensionDirs;
    }

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
    @ToBeReplacedByLazyProperty
    public List<String> getCompilerArgs() {
        return compilerArgs;
    }

    /**
     * Returns all compiler arguments, added to the {@link #getCompilerArgs()} or the {@link #getCompilerArgumentProviders()} property.
     *
     * @since 4.5
     */
    @Internal
    @ToBeReplacedByLazyProperty
    public List<String> getAllCompilerArgs() {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.addAll(CollectionUtils.stringize(getCompilerArgs()));
        for (CommandLineArgumentProvider compilerArgumentProvider : getCompilerArgumentProviders()) {
            builder.addAll(CollectionUtils.toStringList(compilerArgumentProvider.asArguments()));
        }
        return builder.build();
    }

    /**
     * Compiler argument providers.
     *
     * @since 4.5
     */
    @Nested
    @ToBeReplacedByLazyProperty(comment = "Should this be lazy?")
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
     *
     * @deprecated This method will be removed in Gradle 9.0
     */
    @Deprecated
    public CompileOptions fork(Map<String, Object> forkArgs) {

        DeprecationLogger.deprecateMethod(CompileOptions.class, "fork(Map)")
            .withAdvice("Set properties directly on the 'forkOptions' property instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_abstract_options")
            .nagUser();

        fork = true;
        DeprecationLogger.whileDisabled(() -> forkOptions.define(forkArgs));
        return this;
    }

    /**
     * Convenience method to set {@link DebugOptions} with named parameter syntax.
     * Calling this method will set {@code debug} to {@code true}.
     *
     * @deprecated This method will be removed in Gradle 9.0
     */
    @Deprecated
    public CompileOptions debug(Map<String, Object> debugArgs) {

        DeprecationLogger.deprecateMethod(CompileOptions.class, "debug(Map)")
            .withAdvice("Set properties directly on the 'debugOptions' property instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_abstract_options")
            .nagUser();

        debug = true;

        // Disable deprecation to avoid double-warning
        DeprecationLogger.whileDisabled(() -> debugOptions.define(debugArgs));
        return this;
    }

    /**
     * Configure the java compilation to be incremental (e.g. compiles only those java classes that were changed or that are dependencies to the changed classes).
     */
    public CompileOptions setIncremental(boolean incremental) {
        this.incremental = incremental;
        return this;
    }

    /**
     * informs whether to use incremental compilation feature. See {@link #setIncremental(boolean)}
     */
    @Internal
    @ToBeReplacedByLazyProperty
    public boolean isIncremental() {
        return incremental;
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
    @ToBeReplacedByLazyProperty
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
    @Nullable
    @Optional
    @Classpath
    @ToBeReplacedByLazyProperty
    public FileCollection getAnnotationProcessorPath() {
        return annotationProcessorPath;
    }

    /**
     * Set the classpath to use to load annotation processors. This path is also used for annotation processor discovery.
     *
     * @param annotationProcessorPath The annotation processor path, or {@code null} to disable annotation processing.
     * @since 3.4
     */
    public void setAnnotationProcessorPath(@Nullable FileCollection annotationProcessorPath) {
        this.annotationProcessorPath = annotationProcessorPath;
    }

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
    @ReplacesEagerProperty(
        replacedAccessors = {
            @ReplacedAccessor(value = GETTER, name = "getAnnotationProcessorGeneratedSourcesDirectory"),
            @ReplacedAccessor(value = SETTER, name = "setAnnotationProcessorGeneratedSourcesDirectory")
        },
        binaryCompatibility = ACCESSORS_KEPT,
        deprecation = @ReplacedDeprecation(removedIn = RemovedIn.GRADLE9, withDslReference = true)
    )
    public DirectoryProperty getGeneratedSourceOutputDirectory() {
        return generatedSourceOutputDirectory;
    }

    /**
     * Returns the directory to place source files generated by annotation processors.
     *
     * @since 4.3
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
