/*
 * Copyright 2012 the original author or authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.CommandLineJavaCompileSpec;
import org.gradle.api.internal.tasks.compile.CompilationSourceDirs;
import org.gradle.api.internal.tasks.compile.CompileJavaBuildOperationReportingCompiler;
import org.gradle.api.internal.tasks.compile.CompilerForkUtils;
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec;
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpecFactory;
import org.gradle.api.internal.tasks.compile.HasCompileOptions;
import org.gradle.api.internal.tasks.compile.JavaCompileExecutableUtils;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.IncrementalCompilerFactory;
import org.gradle.api.internal.tasks.compile.incremental.recomp.JavaRecompilationSpecProvider;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.CacheableTask;
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
import org.gradle.internal.file.Deleter;
import org.gradle.internal.jvm.DefaultModularitySpec;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.internal.DefaultToolchainJavaCompiler;
import org.gradle.jvm.toolchain.internal.JavaExecutableUtils;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;
import org.gradle.work.NormalizeLineEndings;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Compiles Java source files.
 *
 * <pre class='autoTested'>
 *     plugins {
 *         id 'java'
 *     }
 *
 *     tasks.withType(JavaCompile).configureEach {
 *         //enable compilation in a separate daemon process
 *         options.fork = true
 *     }
 * </pre>
 */
@CacheableTask
public abstract class JavaCompile extends AbstractCompile implements HasCompileOptions {
    private final CompileOptions compileOptions;
    private final FileCollection stableSources = getProject().files((Callable<FileTree>) this::getSource);
    private final ModularitySpec modularity;
    private File previousCompilationDataFile;
    private final Property<JavaCompiler> javaCompiler;

    public JavaCompile() {
        ObjectFactory objectFactory = getObjectFactory();
        compileOptions = objectFactory.newInstance(CompileOptions.class);
        modularity = objectFactory.newInstance(DefaultModularitySpec.class);
        JavaToolchainService javaToolchainService = getJavaToolchainService();
        Provider<JavaCompiler> javaCompilerConvention = getProviderFactory()
            .provider(() -> JavaCompileExecutableUtils.getExecutableOverrideToolchainSpec(this, objectFactory))
            .flatMap(javaToolchainService::compilerFor)
            .orElse(javaToolchainService.compilerFor(it -> {}));
        javaCompiler = objectFactory.property(JavaCompiler.class).convention(javaCompilerConvention);
        javaCompiler.finalizeValueOnRead();
        compileOptions.getIncrementalAfterFailure().convention(true);
        CompilerForkUtils.doNotCacheIfForkingViaExecutable(compileOptions, getOutputs());
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
     * Configures the java compiler to be used to compile the Java source.
     *
     * @see org.gradle.jvm.toolchain.JavaToolchainSpec
     * @since 6.7
     */
    @Nested
    public Property<JavaCompiler> getJavaCompiler() {
        return javaCompiler;
    }

    /**
     * Compile the sources, taking into account the changes reported by inputs.
     *
     * @since 6.0
     */
    @TaskAction
    protected void compile(InputChanges inputs) {
        DefaultJavaCompileSpec spec = createSpec();
        if (!compileOptions.getIncremental().getOrElse(false)) {
            performFullCompilation(spec);
        } else {
            performIncrementalCompilation(inputs, spec);
        }
    }

    private void performIncrementalCompilation(InputChanges inputs, DefaultJavaCompileSpec spec) {
        boolean isUsingCliCompiler = isUsingCliCompiler(spec);
        if (isUsingCliCompiler) {
            spec.getCompileOptions().setSupportsIncrementalCompilationAfterFailure(false);
        }
        spec.getCompileOptions().setSupportsCompilerApi(!isUsingCliCompiler);
        spec.getCompileOptions().setSupportsConstantAnalysis(!isUsingCliCompiler);
        spec.getCompileOptions().setPreviousCompilationDataFile(getPreviousCompilationData());

        Compiler<JavaCompileSpec> compiler = createCompiler();
        compiler = makeIncremental(inputs, (CleaningJavaCompiler<JavaCompileSpec>) compiler, getStableSources());
        performCompilation(spec, compiler);
    }

    private Compiler<JavaCompileSpec> makeIncremental(InputChanges inputs, CleaningJavaCompiler<JavaCompileSpec> compiler, FileCollection stableSources) {
        FileTree sources = stableSources.getAsFileTree();
        return getIncrementalCompilerFactory().makeIncremental(
            compiler,
            sources,
            createRecompilationSpec(inputs, sources)
        );
    }

    private JavaRecompilationSpecProvider createRecompilationSpec(InputChanges inputs, FileTree sources) {
        return new JavaRecompilationSpecProvider(
            getDeleter(),
            getServices().get(FileOperations.class),
            sources,
            inputs.isIncremental(),
            () -> inputs.getFileChanges(getStableSources()).iterator()
        );
    }

    private boolean isUsingCliCompiler(DefaultJavaCompileSpec spec) {
        return CommandLineJavaCompileSpec.class.isAssignableFrom(spec.getClass());
    }

    private void performFullCompilation(DefaultJavaCompileSpec spec) {
        Compiler<JavaCompileSpec> compiler;
        spec.setSourceFiles(getStableSources());
        compiler = createCompiler();
        performCompilation(spec, compiler);
    }

    CleaningJavaCompiler<JavaCompileSpec> createCompiler() {
        Compiler<JavaCompileSpec> javaCompiler = createToolchainCompiler();
        return new CleaningJavaCompiler<>(javaCompiler, getOutputs(), getDeleter());
    }

    private <T extends CompileSpec> Compiler<T> createToolchainCompiler() {
        return spec -> {
            DefaultToolchainJavaCompiler compiler = (DefaultToolchainJavaCompiler) getJavaCompiler().get();
            return compiler.execute(spec);
        };
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

    private void performCompilation(JavaCompileSpec spec, Compiler<JavaCompileSpec> compiler) {
        WorkResult result = new CompileJavaBuildOperationReportingCompiler(this, compiler, getServices().get(BuildOperationExecutor.class)).execute(spec);
        setDidWork(result.getDidWork());
    }

    @VisibleForTesting
    DefaultJavaCompileSpec createSpec() {
        validateForkOptionsMatchToolchain();
        List<File> sourcesRoots = CompilationSourceDirs.inferSourceRoots((FileTreeInternal) getStableSources().getAsFileTree());
        JavaModuleDetector javaModuleDetector = getJavaModuleDetector();
        boolean isModule = JavaModuleDetector.isModuleSource(modularity.getInferModulePath().get(), sourcesRoots);
        boolean isSourcepathUserDefined = compileOptions.getSourcepath() != null && !compileOptions.getSourcepath().isEmpty();

        DefaultJavaCompileSpec spec = new DefaultJavaCompileSpecFactory(compileOptions, getToolchain()).create();

        spec.setDestinationDir(getDestinationDirectory().getAsFile().get());
        spec.setWorkingDir(getProjectLayout().getProjectDirectory().getAsFile());
        spec.setTempDir(getTemporaryDir());
        spec.setCompileClasspath(ImmutableList.copyOf(javaModuleDetector.inferClasspath(isModule, getClasspath())));
        spec.setModulePath(ImmutableList.copyOf(javaModuleDetector.inferModulePath(isModule, getClasspath())));
        if (isModule && !isSourcepathUserDefined) {
            compileOptions.setSourcepath(getProjectLayout().files(sourcesRoots));
        }
        spec.setAnnotationProcessorPath(compileOptions.getAnnotationProcessorPath() == null ? ImmutableList.of() : ImmutableList.copyOf(compileOptions.getAnnotationProcessorPath()));
        configureCompileOptions(spec);
        spec.setSourcesRoots(sourcesRoots);

        if (!isToolchainCompatibleWithJava8()) {
            spec.getCompileOptions().setHeaderOutputDirectory(null);
        }
        return spec;
    }

    private void validateForkOptionsMatchToolchain() {
        if (!getOptions().isFork()) {
            return;
        }

        JavaCompiler javaCompilerTool = getJavaCompiler().get();
        File toolchainJavaHome = javaCompilerTool.getMetadata().getInstallationPath().getAsFile();

        ForkOptions forkOptions = getOptions().getForkOptions();
        File customJavaHome = forkOptions.getJavaHome();
        if (customJavaHome != null) {
            JavaExecutableUtils.validateMatchingFiles(
                customJavaHome, "Toolchain from `javaHome` property on `ForkOptions`",
                toolchainJavaHome, "toolchain from `javaCompiler` property"
            );
        }

        String customExecutablePath = forkOptions.getExecutable();
        if (customExecutablePath != null) {
            // We do not match the custom executable against the compiler executable from the toolchain (javac),
            // because the custom executable can be set to the path of another tool in the toolchain such as a launcher (java).
            File customExecutableJavaHome = JavaExecutableUtils.resolveJavaHomeOfExecutable(customExecutablePath);
            JavaExecutableUtils.validateMatchingFiles(
                customExecutableJavaHome, "Toolchain from `executable` property on `ForkOptions`",
                toolchainJavaHome, "toolchain from `javaCompiler` property"
            );
        }
    }

    private boolean isToolchainCompatibleWithJava8() {
        return getToolchain().getLanguageVersion().canCompileOrRun(8);
    }

    @Input
    JavaVersion getJavaVersion() {
        return JavaVersion.toVersion(getToolchain().getLanguageVersion().asInt());
    }

    private void configureCompileOptions(DefaultJavaCompileSpec spec) {
        if (compileOptions.getRelease().isPresent()) {
            spec.setRelease(compileOptions.getRelease().get());
        } else {
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
        spec.setCompileOptions(compileOptions);
    }

    private JavaInstallationMetadata getToolchain() {
        return getJavaCompiler().get().getMetadata();
    }

    private File getTemporaryDirWithoutCreating() {
        // Do not create the temporary folder, since that causes problems.
        return getServices().get(TemporaryFileProvider.class).newTemporaryFile(getName());
    }

    /**
     * Returns the module path handling of this compile task.
     *
     * @since 6.4
     */
    @Nested
    public ModularitySpec getModularity() {
        return modularity;
    }

    /**
     * Returns the compilation options.
     *
     * @return The compilation options.
     */
    @Nested
    public CompileOptions getOptions() {
        return compileOptions;
    }

    @Override
    @CompileClasspath
    @Incremental
    public FileCollection getClasspath() {
        return super.getClasspath();
    }

    /**
     * The sources for incremental change detection.
     *
     * @since 6.0
     */
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @NormalizeLineEndings
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    protected FileCollection getStableSources() {
        return stableSources;
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected JavaToolchainService getJavaToolchainService() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ProviderFactory getProviderFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected IncrementalCompilerFactory getIncrementalCompilerFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected JavaModuleDetector getJavaModuleDetector() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected Deleter getDeleter() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ProjectLayout getProjectLayout() {
        throw new UnsupportedOperationException();
    }
}
