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
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.IncrementalCompilerFactory;
import org.gradle.api.internal.tasks.compile.incremental.recomp.JavaRecompilationSpecProvider;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
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
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.CurrentJvmToolchainSpec;
import org.gradle.jvm.toolchain.internal.DefaultToolchainJavaCompiler;
import org.gradle.jvm.toolchain.internal.SpecificInstallationToolchainSpec;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;
import org.gradle.work.NormalizeLineEndings;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

import static com.google.common.base.Preconditions.checkState;

/**
 * Compiles Java source files.
 *
 * <pre class='autoTested'>
 *     plugins {
 *         id 'java'
 *     }
 *
 *     tasks.withType(JavaCompile) {
 *         //enable compilation in a separate daemon process
 *         options.fork = true
 *     }
 * </pre>
 */
@CacheableTask
public class JavaCompile extends AbstractCompile implements HasCompileOptions {
    private final CompileOptions compileOptions;
    private final FileCollection stableSources = getProject().files((Callable<FileTree>) this::getSource);
    private final ModularitySpec modularity;
    private File previousCompilationDataFile;
    private final Property<JavaCompiler> javaCompiler;
    private final ObjectFactory objectFactory;

    public JavaCompile() {
        objectFactory = getProject().getObjects();
        compileOptions = objectFactory.newInstance(CompileOptions.class);
        modularity = objectFactory.newInstance(DefaultModularitySpec.class);
        javaCompiler = objectFactory.property(JavaCompiler.class);
        javaCompiler.finalizeValueOnRead();
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
    @Optional
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
        if (!compileOptions.isIncremental()) {
            performFullCompilation(spec);
        } else {
            performIncrementalCompilation(inputs, spec);
        }
    }

    private void validateConfiguration() {
        if (javaCompiler.isPresent()) {
            checkState(getOptions().getForkOptions().getJavaHome() == null, "Must not use `javaHome` property on `ForkOptions` together with `javaCompiler` property");
            checkState(getOptions().getForkOptions().getExecutable() == null, "Must not use `executable` property on `ForkOptions` together with `javaCompiler` property");
        }
    }

    private void performIncrementalCompilation(InputChanges inputs, DefaultJavaCompileSpec spec) {
        boolean isUsingCliCompiler = isUsingCliCompiler(spec);
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
        throw new UnsupportedOperationException("Decorator takes care of injection");
    }

    @Inject
    protected ProjectLayout getProjectLayout() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected JavaToolchainService getJavaToolchainService() {
        throw new UnsupportedOperationException();
    }

    CleaningJavaCompiler<JavaCompileSpec> createCompiler() {
        Compiler<JavaCompileSpec> javaCompiler = createToolchainCompiler();
        return new CleaningJavaCompiler<>(javaCompiler, getOutputs(), getDeleter());
    }

    private <T extends CompileSpec> Compiler<T> createToolchainCompiler() {
        return spec -> {
            final Provider<JavaCompiler> compilerProvider = getCompilerTool();
            final DefaultToolchainJavaCompiler compiler = (DefaultToolchainJavaCompiler) compilerProvider.get();
            return compiler.execute(spec);
        };
    }

    private Provider<JavaCompiler> getCompilerTool() {
        JavaToolchainSpec explicitToolchain = determineExplicitToolchain();
        if(explicitToolchain == null) {
            if(javaCompiler.isPresent()) {
                return this.javaCompiler;
            } else {
                explicitToolchain = new CurrentJvmToolchainSpec(objectFactory);
            }
        }
        return getJavaToolchainService().compilerFor(explicitToolchain);
    }

    @Nullable
    private JavaToolchainSpec determineExplicitToolchain() {
        final File customJavaHome = getOptions().getForkOptions().getJavaHome();
        if (customJavaHome != null) {
            return new SpecificInstallationToolchainSpec(objectFactory, customJavaHome);
        } else {
            final String customExecutable = getOptions().getForkOptions().getExecutable();
            if (customExecutable != null) {
                final File executable = new File(customExecutable);
                if(executable.exists()) {
                    return new SpecificInstallationToolchainSpec(objectFactory, executable.getParentFile().getParentFile());
                }
            }
        }
        return null;
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

    private WorkResult performCompilation(JavaCompileSpec spec, Compiler<JavaCompileSpec> compiler) {
        WorkResult result = new CompileJavaBuildOperationReportingCompiler(this, compiler, getServices().get(BuildOperationExecutor.class)).execute(spec);
        setDidWork(result.getDidWork());
        return result;
    }

    DefaultJavaCompileSpec createSpec() {
        validateConfiguration();
        List<File> sourcesRoots = CompilationSourceDirs.inferSourceRoots((FileTreeInternal) getStableSources().getAsFileTree());
        JavaModuleDetector javaModuleDetector = getJavaModuleDetector();
        boolean isModule = JavaModuleDetector.isModuleSource(modularity.getInferModulePath().get(), sourcesRoots);
        boolean toolchainCompatibleWithJava8 = isToolchainCompatibleWithJava8();
        boolean isSourcepathUserDefined = compileOptions.getSourcepath() != null && !compileOptions.getSourcepath().isEmpty();

        final DefaultJavaCompileSpec spec = createBaseSpec();

        spec.setDestinationDir(getDestinationDirectory().getAsFile().get());
        spec.setWorkingDir(getProjectLayout().getProjectDirectory().getAsFile());
        spec.setTempDir(getTemporaryDir());
        spec.setCompileClasspath(ImmutableList.copyOf(javaModuleDetector.inferClasspath(isModule, getClasspath())));
        spec.setModulePath(ImmutableList.copyOf(javaModuleDetector.inferModulePath(isModule, getClasspath())));
        if (isModule && !isSourcepathUserDefined) {
            compileOptions.setSourcepath(getProjectLayout().files(sourcesRoots));
        }
        spec.setAnnotationProcessorPath(compileOptions.getAnnotationProcessorPath() == null ? ImmutableList.of() : ImmutableList.copyOf(compileOptions.getAnnotationProcessorPath()));
        configureCompatibilityOptions(spec);
        spec.setSourcesRoots(sourcesRoots);

        if (!toolchainCompatibleWithJava8) {
            spec.getCompileOptions().setHeaderOutputDirectory(null);
        }
        return spec;
    }

    private boolean isToolchainCompatibleWithJava8() {
        return getCompilerTool().get().getMetadata().getLanguageVersion().canCompileOrRun(8);
    }

    @Input
    JavaVersion getJavaVersion() {
        return JavaVersion.toVersion(getCompilerTool().get().getMetadata().getLanguageVersion().asInt());
    }

    private DefaultJavaCompileSpec createBaseSpec() {
        final ForkOptions forkOptions = compileOptions.getForkOptions();
        if (javaCompiler.isPresent()) {
            applyToolchain(forkOptions);
        }
        return new DefaultJavaCompileSpecFactory(compileOptions, getToolchain()).create();
    }

    private void applyToolchain(ForkOptions forkOptions) {
        final JavaInstallationMetadata metadata = getToolchain();
        forkOptions.setJavaHome(metadata.getInstallationPath().getAsFile());
    }

    @Nullable
    private JavaInstallationMetadata getToolchain() {
        return javaCompiler.map(JavaCompiler::getMetadata).getOrNull();
    }

    private void configureCompatibilityOptions(DefaultJavaCompileSpec spec) {
        final JavaInstallationMetadata toolchain = getToolchain();
        if (toolchain != null) {
            if (compileOptions.getRelease().isPresent()) {
                spec.setRelease(compileOptions.getRelease().get());
            } else {
                boolean isSourceOrTargetConfigured = false;
                if (super.getSourceCompatibility() != null) {
                    spec.setSourceCompatibility(getSourceCompatibility());
                    isSourceOrTargetConfigured = true;
                }
                if (super.getTargetCompatibility() != null) {
                    spec.setTargetCompatibility(getTargetCompatibility());
                    isSourceOrTargetConfigured = true;
                }
                if (!isSourceOrTargetConfigured) {
                    JavaLanguageVersion languageVersion = toolchain.getLanguageVersion();
                    if (languageVersion.canCompileOrRun(10)) {
                        spec.setRelease(languageVersion.asInt());
                    } else {
                        String version = languageVersion.toString();
                        spec.setSourceCompatibility(version);
                        spec.setTargetCompatibility(version);
                    }
                }
            }
        } else if (compileOptions.getRelease().isPresent()) {
            spec.setRelease(compileOptions.getRelease().get());
        } else {
            spec.setTargetCompatibility(getTargetCompatibility());
            spec.setSourceCompatibility(getSourceCompatibility());
        }
        spec.setCompileOptions(compileOptions);
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
}
