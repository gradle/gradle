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

import com.google.common.collect.ImmutableList;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.changedetection.state.FileSystemSnapshotter;
import org.gradle.api.internal.file.TaskFileVarFactory;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.tasks.LifecycleAwareTaskProperty;
import org.gradle.cache.PersistentStateCache;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.CSourceParser;
import org.gradle.nativeplatform.toolchain.Clang;
import org.gradle.nativeplatform.toolchain.Gcc;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;

import java.io.File;
import java.util.List;
import java.util.Set;

public class DefaultIncrementalCompilerBuilder implements IncrementalCompilerBuilder {
    private final FileSystemSnapshotter fileSystemSnapshotter;
    private final CompilationStateCacheFactory compilationStateCacheFactory;
    private final CSourceParser sourceParser;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final TaskFileVarFactory fileVarFactory;

    public DefaultIncrementalCompilerBuilder(FileSystemSnapshotter fileSystemSnapshotter, CompilationStateCacheFactory compilationStateCacheFactory, CSourceParser sourceParser, DirectoryFileTreeFactory directoryFileTreeFactory, TaskFileVarFactory fileVarFactory) {
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        this.compilationStateCacheFactory = compilationStateCacheFactory;
        this.sourceParser = sourceParser;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.fileVarFactory = fileVarFactory;
    }

    @Override
    public IncrementalCompiler newCompiler(TaskInternal task, FileCollection sourceFiles, FileCollection includeDirs) {
        return new StateCollectingIncrementalCompiler(task, includeDirs, sourceFiles, fileSystemSnapshotter, compilationStateCacheFactory, sourceParser, directoryFileTreeFactory, fileVarFactory);
    }

    private static class StateCollectingIncrementalCompiler implements IncrementalCompiler, MinimalFileSet, LifecycleAwareTaskProperty {
        private final FileSystemSnapshotter fileSystemSnapshotter;
        private final CompilationStateCacheFactory compilationStateCacheFactory;
        private final CSourceParser sourceParser;
        private final DirectoryFileTreeFactory directoryFileTreeFactory;
        private final TaskOutputsInternal taskOutputs;
        private final FileCollection includeDirs;
        private final String taskPath;
        private final FileCollection sourceFiles;
        private final FileCollection headerFilesCollection;
        private PersistentStateCache<CompilationState> compileStateCache;
        private IncrementalCompilation incrementalCompilation;
        private NativeToolChainInternal toolChain;

        StateCollectingIncrementalCompiler(TaskInternal task, FileCollection includeDirs, FileCollection sourceFiles, FileSystemSnapshotter fileSystemSnapshotter, CompilationStateCacheFactory compilationStateCacheFactory, CSourceParser sourceParser, DirectoryFileTreeFactory directoryFileTreeFactory, TaskFileVarFactory fileVarFactory) {
            this.taskOutputs = task.getOutputs();
            this.taskPath = task.getPath();
            this.includeDirs = includeDirs;
            this.sourceFiles = sourceFiles;
            this.fileSystemSnapshotter = fileSystemSnapshotter;
            this.compilationStateCacheFactory = compilationStateCacheFactory;
            this.sourceParser = sourceParser;
            this.directoryFileTreeFactory = directoryFileTreeFactory;
            headerFilesCollection = fileVarFactory.newCalculatedInputFileCollection(task, this, sourceFiles, includeDirs);
        }

        @Override
        public <T extends NativeCompileSpec> Compiler<T> createCompiler(Compiler<T> compiler) {
            if (incrementalCompilation == null) {
                throw new IllegalStateException("Header files should be calculated before compiler is created.");
            }
            return new IncrementalNativeCompiler<T>(taskOutputs, compiler, compileStateCache, incrementalCompilation);
        }

        @Override
        public void setToolChain(NativeToolChainInternal toolChain) {
            this.toolChain = toolChain;
        }

        @Override
        public Set<File> getFiles() {
            List<File> includeRoots = ImmutableList.copyOf(includeDirs);
            compileStateCache = compilationStateCacheFactory.create(taskPath);
            DefaultSourceIncludesParser sourceIncludesParser = new DefaultSourceIncludesParser(sourceParser, toolChain instanceof Clang || toolChain instanceof Gcc);
            DefaultSourceIncludesResolver dependencyParser = new DefaultSourceIncludesResolver(includeRoots, fileSystemSnapshotter);
            IncrementalCompileFilesFactory incrementalCompileFilesFactory = new IncrementalCompileFilesFactory(sourceIncludesParser, dependencyParser, fileSystemSnapshotter);
            IncrementalCompileProcessor incrementalCompileProcessor = new IncrementalCompileProcessor(compileStateCache, incrementalCompileFilesFactory);

            incrementalCompilation = incrementalCompileProcessor.processSourceFiles(sourceFiles.getFiles());
            DefaultHeaderDependenciesCollector headerDependenciesCollector = new DefaultHeaderDependenciesCollector(directoryFileTreeFactory);
            return headerDependenciesCollector.collectExistingHeaderDependencies(taskPath, includeRoots, incrementalCompilation);
        }

        @Override
        public void prepareValue() {
        }

        @Override
        public void cleanupValue() {
            compileStateCache = null;
            incrementalCompilation = null;
            toolChain = null;
        }

        @Override
        public String getDisplayName() {
            return "header files for " + taskPath;
        }

        @Override
        public FileCollection getHeaderFiles() {
            return headerFilesCollection;
        }
    }
}
