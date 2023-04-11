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

import org.gradle.cache.ObjectHolder;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.operations.BuildOperationDescriptor;

import java.io.File;
import java.util.Collection;

public class IncrementalCompileProcessor {
    private final ObjectHolder<CompilationState> previousCompileStateCache;
    private final IncrementalCompileFilesFactory incrementalCompileFilesFactory;
    private final BuildOperationExecutor buildOperationExecutor;

    public IncrementalCompileProcessor(ObjectHolder<CompilationState> previousCompileStateCache, IncrementalCompileFilesFactory incrementalCompileFilesFactory, BuildOperationExecutor buildOperationExecutor) {
        this.previousCompileStateCache = previousCompileStateCache;
        this.incrementalCompileFilesFactory = incrementalCompileFilesFactory;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    public IncrementalCompilation processSourceFiles(final Collection<File> sourceFiles) {
        return buildOperationExecutor.call(new CallableBuildOperation<IncrementalCompilation>() {
            @Override
            public IncrementalCompilation call(BuildOperationContext context) {
                CompilationState previousCompileState = previousCompileStateCache.get();
                IncrementalCompileSourceProcessor processor = incrementalCompileFilesFactory.files(previousCompileState);
                for (File sourceFile : sourceFiles) {
                    processor.processSource(sourceFile);
                }
                return processor.getResult();
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                ProcessSourceFilesDetails operationDetails = new ProcessSourceFilesDetails(sourceFiles.size());
                return BuildOperationDescriptor
                    .displayName("Processing source files")
                    .details(operationDetails);
            }

            class ProcessSourceFilesDetails {
                private final int sourceFileCount;

                ProcessSourceFilesDetails(int sourceFileCount) {
                    this.sourceFileCount = sourceFileCount;
                }

                public int getSourceFileCount() {
                    return sourceFileCount;
                }
            }
        });
    }

}
