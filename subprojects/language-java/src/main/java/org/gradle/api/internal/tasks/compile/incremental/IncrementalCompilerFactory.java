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
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.CachingClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassAnalysisCache;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.DefaultClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.cache.CompileCaches;
import org.gradle.api.internal.tasks.compile.incremental.cache.GeneralCompileCaches;
import org.gradle.api.internal.tasks.compile.incremental.deps.LocalClassSetAnalysisStore;
import org.gradle.api.internal.tasks.compile.incremental.jar.CachingJarSnapshotter;
import org.gradle.api.internal.tasks.compile.incremental.jar.ClasspathJarFinder;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarClasspathSnapshotFactory;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarClasspathSnapshotMaker;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotter;
import org.gradle.api.internal.tasks.compile.incremental.jar.LocalJarClasspathSnapshotStore;
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.language.base.internal.compile.Compiler;

import java.util.List;

public class IncrementalCompilerFactory {

    private final FileOperations fileOperations;
    private final StreamHasher streamHasher;
    private final FileHasher fileHasher;
    private final AnnotationProcessorDetector annotationProcessorDetector;
    private final GeneralCompileCaches generalCompileCaches;

    public IncrementalCompilerFactory(FileOperations fileOperations, StreamHasher streamHasher, FileHasher fileHasher, AnnotationProcessorDetector annotationProcessorDetector, GeneralCompileCaches generalCompileCaches) {
        this.fileOperations = fileOperations;
        this.streamHasher = streamHasher;
        this.fileHasher = fileHasher;
        this.annotationProcessorDetector = annotationProcessorDetector;
        this.generalCompileCaches = generalCompileCaches;
    }

    public Compiler<JavaCompileSpec> makeIncremental(CleaningJavaCompiler cleaningJavaCompiler, String compileDisplayName, IncrementalTaskInputsInternal inputs, List<Object> source, FileCollection annotationProcessorClasspath) {
        CompileCaches compileCaches = createCompileCaches(compileDisplayName);
        ClassDependenciesAnalyzer analyzer = new CachingClassDependenciesAnalyzer(new DefaultClassDependenciesAnalyzer(), compileCaches.getClassAnalysisCache());
        JarSnapshotter jarSnapshotter = new CachingJarSnapshotter(streamHasher, fileHasher, analyzer, compileCaches.getJarSnapshotCache());
        JarClasspathSnapshotMaker jarClasspathSnapshotMaker = new JarClasspathSnapshotMaker(compileCaches.getLocalJarClasspathSnapshotStore(), new JarClasspathSnapshotFactory(jarSnapshotter), new ClasspathJarFinder(fileOperations));
        CompilationSourceDirs sourceDirs = new CompilationSourceDirs(source);
        SourceToNameConverter sourceToNameConverter = new SourceToNameConverter(sourceDirs);
        RecompilationSpecProvider recompilationSpecProvider = new RecompilationSpecProvider(sourceToNameConverter, fileOperations);
        ClassSetAnalysisUpdater classSetAnalysisUpdater = new ClassSetAnalysisUpdater(compileCaches.getLocalClassSetAnalysisStore(), fileOperations, analyzer, fileHasher);
        IncrementalCompilationInitializer compilationInitializer = new IncrementalCompilationInitializer(fileOperations);
        IncrementalCompilerDecorator incrementalSupport = new IncrementalCompilerDecorator(jarClasspathSnapshotMaker, compileCaches, compilationInitializer, cleaningJavaCompiler, compileDisplayName, recompilationSpecProvider, classSetAnalysisUpdater, sourceDirs, annotationProcessorClasspath, annotationProcessorDetector);
        return incrementalSupport.prepareCompiler(inputs);
    }

    private CompileCaches createCompileCaches(String path) {
        final LocalClassSetAnalysisStore localClassSetAnalysisStore = generalCompileCaches.createLocalClassSetAnalysisStore(path);
        final LocalJarClasspathSnapshotStore localJarClasspathSnapshotStore = generalCompileCaches.createLocalJarClasspathSnapshotStore(path);
        return new CompileCaches() {
            public ClassAnalysisCache getClassAnalysisCache() {
                return generalCompileCaches.getClassAnalysisCache();
            }

            public JarSnapshotCache getJarSnapshotCache() {
                return generalCompileCaches.getJarSnapshotCache();
            }

            public LocalJarClasspathSnapshotStore getLocalJarClasspathSnapshotStore() {
                return localJarClasspathSnapshotStore;
            }

            public LocalClassSetAnalysisStore getLocalClassSetAnalysisStore() {
                return localClassSetAnalysisStore;
            }
        };
    }
}
