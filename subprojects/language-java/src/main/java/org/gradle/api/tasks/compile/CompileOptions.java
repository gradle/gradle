/*
 * Copyright 2007 the original author or authors.
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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.gradle.api.Incubating;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.util.CollectionUtils;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Main options for Java compilation.
 */
public class CompileOptions extends AbstractOptions {
    private static final long serialVersionUID = 0;

    private static final ImmutableSet<String> EXCLUDE_FROM_ANT_PROPERTIES =
            ImmutableSet.of("debugOptions", "forkOptions", "compilerArgs", "incremental", "allCompilerArgs", "compilerArgumentProviders");

    private boolean failOnError = true;

    private boolean verbose;

    private boolean listFiles;

    private boolean deprecation;

    private boolean warnings = true;

    private String encoding;

    private boolean debug = true;

    private DebugOptions debugOptions = new DebugOptions();

    private boolean fork;

    private ForkOptions forkOptions = new ForkOptions();

    private FileCollection bootstrapClasspath;

    private String extensionDirs;

    private List<String> compilerArgs = Lists.newArrayList();
    private List<CommandLineArgumentProvider> compilerArgumentProviders = Lists.newArrayList();

    private boolean incremental = true;

    private FileCollection sourcepath;

    private FileCollection annotationProcessorPath;

    private final Property<File> annotationProcessorGeneratedSourcesDirectory;

    private DirectoryProperty headerOutputDirectory;

    @Inject
    public CompileOptions(ProjectLayout projectLayout, ObjectFactory objectFactory) {
        this.annotationProcessorGeneratedSourcesDirectory = objectFactory.property(File.class);
        this.headerOutputDirectory = projectLayout.directoryProperty();
    }

    /**
     * Tells whether to fail the build when compilation fails. Defaults to {@code true}.
     */
    @Input
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
    @Nullable
    @Optional
    @Input
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
     * Compiler arguments not supported by the DSL can be added here. For example, it is possible
     * to pass the {@code --release} option of JDK 9:
     * <pre><code>compilerArgs.addAll(['--release', '7'])</code></pre>
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
    @Incubating
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
    @Incubating
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
        debug = true;
        debugOptions.define(debugArgs);
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
     * Internal method.
     */
    @Override
    public Map<String, Object> optionMap() {
        Map<String, Object> map = super.optionMap();
        map.putAll(debugOptions.optionMap());
        map.putAll(forkOptions.optionMap());
        return map;
    }

    @Override
    protected boolean excludeFromAntProperties(String fieldName) {
        return EXCLUDE_FROM_ANT_PROPERTIES.contains(fieldName);
    }

    @Override
    protected String getAntPropertyName(String fieldName) {
        if (fieldName.equals("warnings")) {
            return "nowarn";
        }
        if (fieldName.equals("extensionDirs")) {
            return "extdirs";
        }
        return fieldName;
    }

    @Override
    protected Object getAntPropertyValue(String fieldName, Object value) {
        if (fieldName.equals("warnings")) {
            return !warnings;
        }
        return value;
    }

    /**
     * informs whether to use incremental compilation feature. See {@link #setIncremental(boolean)}
     */
    @Internal
    public boolean isIncremental() {
        return incremental;
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
    @Incubating
    @Optional
    @Nullable
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
    @Incubating
    public void setSourcepath(@Nullable FileCollection sourcepath) {
        this.sourcepath = sourcepath;
    }

    /**
     * Returns the classpath to use to load annotation processors. This path is also used for annotation processor discovery. If set to {@code null}, it means use the compile classpath.
     *
     * @return The annotation processor path, or {@code null} to use the compile classpath.
     * @since 3.4
     */
    @Nullable
    @Internal // Handled on the compile task
    public FileCollection getAnnotationProcessorPath() {
        return annotationProcessorPath;
    }

    /**
     * Set the classpath to use to load annotation processors. This path is also used for annotation processor discovery. The value can be {@code null}, which means use the compile classpath.
     *
     * @param annotationProcessorPath The annotation processor path, or {@code null} to use the compile classpath.
     * @since 3.4
     */
    public void setAnnotationProcessorPath(@Nullable FileCollection annotationProcessorPath) {
        this.annotationProcessorPath = annotationProcessorPath;
    }

    /**
     * Returns the directory to place source files generated by annotation processors.
     *
     * @since 4.3
     */
    @Incubating
    @Nullable
    @Optional
    @OutputDirectory
    public File getAnnotationProcessorGeneratedSourcesDirectory() {
        return annotationProcessorGeneratedSourcesDirectory.getOrNull();
    }

    /**
     * Sets the directory to place source files generated by annotation processors.
     *
     * @since 4.3
     */
    @Incubating
    public void setAnnotationProcessorGeneratedSourcesDirectory(@Nullable File file) {
        this.annotationProcessorGeneratedSourcesDirectory.set(file);
    }

    /**
     * Sets the directory to place source files generated by annotation processors.
     *
     * @since 4.3
     */
    @Incubating
    public void setAnnotationProcessorGeneratedSourcesDirectory(Provider<File> file) {
        this.annotationProcessorGeneratedSourcesDirectory.set(file);
    }

    /**
     * If this option is set to a non-null directory, it will be passed to the Java compiler's `-h` option, prompting it to generate native headers to that directory.
     *
     * @since 4.10
     */
    @Incubating
    @Optional
    @OutputDirectory
    public DirectoryProperty getHeaderOutputDirectory() {
        return headerOutputDirectory;
    }

}
