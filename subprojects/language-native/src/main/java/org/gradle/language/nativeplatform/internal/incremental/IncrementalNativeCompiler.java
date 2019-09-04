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
import com.google.common.collect.Lists;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.cache.PersistentStateCache;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.hash.HashCode;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.tasks.StaleOutputCleaner;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.List;

@NonNullApi
public class IncrementalNativeCompiler<T extends NativeCompileSpec> implements Compiler<T> {
    private final Logger logger = LoggerFactory.getLogger(IncrementalNativeCompiler.class);

    private final Compiler<T> delegateCompiler;
    private final TaskOutputsInternal outputs;
    private final Deleter deleter;
    private final PersistentStateCache<CompilationState> compileStateCache;
    private final IncrementalCompilation incrementalCompilation;

    public IncrementalNativeCompiler(
        TaskOutputsInternal outputs,
        Compiler<T> delegateCompiler,
        Deleter deleter,
        PersistentStateCache<CompilationState> compileStateCache,
        IncrementalCompilation incrementalCompilation
    ) {
        this.outputs = outputs;
        this.delegateCompiler = delegateCompiler;
        this.deleter = deleter;
        this.compileStateCache = compileStateCache;
        this.incrementalCompilation = incrementalCompilation;
    }

    @Override
    public WorkResult execute(final T spec) {
        WorkResult workResult;
        if (spec.isIncrementalCompile()) {
            workResult = doIncrementalCompile(incrementalCompilation, spec);
        } else {
            workResult = doCleanIncrementalCompile(spec);
        }

        compileStateCache.set(incrementalCompilation.getFinalState());

        return workResult;
    }

    private List<File> getSourceFilesForPch(T spec) {
        // When the component defines a precompiled header, we need to check if the precompiled header is the _first_ header in the source file.
        // For source files that do not include the precompiled header as the first file, we emit a warning
        // For source files that do include the precompiled header, we mark them as a "source file for pch"
        // The native compiler then adds the appropriate compiler arguments for those source files that can use PCH
        if (spec.getPreCompiledHeader() != null) {
            ImmutableList.Builder<File> sourceFiles = ImmutableList.builder();
            for (File sourceFile : spec.getSourceFiles()) {
                SourceFileState state = incrementalCompilation.getFinalState().getState(sourceFile);
                final HashCode hash = state.getHash();
                List<String> headers = Lists.newArrayList();
                for (IncludeFileEdge edge : state.getEdges()) {
                    if (hash.equals(edge.getIncludedBy())) {
                        headers.add(edge.getIncludePath());
                    }
                }
                String header = spec.getPreCompiledHeader();
                boolean usePCH = !headers.isEmpty() && header.equals(headers.get(0));
                if (usePCH) {
                    sourceFiles.add(sourceFile);
                } else {
                    boolean containsHeader = headers.contains(header);
                    if (containsHeader) {
                        logger.warn(getCantUsePCHMessage(spec.getPreCompiledHeader(), sourceFile));
                    }
                }
            }
            return sourceFiles.build();
        }

        return Collections.emptyList();
    }

    private static String getCantUsePCHMessage(String pchHeader, File sourceFile) {
        return "The source file "
            .concat(sourceFile.getName())
            .concat(" includes the header ")
            .concat(pchHeader)
            .concat(" but it is not the first declared header, so the pre-compiled header will not be used.");
    }

    protected WorkResult doIncrementalCompile(IncrementalCompilation compilation, T spec) {
        // Determine the actual sources to clean/compile
        spec.setSourceFiles(compilation.getRecompile());
        spec.setRemovedSourceFiles(compilation.getRemoved());
        spec.setSourceFilesForPch(getSourceFilesForPch(spec));
        return delegateCompiler.execute(spec);
    }

    protected WorkResult doCleanIncrementalCompile(T spec) {
        boolean deleted = cleanPreviousOutputs(spec);
        spec.setSourceFilesForPch(getSourceFilesForPch(spec));
        WorkResult compileResult = delegateCompiler.execute(spec);
        if (deleted && !compileResult.getDidWork()) {
            return WorkResults.didWork(true);
        }
        return compileResult;
    }

    private boolean cleanPreviousOutputs(NativeCompileSpec spec) {
        return StaleOutputCleaner.cleanOutputs(deleter, outputs.getPreviousOutputFiles(), spec.getObjectFileDir());
    }
}
