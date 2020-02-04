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
import org.gradle.api.Incubating;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.JavaToolChainFactory;
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.CompileJavaBuildOperationReportingCompiler;
import org.gradle.api.internal.tasks.compile.CompilerForkUtils;
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec;
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpecFactory;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.IncrementalCompilerFactory;
import org.gradle.api.internal.tasks.compile.incremental.recomp.CompilationSourceDirs;
import org.gradle.api.internal.tasks.compile.incremental.recomp.JavaRecompilationSpecProvider;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.jvm.toolchain.JavaToolChain;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerUtil;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;

import javax.inject.Inject;
import java.util.concurrent.Callable;

/**
 * Compiles Java source files.
 *
 * <pre class='autoTested'>
 *     apply plugin: 'java'
 *
 *     tasks.withType(JavaCompile) {
 *         //enable compilation in a separate daemon process
 *         options.fork = true
 *     }
 * </pre>
 */
@CacheableTask
public class JavaCompile extends AbstractCompile {
    private final CompileOptions compileOptions;
    private JavaToolChain toolChain;
    private final FileCollection stableSources = getProject().files(new Callable<Object[]>() {
        @Override
        public Object[] call() {
            return new Object[]{getSource(), getSources()};
        }
    });

    public JavaCompile() {
        CompileOptions compileOptions = getProject().getObjects().newInstance(CompileOptions.class);
        this.compileOptions = compileOptions;
        CompilerForkUtils.doNotCacheIfForkingViaExecutable(compileOptions, getOutputs());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @ReplacedBy("stableSources")
    public FileTree getSource() {
        return super.getSource();
    }

    /**
     * This method is overwritten by the Android plugin &lt; 3.6.
     * We add it here as hack so we can add the source from that method to stableSources.
     * DO NOT USE!
     *
     * @since 6.0
     */
    @Deprecated
    @Internal
    protected FileTree getSources() {
        return getProject().getLayout().files().getAsFileTree();
    }

    /**
     * Returns the tool chain that will be used to compile the Java source.
     *
     * @return The tool chain.
     */
    @Nested
    public JavaToolChain getToolChain() {
        if (toolChain != null) {
            return toolChain;
        }
        return getJavaToolChainFactory().forCompileOptions(getOptions());
    }

    /**
     * Sets the tool chain that should be used to compile the Java source.
     *
     * @param toolChain The tool chain.
     */
    public void setToolChain(JavaToolChain toolChain) {
        this.toolChain = toolChain;
    }

    /**
     * Compile the sources, taking into account the changes reported by inputs.
     *
     * @deprecated Left for backwards compatibility.
     */
    @Deprecated
    @TaskAction
    protected void compile(@SuppressWarnings("deprecation") org.gradle.api.tasks.incremental.IncrementalTaskInputs inputs) {
        DeprecationLogger.deprecate("Extending the JavaCompile task")
            .withAdvice("Configure the task instead.")
            .undocumented()
            .nagUser();
        compile((InputChanges) inputs);
    }

    /**
     * Compile the sources, taking into account the changes reported by inputs.
     *
     * @since 6.0
     */
    @Incubating
    @TaskAction
    protected void compile(InputChanges inputs) {
        DefaultJavaCompileSpec spec;
        Compiler<JavaCompileSpec> compiler;
        if (!compileOptions.isIncremental()) {
            spec = createSpec();
            spec.setSourceFiles(getStableSources());
            compiler = createCompiler(spec);
        } else {
            spec = createSpec();
            FileTree sources = getStableSources().getAsFileTree();
            compiler = getIncrementalCompilerFactory().makeIncremental(
                createCompiler(spec),
                getPath(),
                sources,
                new JavaRecompilationSpecProvider(
                    getDeleter(),
                    ((ProjectInternal) getProject()).getFileOperations(),
                    sources,
                    inputs.isIncremental(),
                    () -> inputs.getFileChanges(getStableSources()).iterator()
                )
            );
        }
        performCompilation(spec, compiler);
    }

    @Inject
    protected IncrementalCompilerFactory getIncrementalCompilerFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected JavaToolChainFactory getJavaToolChainFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected Deleter getDeleter() {
        throw new UnsupportedOperationException("Decorator takes care of injection");
    }

    private CleaningJavaCompiler<JavaCompileSpec> createCompiler(JavaCompileSpec spec) {
        Compiler<JavaCompileSpec> javaCompiler = CompilerUtil.castCompiler(((JavaToolChainInternal) getToolChain()).select(getPlatform()).newCompiler(spec.getClass()));
        return new CleaningJavaCompiler<>(javaCompiler, getOutputs(), getDeleter());
    }

    @Nested
    protected JavaPlatform getPlatform() {
        return new DefaultJavaPlatform(JavaVersion.toVersion(getTargetCompatibility()));
    }

    private void performCompilation(JavaCompileSpec spec, Compiler<JavaCompileSpec> compiler) {
        WorkResult result = new CompileJavaBuildOperationReportingCompiler(this, compiler, getServices().get(BuildOperationExecutor.class)).execute(spec);
        setDidWork(result.getDidWork());
    }

    private DefaultJavaCompileSpec createSpec() {
        final DefaultJavaCompileSpec spec = new DefaultJavaCompileSpecFactory(compileOptions).create();
        spec.setDestinationDir(getDestinationDirectory().getAsFile().get());
        spec.setWorkingDir(getProject().getProjectDir());
        spec.setTempDir(getTemporaryDir());
        spec.setCompileClasspath(ImmutableList.copyOf(getClasspath()));
        spec.setAnnotationProcessorPath(compileOptions.getAnnotationProcessorPath() == null ? ImmutableList.of() : ImmutableList.copyOf(compileOptions.getAnnotationProcessorPath()));
        spec.setTargetCompatibility(getTargetCompatibility());
        spec.setSourceCompatibility(getSourceCompatibility());
        spec.setCompileOptions(compileOptions);
        spec.setSourcesRoots(CompilationSourceDirs.inferSourceRoots((FileTreeInternal) getStableSources().getAsFileTree()));
        return spec;
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
    @Incubating
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    protected FileCollection getStableSources() {
        return stableSources;
    }
}
