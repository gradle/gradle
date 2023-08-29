/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.CommandLineJavaCompileSpec;
import org.gradle.api.internal.tasks.compile.CompilationSourceDirs;
import org.gradle.api.internal.tasks.compile.CompilerForkUtils;
import org.gradle.api.internal.tasks.compile.DefaultGroovyJavaJointCompileSpec;
import org.gradle.api.internal.tasks.compile.DefaultGroovyJavaJointCompileSpecFactory;
import org.gradle.api.internal.tasks.compile.GroovyCompilerFactory;
import org.gradle.api.internal.tasks.compile.GroovyJavaJointCompileSpec;
import org.gradle.api.internal.tasks.compile.HasCompileOptions;
import org.gradle.api.internal.tasks.compile.MinimalGroovyCompileOptions;
import org.gradle.api.internal.tasks.compile.incremental.IncrementalCompilerFactory;
import org.gradle.api.internal.tasks.compile.incremental.recomp.GroovyRecompilationSpecProvider;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpecProvider;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.buildoption.FeatureFlags;
import org.gradle.internal.file.Deleter;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.internal.GFileUtils;
import org.gradle.util.internal.IncubationLogger;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

import static org.gradle.api.internal.FeaturePreviews.Feature.GROOVY_COMPILATION_AVOIDANCE;

/**
 * Compiles Groovy source files, and optionally, Java source files.
 */
@CacheableTask
public abstract class GroovyCompile extends AbstractCompile implements HasCompileOptions {
    private FileCollection groovyClasspath;
    private final ConfigurableFileCollection astTransformationClasspath;
    private final CompileOptions compileOptions;
    private final GroovyCompileOptions groovyCompileOptions = getProject().getObjects().newInstance(GroovyCompileOptions.class);
    private final FileCollection stableSources = getProject().files((Callable<FileTree>) this::getSource);
    private final Property<JavaLauncher> javaLauncher;
    private File previousCompilationDataFile;

    public GroovyCompile() {
        ObjectFactory objectFactory = getObjectFactory();
        CompileOptions compileOptions = objectFactory.newInstance(CompileOptions.class);
        compileOptions.setIncremental(false);
        compileOptions.getIncrementalAfterFailure().convention(true);
        this.compileOptions = compileOptions;

        JavaToolchainService javaToolchainService = getJavaToolchainService();
        this.javaLauncher = objectFactory.property(JavaLauncher.class).convention(javaToolchainService.launcherFor(it -> {}));

        this.astTransformationClasspath = objectFactory.fileCollection();
        if (!experimentalCompilationAvoidanceEnabled()) {
            this.astTransformationClasspath.from((Callable<FileCollection>) this::getClasspath);
        }
        CompilerForkUtils.doNotCacheIfForkingViaExecutable(compileOptions, getOutputs());
    }

    @Override
    @CompileClasspath
    @Incremental
    public FileCollection getClasspath() {
        // Note that @CompileClasspath here is an approximation and must be fixed before de-incubating getAstTransformationClasspath()
        // See https://github.com/gradle/gradle/pull/9513
        return super.getClasspath();
    }

    /**
     * The classpath containing AST transformations and their dependencies.
     *
     * @since 5.6
     */
    @Classpath
    public ConfigurableFileCollection getAstTransformationClasspath() {
        return astTransformationClasspath;
    }

    private boolean experimentalCompilationAvoidanceEnabled() {
        return getFeatureFlags().isEnabled(GROOVY_COMPILATION_AVOIDANCE);
    }

    @TaskAction
    protected void compile(InputChanges inputChanges) {
        checkGroovyClasspathIsNonEmpty();
        warnIfCompileAvoidanceEnabled();
        GroovyJavaJointCompileSpec spec = createSpec();
        maybeDisableIncrementalCompilationAfterFailure(spec);
        WorkResult result = createCompiler(spec, inputChanges).execute(spec);
        setDidWork(result.getDidWork());
    }

    private void maybeDisableIncrementalCompilationAfterFailure(GroovyJavaJointCompileSpec spec) {
        if (CommandLineJavaCompileSpec.class.isAssignableFrom(spec.getClass())) {
            spec.getCompileOptions().setSupportsIncrementalCompilationAfterFailure(false);
        }
    }

    /**
     * The previous compilation analysis. Internal use only.
     *
     * @since 7.1
     */
    @OutputFile
    protected File getPreviousCompilationData() {
        if (previousCompilationDataFile == null) {
            previousCompilationDataFile = new File(getTemporaryDirWithoutCreating(), "previous-compilation-data.bin");
        }
        return previousCompilationDataFile;
    }

    private void warnIfCompileAvoidanceEnabled() {
        if (experimentalCompilationAvoidanceEnabled()) {
            IncubationLogger.incubatingFeatureUsed("Groovy compilation avoidance");
        }
    }

    private Compiler<GroovyJavaJointCompileSpec> createCompiler(GroovyJavaJointCompileSpec spec, InputChanges inputChanges) {
        GroovyCompilerFactory groovyCompilerFactory = getGroovyCompilerFactory();
        Compiler<GroovyJavaJointCompileSpec> delegatingCompiler = groovyCompilerFactory.newCompiler(spec);
        CleaningJavaCompiler<GroovyJavaJointCompileSpec> cleaningGroovyCompiler = new CleaningJavaCompiler<>(delegatingCompiler, getOutputs(), getDeleter());
        if (spec.incrementalCompilationEnabled()) {
            IncrementalCompilerFactory factory = getIncrementalCompilerFactory();
            return factory.makeIncremental(
                cleaningGroovyCompiler,
                getStableSources().getAsFileTree(),
                createRecompilationSpecProvider(inputChanges)
            );
        } else {
            return cleaningGroovyCompiler;
        }
    }

    private RecompilationSpecProvider createRecompilationSpecProvider(InputChanges inputChanges) {
        FileCollection stableSources = getStableSources();
        return new GroovyRecompilationSpecProvider(
            getDeleter(),
            getServices().get(FileOperations.class),
            stableSources.getAsFileTree(),
            inputChanges.isIncremental(),
            () -> inputChanges.getFileChanges(stableSources).iterator()
        );
    }

    /**
     * The sources for incremental change detection.
     *
     * @since 5.6
     */
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    // Java source files are supported, too. Therefore, we should care about the relative path.
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    protected FileCollection getStableSources() {
        return stableSources;
    }

    private FileCollection determineGroovyCompileClasspath() {
        if (experimentalCompilationAvoidanceEnabled()) {
            return astTransformationClasspath.plus(getClasspath());
        } else {
            return getClasspath();
        }
    }

    private static void validateIncrementalCompilationOptions(List<File> sourceRoots, boolean annotationProcessingConfigured) {
        if (sourceRoots.isEmpty()) {
            throw new InvalidUserDataException("Unable to infer source roots. Incremental Groovy compilation requires the source roots. Change the configuration of your sources or disable incremental Groovy compilation.");
        }

        if (annotationProcessingConfigured) {
            throw new InvalidUserDataException("Enabling incremental compilation and configuring Java annotation processors for Groovy compilation is not allowed. Disable incremental Groovy compilation or remove the Java annotation processor configuration.");
        }
    }

    private GroovyJavaJointCompileSpec createSpec() {
        DefaultGroovyJavaJointCompileSpec spec = new DefaultGroovyJavaJointCompileSpecFactory(compileOptions, getToolchain()).create();
        assert spec != null;

        FileTreeInternal stableSourcesAsFileTree = (FileTreeInternal) getStableSources().getAsFileTree();
        List<File> sourceRoots = CompilationSourceDirs.inferSourceRoots(stableSourcesAsFileTree);

        spec.setSourcesRoots(sourceRoots);
        spec.setSourceFiles(stableSourcesAsFileTree);
        spec.setDestinationDir(getDestinationDirectory().getAsFile().get());
        spec.setWorkingDir(getProjectLayout().getProjectDirectory().getAsFile());
        spec.setTempDir(getTemporaryDir());
        spec.setCompileClasspath(ImmutableList.copyOf(determineGroovyCompileClasspath()));
        configureCompatibilityOptions(spec);
        spec.setAnnotationProcessorPath(Lists.newArrayList(compileOptions.getAnnotationProcessorPath() == null ? getProjectLayout().files() : compileOptions.getAnnotationProcessorPath()));
        spec.setGroovyClasspath(Lists.newArrayList(getGroovyClasspath()));
        spec.setCompileOptions(compileOptions);
        spec.setGroovyCompileOptions(new MinimalGroovyCompileOptions(groovyCompileOptions));
        spec.getCompileOptions().setSupportsCompilerApi(true);
        if (getOptions().isIncremental()) {
            validateIncrementalCompilationOptions(sourceRoots, spec.annotationProcessingConfigured());
            spec.getCompileOptions().setPreviousCompilationDataFile(getPreviousCompilationData());
        }
        if (spec.getGroovyCompileOptions().getStubDir() == null) {
            File dir = new File(getTemporaryDir(), "groovy-java-stubs");
            GFileUtils.mkdirs(dir);
            spec.getGroovyCompileOptions().setStubDir(dir);
        }

        String executable = getJavaLauncher().get().getExecutablePath().getAsFile().getAbsolutePath();
        spec.getCompileOptions().getForkOptions().setExecutable(executable);

        return spec;
    }

    private void configureCompatibilityOptions(DefaultGroovyJavaJointCompileSpec spec) {
        String toolchainVersion = JavaVersion.toVersion(getToolchain().getLanguageVersion().asInt()).toString();
        String sourceCompatibility = getSourceCompatibility();
        // Compatibility can be null if no convention was configured, e.g. when JavaBasePlugin is not applied
        if (sourceCompatibility == null) {
            sourceCompatibility = toolchainVersion;
        }
        String targetCompatibility = getTargetCompatibility();
        if (targetCompatibility == null) {
            targetCompatibility = sourceCompatibility;
        }

        spec.setSourceCompatibility(sourceCompatibility);
        spec.setTargetCompatibility(targetCompatibility);
    }

    private JavaInstallationMetadata getToolchain() {
        return javaLauncher.map(JavaLauncher::getMetadata).get();
    }

    private void checkGroovyClasspathIsNonEmpty() {
        if (getGroovyClasspath().isEmpty()) {
            throw new InvalidUserDataException("'" + getName() + ".groovyClasspath' must not be empty. If a Groovy compile dependency is provided, "
                + "the 'groovy-base' plugin will attempt to configure 'groovyClasspath' automatically. Alternatively, you may configure 'groovyClasspath' explicitly.");
        }
    }

    /**
     * We need to track the Java version of the JVM the Groovy compiler is running on, since the Groovy compiler produces different results depending on it.
     *
     * This should be replaced by a property on the Groovy toolchain as soon as we model these.
     *
     * @since 4.0
     */
    @Input
    protected String getGroovyCompilerJvmVersion() {
        return getToolchain().getLanguageVersion().toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal("tracked via stableSources")
    public FileTree getSource() {
        return super.getSource();
    }

    /**
     * Gets the options for the Groovy compilation. To set specific options for the nested Java compilation, use {@link
     * #getOptions()}.
     *
     * @return The Groovy compile options. Never returns null.
     */
    @Nested
    public GroovyCompileOptions getGroovyOptions() {
        return groovyCompileOptions;
    }

    /**
     * Returns the options for Java compilation.
     *
     * @return The Java compile options. Never returns null.
     */
    @Nested
    public CompileOptions getOptions() {
        return compileOptions;
    }

    /**
     * Returns the classpath containing the version of Groovy to use for compilation.
     *
     * @return The classpath.
     */
    @Classpath
    public FileCollection getGroovyClasspath() {
        return groovyClasspath;
    }

    /**
     * Sets the classpath containing the version of Groovy to use for compilation.
     *
     * @param groovyClasspath The classpath. Must not be null.
     */
    public void setGroovyClasspath(FileCollection groovyClasspath) {
        this.groovyClasspath = groovyClasspath;
    }

    /**
     * The toolchain {@link JavaLauncher} to use for executing the Groovy compiler.
     *
     * @return the java launcher property
     * @since 6.8
     */
    @Nested
    public Property<JavaLauncher> getJavaLauncher() {
        return javaLauncher;
    }

    @Inject
    protected abstract IncrementalCompilerFactory getIncrementalCompilerFactory();

    @Inject
    protected abstract Deleter getDeleter();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Inject
    protected abstract GroovyCompilerFactory getGroovyCompilerFactory();

    @Inject
    protected abstract FeatureFlags getFeatureFlags();

    @Inject
    protected abstract JavaToolchainService getJavaToolchainService();

    private File getTemporaryDirWithoutCreating() {
        // Do not create the temporary folder, since that causes problems.
        return getServices().get(TemporaryFileProvider.class).newTemporaryFile(getName());
    }
}
