/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.changedetection.changes.IncrementalTaskInputsInternal;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.CachingClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.DefaultClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.cache.CompileCaches;
import org.gradle.api.internal.tasks.compile.incremental.jar.CachingJarSnapshotter;
import org.gradle.api.internal.tasks.compile.incremental.jar.ClasspathJarFinder;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarClasspathSnapshotFactory;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarClasspathSnapshotMaker;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotter;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.language.base.internal.compile.Compiler;

import java.util.List;

public class IncrementalCompilerFactory {

    private final IncrementalCompilerDecorator incrementalSupport;
    private final IncrementalTaskInputs inputs;

    public IncrementalCompilerFactory(FileOperations fileOperations, FileHasher cachingFileHasher, String compileDisplayName, CleaningJavaCompiler cleaningJavaCompiler,
                                      List<Object> source, CompileCaches compileCaches, IncrementalTaskInputsInternal inputs, FileCollection annotationProcessorClasspath) {
        this.inputs = inputs;
        //bunch of services that enable incremental java compilation.
        ClassDependenciesAnalyzer analyzer = new CachingClassDependenciesAnalyzer(new DefaultClassDependenciesAnalyzer(), compileCaches.getClassAnalysisCache());
        JarSnapshotter jarSnapshotter = new CachingJarSnapshotter(cachingFileHasher, analyzer, compileCaches.getJarSnapshotCache());
        JarClasspathSnapshotMaker jarClasspathSnapshotMaker = new JarClasspathSnapshotMaker(compileCaches.getLocalJarClasspathSnapshotStore(), new JarClasspathSnapshotFactory(jarSnapshotter), new ClasspathJarFinder(fileOperations));
        CompilationSourceDirs sourceDirs = new CompilationSourceDirs(source);
        SourceToNameConverter sourceToNameConverter = new SourceToNameConverter(sourceDirs); //TODO SF replace with converter that parses input source class
        RecompilationSpecProvider recompilationSpecProvider = new RecompilationSpecProvider(sourceToNameConverter, fileOperations);
        ClassSetAnalysisUpdater classSetAnalysisUpdater = new ClassSetAnalysisUpdater(compileCaches.getLocalClassSetAnalysisStore(), fileOperations, analyzer, cachingFileHasher);
        IncrementalCompilationInitializer compilationInitializer = new IncrementalCompilationInitializer(fileOperations);
        incrementalSupport = new IncrementalCompilerDecorator(jarClasspathSnapshotMaker, compileCaches, compilationInitializer,
                cleaningJavaCompiler, compileDisplayName, recompilationSpecProvider, classSetAnalysisUpdater, sourceDirs, annotationProcessorClasspath);
    }

    public Compiler<JavaCompileSpec> createCompiler() {
        return incrementalSupport.prepareCompiler(inputs);
    }
}
