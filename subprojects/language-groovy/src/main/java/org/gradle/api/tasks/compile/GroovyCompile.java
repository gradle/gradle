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
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.tasks.JavaToolChainFactory;
import org.gradle.api.internal.tasks.compile.CleaningGroovyCompiler;
import org.gradle.api.internal.tasks.compile.CompilerForkUtils;
import org.gradle.api.internal.tasks.compile.DefaultGroovyJavaJointCompileSpec;
import org.gradle.api.internal.tasks.compile.DefaultGroovyJavaJointCompileSpecFactory;
import org.gradle.api.internal.tasks.compile.GroovyCompilerFactory;
import org.gradle.api.internal.tasks.compile.GroovyJavaJointCompileSpec;
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.jvm.toolchain.JavaToolChain;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider;
import org.gradle.util.GFileUtils;
import org.gradle.work.FileChange;
import org.gradle.work.InputChanges;
import org.gradle.workers.internal.IsolatedClassloaderWorkerFactory;
import org.gradle.workers.internal.WorkerDaemonFactory;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Compiles Groovy source files, and optionally, Java source files.
 */
@CacheableTask
public class GroovyCompile extends AbstractCompile {
    private Compiler<GroovyJavaJointCompileSpec> compiler;
    private FileCollection groovyClasspath;
    private final CompileOptions compileOptions;
    private final GroovyCompileOptions groovyCompileOptions = new GroovyCompileOptions();
    private final ConfigurableFileCollection astTransformClasspath;
    private final FileCollection stableSources = getProject().files(new Callable<FileTree>() {
        @Override
        public FileTree call() {
            return getSource();
        }
    });

    public GroovyCompile() {
        ObjectFactory objectFactory = getServices().get(ObjectFactory.class);
        CompileOptions compileOptions = objectFactory.newInstance(CompileOptions.class);
        this.compileOptions = compileOptions;
        this.astTransformClasspath = objectFactory.fileCollection();
        CompilerForkUtils.doNotCacheIfForkingViaExecutable(compileOptions, getOutputs());
        boolean enableCompileAvoidance = Boolean.getBoolean("org.gradle.unsafe.groovy.compile.avoidance");
        if (!enableCompileAvoidance) {
            astTransformClasspath.from(new Callable<FileCollection>() {
                @Override
                public FileCollection call() {
                    return getClasspath();
                }
            });
        }
    }

    @Override
    protected void compile() {
        checkGroovyClasspathIsNonEmpty();
        DefaultGroovyJavaJointCompileSpec spec = createSpec();
        WorkResult result = getCompiler(spec, false).execute(spec);
        setDidWork(result.getDidWork());
    }

    @TaskAction
    protected void compile(InputChanges inputChanges) {
        System.out.println("Incremental compilation: " + inputChanges.isIncremental());
        if (!inputChanges.isIncremental() || !getOptions().isIncremental()) {
            compile();
        } else {
            List<File> sourceFilesToCompile = new ArrayList<File>();
            for (FileChange fileChange : inputChanges.getFileChanges(getStableSources())) {
                String normalizedPath = fileChange.getNormalizedPath();
                String className = FilenameUtils.removeExtension(normalizedPath) + ".class";
                File outputFile = new File(getDestinationDir(), className);
                switch (fileChange.getChangeType()) {
                    case REMOVED:
                        System.out.println("Deleting " + outputFile);
                        outputFile.delete();
                        break;
                    case MODIFIED:
                        System.out.println("Deleting " + outputFile);
                        outputFile.delete();
                        sourceFilesToCompile.add(fileChange.getFile());
                        break;
                    case ADDED:
                        sourceFilesToCompile.add(fileChange.getFile());
                        break;
                }
            }
            DefaultGroovyJavaJointCompileSpec spec = createSpec(sourceFilesToCompile);
            WorkResult result = getCompiler(spec, true).execute(spec);
            setDidWork(result.getDidWork());
        }
    }

    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE) // Java source files are supported, too. Therefore we should care about the relative path.
    @InputFiles
    protected FileCollection getStableSources() {
        return stableSources;
    }

    private Compiler<GroovyJavaJointCompileSpec> getCompiler(GroovyJavaJointCompileSpec spec, boolean incremental) {
        if (compiler == null) {
            WorkerDaemonFactory workerDaemonFactory = getServices().get(WorkerDaemonFactory.class);
            IsolatedClassloaderWorkerFactory inProcessWorkerFactory = getServices().get(IsolatedClassloaderWorkerFactory.class);
            JavaForkOptionsFactory forkOptionsFactory = getServices().get(JavaForkOptionsFactory.class);
            AnnotationProcessorDetector processorDetector = getServices().get(AnnotationProcessorDetector.class);
            JvmVersionDetector jvmVersionDetector = getServices().get(JvmVersionDetector.class);
            WorkerDirectoryProvider workerDirectoryProvider = getServices().get(WorkerDirectoryProvider.class);
            ClassPathRegistry classPathRegistry = getServices().get(ClassPathRegistry.class);
            GroovyCompilerFactory groovyCompilerFactory = new GroovyCompilerFactory(workerDaemonFactory, inProcessWorkerFactory, forkOptionsFactory, processorDetector, jvmVersionDetector, workerDirectoryProvider, classPathRegistry);
            Compiler<GroovyJavaJointCompileSpec> delegatingCompiler = groovyCompilerFactory.newCompiler(spec);
            compiler = incremental ? delegatingCompiler : new CleaningGroovyCompiler(delegatingCompiler, getOutputs());
        }
        return compiler;
    }

    private DefaultGroovyJavaJointCompileSpec createSpec() {
        return createSpec(getSource());
    }

    private DefaultGroovyJavaJointCompileSpec createSpec(Iterable<File> source) {
        DefaultGroovyJavaJointCompileSpec spec = new DefaultGroovyJavaJointCompileSpecFactory(compileOptions).create();
        spec.setSourceFiles(source);
        spec.setDestinationDir(getDestinationDir());
        spec.setWorkingDir(getProject().getProjectDir());
        spec.setTempDir(getTemporaryDir());
        spec.setCompileClasspath(ImmutableList.copyOf(getClasspath()));
        spec.setSourceCompatibility(getSourceCompatibility());
        spec.setTargetCompatibility(getTargetCompatibility());
        spec.setAnnotationProcessorPath(Lists.newArrayList(compileOptions.getAnnotationProcessorPath() == null ? getProject().getLayout().files() : compileOptions.getAnnotationProcessorPath()));
        spec.setGroovyClasspath(Lists.newArrayList(getGroovyClasspath()));
        spec.setCompileOptions(compileOptions);
        spec.setGroovyCompileOptions(groovyCompileOptions);
        if (spec.getGroovyCompileOptions().getStubDir() == null) {
            File dir = new File(getTemporaryDir(), "groovy-java-stubs");
            GFileUtils.mkdirs(dir);
            spec.getGroovyCompileOptions().setStubDir(dir);
        }
        return spec;
    }

    @Override
    @CompileClasspath
    public FileCollection getClasspath() {
        return super.getClasspath();
    }

    @Classpath
    public ConfigurableFileCollection getAstTransformClasspath() {
        return astTransformClasspath;
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
        return JavaVersion.current().getMajorVersion();
    }

    /**
     * We need to track the toolchain used by the Groovy compiler to compile Java sources.
     *
     * @since 4.0
     */
    @Nested
    protected JavaToolChain getJavaToolChain() {
        return getJavaToolChainFactory().forCompileOptions(getOptions());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
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

    @Internal
    public Compiler<GroovyJavaJointCompileSpec> getCompiler() {
        return getCompiler(createSpec(), false);
    }

    public void setCompiler(Compiler<GroovyJavaJointCompileSpec> compiler) {
        this.compiler = compiler;
    }

    @Inject
    protected JavaToolChainFactory getJavaToolChainFactory() {
        throw new UnsupportedOperationException();
    }
}
