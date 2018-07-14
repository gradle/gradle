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

import org.gradle.api.file.FileTree;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.FileSystemSnapshotter;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.CachingClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassAnalysisCache;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.DefaultClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.cache.GeneralCompileCaches;
import org.gradle.api.internal.tasks.compile.incremental.cache.TaskScopedCompileCaches;
import org.gradle.api.internal.tasks.compile.incremental.deps.LocalClassSetAnalysisStore;
import org.gradle.api.internal.tasks.compile.incremental.classpath.CachingClasspathEntrySnapshotter;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntryConverter;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshotFactory;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshotMaker;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshotter;
import org.gradle.api.internal.tasks.compile.incremental.classpath.LocalClasspathSnapshotStore;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessorPathStore;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.language.base.internal.compile.Compiler;

public class IncrementalCompilerFactory {

    private final FileOperations fileOperations;
    private final StreamHasher streamHasher;
    private final FileHasher fileHasher;
    private final GeneralCompileCaches generalCompileCaches;
    private final BuildOperationExecutor buildOperationExecutor;
    private final StringInterner interner;
    private final FileSystemSnapshotter fileSystemSnapshotter;

    public IncrementalCompilerFactory(FileOperations fileOperations, StreamHasher streamHasher, FileHasher fileHasher, GeneralCompileCaches generalCompileCaches, BuildOperationExecutor buildOperationExecutor, StringInterner interner, FileSystemSnapshotter fileSystemSnapshotter) {
        this.fileOperations = fileOperations;
        this.streamHasher = streamHasher;
        this.fileHasher = fileHasher;
        this.generalCompileCaches = generalCompileCaches;
        this.buildOperationExecutor = buildOperationExecutor;
        this.interner = interner;
        this.fileSystemSnapshotter = fileSystemSnapshotter;
    }

    public Compiler<JavaCompileSpec> makeIncremental(CleaningJavaCompiler cleaningJavaCompiler, String taskPath, IncrementalTaskInputs inputs, FileTree sources) {
        TaskScopedCompileCaches compileCaches = createCompileCaches(taskPath);
        Compiler<JavaCompileSpec> rebuildAllCompiler = createRebuildAllCompiler(cleaningJavaCompiler, sources);
        ClassDependenciesAnalyzer analyzer = new CachingClassDependenciesAnalyzer(new DefaultClassDependenciesAnalyzer(interner), compileCaches.getClassAnalysisCache());
        ClasspathEntrySnapshotter classpathEntrySnapshotter = new CachingClasspathEntrySnapshotter(streamHasher, fileSystemSnapshotter, analyzer, compileCaches.getClasspathEntrySnapshotCache());
        ClasspathSnapshotMaker classpathSnapshotMaker = new ClasspathSnapshotMaker(compileCaches.getLocalClasspathSnapshotStore(), new ClasspathSnapshotFactory(classpathEntrySnapshotter, buildOperationExecutor), new ClasspathEntryConverter(fileOperations));
        CompilationSourceDirs sourceDirs = new CompilationSourceDirs((FileTreeInternal) sources);
        SourceToNameConverter sourceToNameConverter = new SourceToNameConverter(sourceDirs);
        RecompilationSpecProvider recompilationSpecProvider = new RecompilationSpecProvider(sourceToNameConverter, fileOperations);
        ClassSetAnalysisUpdater classSetAnalysisUpdater = new ClassSetAnalysisUpdater(compileCaches.getLocalClassSetAnalysisStore(), fileOperations, analyzer, fileHasher);
        IncrementalCompilationInitializer compilationInitializer = new IncrementalCompilationInitializer(fileOperations, sources);
        IncrementalCompilerDecorator incrementalSupport = new IncrementalCompilerDecorator(classpathSnapshotMaker, compileCaches, compilationInitializer, cleaningJavaCompiler, recompilationSpecProvider, classSetAnalysisUpdater, sourceDirs, rebuildAllCompiler);
        return incrementalSupport.prepareCompiler(inputs);
    }

    private TaskScopedCompileCaches createCompileCaches(String path) {
        final LocalClassSetAnalysisStore localClassSetAnalysisStore = generalCompileCaches.createLocalClassSetAnalysisStore(path);
        final LocalClasspathSnapshotStore localClasspathSnapshotStore = generalCompileCaches.createLocalClasspathSnapshotStore(path);
        final AnnotationProcessorPathStore annotationProcessorPathStore = generalCompileCaches.createAnnotationProcessorPathStore(path);
        return new TaskScopedCompileCaches() {
            @Override
            public ClassAnalysisCache getClassAnalysisCache() {
                return generalCompileCaches.getClassAnalysisCache();
            }

            @Override
            public ClasspathEntrySnapshotCache getClasspathEntrySnapshotCache() {
                return generalCompileCaches.getClasspathEntrySnapshotCache();
            }

            @Override
            public LocalClasspathSnapshotStore getLocalClasspathSnapshotStore() {
                return localClasspathSnapshotStore;
            }

            @Override
            public LocalClassSetAnalysisStore getLocalClassSetAnalysisStore() {
                return localClassSetAnalysisStore;
            }

            @Override
            public AnnotationProcessorPathStore getAnnotationProcessorPathStore() {
                return annotationProcessorPathStore;
            }
        };
    }

    private Compiler<JavaCompileSpec> createRebuildAllCompiler(final CleaningJavaCompiler cleaningJavaCompiler, final FileTree sourceFiles) {
        return new Compiler<JavaCompileSpec>() {
            @Override
            public WorkResult execute(JavaCompileSpec spec) {
                spec.setSourceFiles(sourceFiles);
                return cleaningJavaCompiler.execute(spec);
            }
        };
    }
}
