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

import org.gradle.api.AntBuilder;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.hash.DefaultHasher;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.*;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.api.internal.tasks.compile.incremental.*;
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfoExtractor;
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfoSerializer;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.internal.Factory;
import org.gradle.util.Clock;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.SingleMessageLogger;

import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * Compiles Java source files.
 *
 * @deprecated This class has been replaced by {@link JavaCompile}.
 */
@Deprecated
public class Compile extends AbstractCompile {

    private static final Logger LOG = Logging.getLogger(Compile.class);

    private Compiler<JavaCompileSpec> cleaningCompiler;
    private File dependencyCacheDir;
    private final CompileOptions compileOptions = new CompileOptions();
    private final Compiler<JavaCompileSpec> javaCompiler;
    private final IncrementalCompilationSupport incrementalCompilation;

    public Compile() {
        if (!(this instanceof JavaCompile)) {
            DeprecationLogger.nagUserOfReplacedTaskType("Compile", "JavaCompile task type");
        }
        Factory<AntBuilder> antBuilderFactory = getServices().getFactory(AntBuilder.class);
        JavaCompilerFactory inProcessCompilerFactory = new InProcessJavaCompilerFactory();
        ProjectInternal projectInternal = (ProjectInternal) getProject();
        CompilerDaemonManager compilerDaemonManager = getServices().get(CompilerDaemonManager.class);
        JavaCompilerFactory defaultCompilerFactory = new DefaultJavaCompilerFactory(projectInternal, antBuilderFactory, inProcessCompilerFactory, compilerDaemonManager);
        javaCompiler = new DelegatingJavaCompiler(defaultCompilerFactory);
        cleaningCompiler = new CleaningJavaCompiler(javaCompiler, antBuilderFactory, getOutputs());
        JarSnapshotFeeder jarSnapshotFeeder = new JarSnapshotFeeder(getJarSnapshotCache(), new JarSnapshotter(new DefaultHasher()));
        incrementalCompilation = new IncrementalCompilationSupport(jarSnapshotFeeder);
    }

    private FileCollection compileClasspath; //TODO SF remove this hack

    @TaskAction
    protected void compile(IncrementalTaskInputs inputs) {
        if (!maybeCompileIncrementally(inputs)) {
            compile();
        }
        incrementalCompilation.compilationComplete(compileOptions,
                new ClassDependencyInfoExtractor(getDestinationDir()),
                getDependencyInfoSerializer(), Collections.<JarArchive>emptyList());
    }

    private ClassDependencyInfoSerializer getDependencyInfoSerializer() {
        return new ClassDependencyInfoSerializer(new File(getProject().getBuildDir(), "class-info.bin"));
    }

    private boolean maybeCompileIncrementally(IncrementalTaskInputs inputs) {
        if (!compileOptions.isIncremental()) {
            return false;
        }
        //hack
        List<File> sourceDirs = getSourceDirs();
        if (sourceDirs.isEmpty()) {
            LOG.lifecycle("{} cannot run incrementally because Gradle cannot infer the source directories.", getPath());
            return false;
        }
        if (!inputs.isIncremental()) {
            LOG.lifecycle("{} is not incremental (e.g. outputs have changed, no previous execution, etc). Using regular compile.", getPath());
            return false;
        }
        ClassDependencyInfoSerializer dependencyInfoSerializer = getDependencyInfoSerializer();
        if (!dependencyInfoSerializer.isInfoAvailable()) {
            //TODO SF let's unit test a scenario when after regular compilation incremental compilation is scheduled
            LOG.lifecycle("{} is not incremental because there is no class dependency data left from previous incremental build.", getPath());
            return false;
        }

        SingleMessageLogger.incubatingFeatureUsed("Incremental java compilation");

        SelectiveJavaCompiler compiler = new SelectiveJavaCompiler(javaCompiler, getProject().fileTree(getDestinationDir()));
        SelectiveCompilation selectiveCompilation = new SelectiveCompilation(inputs, getSource(), getClasspath(), getDestinationDir(),
                dependencyInfoSerializer, getJarSnapshotCache(), compiler, sourceDirs, (FileOperations) getProject());

        if (!selectiveCompilation.getCompilationNeeded()) {
            LOG.lifecycle("{} does not require recompilation. Skipping the compiler.", getPath());
            return true;
        }

        Clock clock = new Clock();
        performCompilation(selectiveCompilation.getSource(), selectiveCompilation.getClasspath(), selectiveCompilation.getFullRebuildRequired()? cleaningCompiler : compiler);
        LOG.lifecycle("{} - incremental compilation took {}", getPath(), clock.getTime());

        return true;
    }

    private List<File> getSourceDirs() {
        List<File> sourceDirs = new LinkedList<File>();
        for (Object s : source) {
            if (s instanceof SourceDirectorySet) {
                sourceDirs.addAll(((SourceDirectorySet) s).getSrcDirs());
            } else {
                return emptyList();
            }
        }
        return sourceDirs;
    }

    protected void compile() {
        FileTree source = getSource();
        FileCollection classpath = getClasspath();

        performCompilation(source, classpath, cleaningCompiler);
    }

    private void performCompilation(FileCollection source, FileCollection classpath, Compiler<JavaCompileSpec> compiler) {
        DefaultJavaCompileSpec spec = new DefaultJavaCompileSpec();
        spec.setSource(source);
        spec.setDestinationDir(getDestinationDir());
        spec.setClasspath(classpath);
        spec.setDependencyCacheDir(getDependencyCacheDir());
        spec.setSourceCompatibility(getSourceCompatibility());
        spec.setTargetCompatibility(getTargetCompatibility());
        spec.setCompileOptions(compileOptions);
        WorkResult result = compiler.execute(spec);
        setDidWork(result.getDidWork());
        compileClasspath = classpath;
    }

    private JarSnapshotCache getJarSnapshotCache() {
        //hack, needs fixing
        return new JarSnapshotCache(new File(getProject().getRootProject().getProjectDir(), ".gradle/jar-snapshot-cache.bin"));
    }

    @OutputDirectory
    public File getDependencyCacheDir() {
        return dependencyCacheDir;
    }

    public void setDependencyCacheDir(File dependencyCacheDir) {
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

    /**
     * This method was never intended to be used by the users.
     *
     * @return the compiler
     */
    @Deprecated
    public Compiler<JavaCompileSpec> getJavaCompiler() {
        DeprecationLogger.nagUserOfDiscontinuedMethod("Compile.getJavaCompiler()");
        return cleaningCompiler;
    }

    /**
     * This method was never intended to be used by the users.
     *
     * @param javaCompiler to set
     */
    @Deprecated
    public void setJavaCompiler(Compiler<JavaCompileSpec> javaCompiler) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("Compile.setJavaCompiler(Compiler<JavaCompileSpec>)");
        this.cleaningCompiler = javaCompiler;
    }
}
