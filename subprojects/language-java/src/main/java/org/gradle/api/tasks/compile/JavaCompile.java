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

import org.gradle.api.AntBuilder;
import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.changedetection.changes.IncrementalTaskInputsInternal;
import org.gradle.api.internal.changedetection.state.CachingFileHasher;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec;
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpecFactory;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.IncrementalCompilerFactory;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassAnalysisCache;
import org.gradle.api.internal.tasks.compile.incremental.cache.CompileCaches;
import org.gradle.api.internal.tasks.compile.incremental.cache.GeneralCompileCaches;
import org.gradle.api.internal.tasks.compile.incremental.deps.LocalClassSetAnalysisStore;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.jar.LocalJarClasspathSnapshotStore;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.cache.CacheRepository;
import org.gradle.internal.Factory;
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.jvm.toolchain.JavaToolChain;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerUtil;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.SingleMessageLogger;

import javax.inject.Inject;
import java.io.File;

/**
 * Compiles Java source files.
 *
 * <pre autoTested=''>
 *     apply plugin: 'java'
 *     compileJava {
 *         //enable compilation in a separate daemon process
 *         options.fork = true
 *
 *         //enable incremental compilation
 *         options.incremental = true
 *     }
 * </pre>
 */
@ParallelizableTask
@CacheableTask
public class JavaCompile extends AbstractCompile {
    private File dependencyCacheDir;
    private final CompileOptions compileOptions = new CompileOptions();

    public JavaCompile() {
        getOutputs().doNotCacheIf(new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task task) {
                return DeprecationLogger.whileDisabled(new Factory<Boolean>() {
                    @Override
                    @SuppressWarnings("deprecation")
                    public Boolean create() {
                        return compileOptions.isUseDepend();
                    }
                });
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public FileTree getSource() {
        return super.getSource();
    }

    /**
     * Returns the tool chain that will be used to compile the Java source.
     *
     * @return The tool chain.
     */
    @Incubating @Inject
    public JavaToolChain getToolChain() {
        // Implementation is generated
        throw new UnsupportedOperationException();
    }

    /**
     * Sets the tool chain that should be used to compile the Java source.
     *
     * @param toolChain The tool chain.
     */
    @Incubating
    public void setToolChain(JavaToolChain toolChain) {
        // Implementation is generated
        throw new UnsupportedOperationException();
    }

    @TaskAction
    protected void compile(IncrementalTaskInputs inputs) {
        if (!compileOptions.isIncremental()) {
            compile();
            return;
        }

        SingleMessageLogger.incubatingFeatureUsed("Incremental java compilation");

        DefaultJavaCompileSpec spec = createSpec();
        final CacheRepository cacheRepository = getCacheRepository();
        final GeneralCompileCaches generalCompileCaches = getGeneralCompileCaches();

        CompileCaches compileCaches = new CompileCaches() {
            private final CacheRepository repository = cacheRepository;
            private final JavaCompile javaCompile = JavaCompile.this;
            private final GeneralCompileCaches generalCaches = generalCompileCaches;

            public ClassAnalysisCache getClassAnalysisCache() {
                return generalCaches.getClassAnalysisCache();
            }

            public JarSnapshotCache getJarSnapshotCache() {
                return generalCaches.getJarSnapshotCache();
            }

            public LocalJarClasspathSnapshotStore getLocalJarClasspathSnapshotStore() {
                return new LocalJarClasspathSnapshotStore(repository, javaCompile);
            }

            public LocalClassSetAnalysisStore getLocalClassSetAnalysisStore() {
                return new LocalClassSetAnalysisStore(repository, javaCompile);
            }
        };
        IncrementalCompilerFactory factory = new IncrementalCompilerFactory(
            getFileOperations(), getCachingFileHasher(), getPath(), createCompiler(spec), source, compileCaches, (IncrementalTaskInputsInternal) inputs);
        Compiler<JavaCompileSpec> compiler = factory.createCompiler();
        performCompilation(spec, compiler);
    }

    @Inject
    protected CachingFileHasher getCachingFileHasher() {
        throw new UnsupportedOperationException();
    }

    @Inject protected FileOperations getFileOperations() {
        throw new UnsupportedOperationException();
    }

    @Inject protected GeneralCompileCaches getGeneralCompileCaches() {
        throw new UnsupportedOperationException();
    }

    @Inject protected CacheRepository getCacheRepository() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void compile() {
        DefaultJavaCompileSpec spec = createSpec();
        performCompilation(spec, createCompiler(spec));
    }

    @Inject
    protected Factory<AntBuilder> getAntBuilderFactory() {
        throw new UnsupportedOperationException();
    }

    private CleaningJavaCompiler createCompiler(JavaCompileSpec spec) {
        Compiler<JavaCompileSpec> javaCompiler = CompilerUtil.castCompiler(((JavaToolChainInternal) getToolChain()).select(getPlatform()).newCompiler(spec.getClass()));
        return new CleaningJavaCompiler(javaCompiler, getAntBuilderFactory(), getOutputs());
    }

    @Nested
    protected JavaPlatform getPlatform() {
        return DefaultJavaPlatform.current();
    }

    private void performCompilation(JavaCompileSpec spec, Compiler<JavaCompileSpec> compiler) {
        WorkResult result = compiler.execute(spec);
        setDidWork(result.getDidWork());
    }

    @SuppressWarnings("deprecation")
    private DefaultJavaCompileSpec createSpec() {
        final DefaultJavaCompileSpec spec = new DefaultJavaCompileSpecFactory(compileOptions).create();
        spec.setSource(getSource());
        spec.setDestinationDir(getDestinationDir());
        spec.setWorkingDir(getProject().getProjectDir());
        spec.setTempDir(getTemporaryDir());
        spec.setClasspath(getClasspath());
        final File dependencyCacheDir = DeprecationLogger.whileDisabled(new Factory<File>() {
            @Override
            @SuppressWarnings("deprecation")
            public File create() {
                return getDependencyCacheDir();
            }
        });
        spec.setDependencyCacheDir(dependencyCacheDir);
        spec.setTargetCompatibility(getTargetCompatibility());
        spec.setSourceCompatibility(getSourceCompatibility());
        spec.setCompileOptions(compileOptions);
        return spec;
    }

    @Internal
    @Deprecated
    public File getDependencyCacheDir() {
        DeprecationLogger.nagUserOfDiscontinuedMethod("JavaCompile.getDependencyCacheDir()");
        return dependencyCacheDir;
    }

    @Deprecated
    public void setDependencyCacheDir(File dependencyCacheDir) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("JavaCompile.setDependencyCacheDir()");
        this.dependencyCacheDir = dependencyCacheDir;
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
}
