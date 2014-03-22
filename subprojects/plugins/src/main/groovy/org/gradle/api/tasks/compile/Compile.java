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
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.hash.DefaultHasher;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.*;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.api.internal.tasks.compile.incremental.CompilationSourceDirs;
import org.gradle.api.internal.tasks.compile.incremental.IncrementalCompilationSupport;
import org.gradle.api.internal.tasks.compile.incremental.RecompilationSpecProvider;
import org.gradle.api.internal.tasks.compile.incremental.SourceToNameConverter;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfoExtractor;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfoSerializer;
import org.gradle.api.internal.tasks.compile.incremental.jar.ClassSnapshotter;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotFeeder;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotter;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.internal.Factory;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.SingleMessageLogger;

import java.io.File;

/**
 * Compiles Java source files.
 *
 * @deprecated This class has been replaced by {@link JavaCompile}.
 */
@Deprecated
public class Compile extends AbstractCompile {

    private Compiler<JavaCompileSpec> cleaningCompiler;
    private File dependencyCacheDir;
    private final CompileOptions compileOptions = new CompileOptions();

    public Compile() {
        if (!(this instanceof JavaCompile)) {
            DeprecationLogger.nagUserOfReplacedTaskType("Compile", "JavaCompile task type");
        }
        Factory<AntBuilder> antBuilderFactory = getServices().getFactory(AntBuilder.class);
        JavaCompilerFactory inProcessCompilerFactory = new InProcessJavaCompilerFactory();
        ProjectInternal projectInternal = (ProjectInternal) getProject();
        CompilerDaemonManager compilerDaemonManager = getServices().get(CompilerDaemonManager.class);
        JavaCompilerFactory defaultCompilerFactory = new DefaultJavaCompilerFactory(projectInternal, antBuilderFactory, inProcessCompilerFactory, compilerDaemonManager);
        DelegatingJavaCompiler javaCompiler = new DelegatingJavaCompiler(defaultCompilerFactory);
        cleaningCompiler = new CleaningJavaCompiler(javaCompiler, antBuilderFactory, getOutputs());
    }

    @TaskAction
    protected void compile(IncrementalTaskInputs inputs) {
        if (!compileOptions.isIncremental()) {
            compile();
            return;
        }

        SingleMessageLogger.incubatingFeatureUsed("Incremental java compilation");

        //bunch of services that enable incremental java compilation. Should be pushed out to services/factories.
        ClassDependenciesAnalyzer analyzer = new ClassDependenciesAnalyzer(); //TODO SF needs caching
        ClassDependencyInfoExtractor extractor = new ClassDependencyInfoExtractor(analyzer);
        JarSnapshotCache jarSnapshotCache = new JarSnapshotCache(new File(getProject().getRootProject().getProjectDir(), ".gradle/jar-snapshot-cache.bin")); //TODO SF cannot be global
        JarSnapshotFeeder jarSnapshotFeeder = new JarSnapshotFeeder(jarSnapshotCache, new JarSnapshotter(new ClassSnapshotter(new DefaultHasher(), analyzer)));
        ClassDependencyInfoSerializer dependencyInfoSerializer = new ClassDependencyInfoSerializer(new File(getProject().getBuildDir(), "class-info.bin"));
        CompilationSourceDirs sourceDirs = new CompilationSourceDirs(source);
        SourceToNameConverter sourceToNameConverter = new SourceToNameConverter(sourceDirs); //can be replaced with converter that parses input source class
        RecompilationSpecProvider staleClassDetecter = new RecompilationSpecProvider(sourceToNameConverter, dependencyInfoSerializer, (FileOperations) getProject(), jarSnapshotFeeder);
        IncrementalCompilationSupport incrementalSupport = new IncrementalCompilationSupport(jarSnapshotFeeder, dependencyInfoSerializer, (FileOperations) getProject(),
                extractor, (CleaningJavaCompiler) cleaningCompiler, getPath(), staleClassDetecter);
        Compiler<JavaCompileSpec> compiler = incrementalSupport.prepareCompiler(inputs, sourceDirs);
        performCompilation(compiler);
    }

    protected void compile() {
        performCompilation(cleaningCompiler);
    }

    private void performCompilation(Compiler<JavaCompileSpec> compiler) {
        DefaultJavaCompileSpec spec = new DefaultJavaCompileSpec();
        spec.setSource(getSource());
        spec.setDestinationDir(getDestinationDir());
        spec.setClasspath(getClasspath());
        spec.setDependencyCacheDir(getDependencyCacheDir());
        spec.setSourceCompatibility(getSourceCompatibility());
        spec.setTargetCompatibility(getTargetCompatibility());
        spec.setCompileOptions(compileOptions);
        WorkResult result = compiler.execute(spec);
        setDidWork(result.getDidWork());
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

    public Compiler<JavaCompileSpec> getJavaCompiler() {
        return cleaningCompiler;
    }

    public void setJavaCompiler(Compiler<JavaCompileSpec> javaCompiler) {
        this.cleaningCompiler = javaCompiler;
    }
}
