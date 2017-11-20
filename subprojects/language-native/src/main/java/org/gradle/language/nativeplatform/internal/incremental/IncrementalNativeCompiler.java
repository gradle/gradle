/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.language.nativeplatform.internal.incremental;

import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.changes.DiscoveredInputRecorder;
import org.gradle.api.internal.changedetection.state.FileSystemSnapshotter;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.cache.PersistentStateCache;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.tasks.SimpleStaleClassCleaner;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.CSourceParser;
import org.gradle.nativeplatform.toolchain.Clang;
import org.gradle.nativeplatform.toolchain.Gcc;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;

import java.io.File;

@NonNullApi
public class IncrementalNativeCompiler<T extends NativeCompileSpec> implements Compiler<T> {
    private final Compiler<T> delegateCompiler;
    private final boolean importsAreIncludes;
    private final TaskInternal task;
    private final FileSystemSnapshotter fileSystemSnapshotter;
    private final CompilationStateCacheFactory compilationStateCacheFactory;
    private final CSourceParser sourceParser;
    private final HeaderDependenciesCollector headerDependenciesCollector;

    public IncrementalNativeCompiler(TaskInternal task, FileSystemSnapshotter fileSystemSnapshotter, CompilationStateCacheFactory compilationStateCacheFactory, Compiler<T> delegateCompiler, NativeToolChain toolChain, HeaderDependenciesCollector headerDependenciesCollector, CSourceParser sourceParser) {
        this.task = task;
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        this.compilationStateCacheFactory = compilationStateCacheFactory;
        this.delegateCompiler = delegateCompiler;
        this.importsAreIncludes = Clang.class.isAssignableFrom(toolChain.getClass()) || Gcc.class.isAssignableFrom(toolChain.getClass());
        this.headerDependenciesCollector = headerDependenciesCollector;
        this.sourceParser = sourceParser;
    }

    @Override
    public WorkResult execute(final T spec) {
        PersistentStateCache<CompilationState> compileStateCache = compilationStateCacheFactory.create(task.getPath());

        IncrementalCompileProcessor processor = createProcessor(compileStateCache, createIncrementalCompileFilesFactory(spec));

        IncrementalCompilation compilation = processor.processSourceFiles(spec.getSourceFiles());

        spec.setSourceFileIncludeDirectives(compilation.getSourceFileIncludeDirectives());

        handleDiscoveredInputs(spec, compilation, spec.getDiscoveredInputRecorder());

        WorkResult workResult;
        if (spec.isIncrementalCompile()) {
            workResult = doIncrementalCompile(compilation, spec);
        } else {
            workResult = doCleanIncrementalCompile(spec);
        }

        compileStateCache.set(compilation.getFinalState());

        return workResult;
    }

    private IncrementalCompileFilesFactory createIncrementalCompileFilesFactory(T spec) {
        DefaultSourceIncludesParser sourceIncludesParser = new DefaultSourceIncludesParser(sourceParser, importsAreIncludes);
        DefaultSourceIncludesResolver includesResolver = new DefaultSourceIncludesResolver(spec.getIncludeRoots());
        return new IncrementalCompileFilesFactory(sourceIncludesParser, includesResolver, fileSystemSnapshotter);
    }

    protected void handleDiscoveredInputs(T spec, IncrementalCompilation compilation, final DiscoveredInputRecorder discoveredInputRecorder) {
        ImmutableSortedSet<File> headerDependencies = headerDependenciesCollector.collectHeaderDependencies(getTask().getPath(), spec.getIncludeRoots(), compilation);
        discoveredInputRecorder.newInputs(headerDependencies);
    }

    protected WorkResult doIncrementalCompile(IncrementalCompilation compilation, T spec) {
        // Determine the actual sources to clean/compile
        spec.setSourceFiles(compilation.getRecompile());
        spec.setRemovedSourceFiles(compilation.getRemoved());
        return delegateCompiler.execute(spec);
    }

    protected WorkResult doCleanIncrementalCompile(T spec) {
        boolean deleted = cleanPreviousOutputs(spec);
        WorkResult compileResult = delegateCompiler.execute(spec);
        if (deleted && !compileResult.getDidWork()) {
            return WorkResults.didWork(true);
        }
        return compileResult;
    }

    private boolean cleanPreviousOutputs(NativeCompileSpec spec) {
        SimpleStaleClassCleaner cleaner = new SimpleStaleClassCleaner(getTask().getOutputs());
        cleaner.setDestinationDir(spec.getObjectFileDir());
        cleaner.execute();
        return cleaner.getDidWork();
    }

    protected TaskInternal getTask() {
        return task;
    }

    private IncrementalCompileProcessor createProcessor(PersistentStateCache<CompilationState> compileStateCache, IncrementalCompileFilesFactory incrementalCompileFilesFactory) {
        return new IncrementalCompileProcessor(compileStateCache, incrementalCompileFilesFactory);
    }
}
