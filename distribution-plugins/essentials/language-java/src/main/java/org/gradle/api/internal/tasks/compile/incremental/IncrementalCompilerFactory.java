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
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.CachingClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassAnalysisCache;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.DefaultClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.cache.GeneralCompileCaches;
import org.gradle.api.internal.tasks.compile.incremental.cache.TaskScopedCompileCaches;
import org.gradle.api.internal.tasks.compile.incremental.classpath.CachingClasspathEntrySnapshotter;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshotter;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshotFactory;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshotMaker;
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilationOutputAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilationStore;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpecProvider;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.vfs.FileSystemAccess;
import org.gradle.language.base.internal.compile.Compiler;

public class IncrementalCompilerFactory {
    private final FileOperations fileOperations;
    private final StreamHasher streamHasher;
    private final GeneralCompileCaches generalCompileCaches;
    private final BuildOperationExecutor buildOperationExecutor;
    private final StringInterner interner;
    private final FileSystemAccess fileSystemAccess;
    private final FileHasher fileHasher;

    public IncrementalCompilerFactory(FileOperations fileOperations, StreamHasher streamHasher, GeneralCompileCaches generalCompileCaches, BuildOperationExecutor buildOperationExecutor, StringInterner interner, FileSystemAccess fileSystemAccess, FileHasher fileHasher) {
        this.fileOperations = fileOperations;
        this.streamHasher = streamHasher;
        this.generalCompileCaches = generalCompileCaches;
        this.buildOperationExecutor = buildOperationExecutor;
        this.interner = interner;
        this.fileSystemAccess = fileSystemAccess;
        this.fileHasher = fileHasher;
    }

    public <T extends JavaCompileSpec> Compiler<T> makeIncremental(CleaningJavaCompiler<T> cleaningJavaCompiler, String taskPath, FileTree sources, RecompilationSpecProvider recompilationSpecProvider) {
        TaskScopedCompileCaches compileCaches = createCompileCaches(taskPath);
        Compiler<T> rebuildAllCompiler = createRebuildAllCompiler(cleaningJavaCompiler, sources);
        ClassDependenciesAnalyzer analyzer = new CachingClassDependenciesAnalyzer(new DefaultClassDependenciesAnalyzer(interner), compileCaches.getClassAnalysisCache());
        ClasspathEntrySnapshotter classpathEntrySnapshotter = new CachingClasspathEntrySnapshotter(fileHasher, streamHasher, fileSystemAccess, analyzer, compileCaches.getClasspathEntrySnapshotCache(), fileOperations);
        ClasspathSnapshotMaker classpathSnapshotMaker = new ClasspathSnapshotMaker(new ClasspathSnapshotFactory(classpathEntrySnapshotter, buildOperationExecutor));
        PreviousCompilationOutputAnalyzer previousCompilationOutputAnalyzer = new PreviousCompilationOutputAnalyzer(fileHasher, streamHasher, analyzer, fileOperations);
        IncrementalCompilerDecorator<T> incrementalSupport = new IncrementalCompilerDecorator<>(classpathSnapshotMaker, compileCaches, cleaningJavaCompiler, rebuildAllCompiler, previousCompilationOutputAnalyzer, interner);
        return incrementalSupport.prepareCompiler(recompilationSpecProvider);
    }

    private TaskScopedCompileCaches createCompileCaches(String path) {
        final PreviousCompilationStore previousCompilationStore = generalCompileCaches.createPreviousCompilationStore(path);
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
            public PreviousCompilationStore getPreviousCompilationStore() {
                return previousCompilationStore;
            }

        };
    }

    private <T extends JavaCompileSpec> Compiler<T> createRebuildAllCompiler(CleaningJavaCompiler<T> cleaningJavaCompiler, FileTree sourceFiles) {
        return spec -> {
            spec.setSourceFiles(sourceFiles);
            return cleaningJavaCompiler.execute(spec);
        };
    }
}
