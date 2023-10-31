/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.execution.steps;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Streams;
import org.gradle.internal.execution.BuildOutputCleanupRegistry;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.OutputFilesRepository;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class HandleStaleOutputsStep<C extends WorkspaceContext, R extends AfterExecutionResult> implements Step<C, R> {
    @VisibleForTesting
    public static final String CLEAN_STALE_OUTPUTS_DISPLAY_NAME = "Clean stale outputs";

    private static final Logger LOGGER = LoggerFactory.getLogger(HandleStaleOutputsStep.class);

    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildOutputCleanupRegistry cleanupRegistry;
    private final Deleter deleter;
    private final OutputChangeListener outputChangeListener;
    private final OutputFilesRepository outputFilesRepository;
    private final Step<? super C, ? extends R> delegate;

    public HandleStaleOutputsStep(
        BuildOperationExecutor buildOperationExecutor,
        BuildOutputCleanupRegistry cleanupRegistry,
        Deleter deleter,
        OutputChangeListener outputChangeListener,
        OutputFilesRepository outputFilesRepository,
        Step<? super C, ? extends R> delegate
    ) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.cleanupRegistry = cleanupRegistry;
        this.deleter = deleter;
        this.outputChangeListener = outputChangeListener;
        this.outputFilesRepository = outputFilesRepository;
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        if (work.shouldCleanupStaleOutputs()) {
            cleanupStaleOutputs(work, context);
        }
        R result = delegate.execute(work, context);
        result.getAfterExecutionState()
            .ifPresent(afterExecutionState -> outputFilesRepository.recordOutputs(afterExecutionState.getOutputFilesProducedByWork().values()));
        return result;
    }

    private void cleanupStaleOutputs(UnitOfWork work, C context) {
        Set<File> filesToDelete = new HashSet<>();
        work.visitOutputs(context.getWorkspace(), new UnitOfWork.OutputVisitor() {
            @Override
            public void visitOutputProperty(String propertyName, TreeType type, UnitOfWork.OutputFileValueSupplier value) {
                Streams.stream(value.getFiles())
                    .filter(cleanupRegistry::isOutputOwnedByBuild)
                    .filter(file -> !outputFilesRepository.isGeneratedByGradle(file))
                    .filter(file -> file.exists() || Files.isSymbolicLink(file.toPath()))
                    .forEach(filesToDelete::add);
            }
        });
        if (!filesToDelete.isEmpty()) {
            outputChangeListener.invalidateCachesFor(
                filesToDelete.stream()
                    .map(File::getAbsolutePath)
                    .collect(Collectors.toList())
            );
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) throws IOException {
                    for (File file : filesToDelete) {
                        LOGGER.info("Deleting stale output file: {}", file.getAbsolutePath());
                        deleter.deleteRecursively(file);
                    }
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor
                        .displayName(CLEAN_STALE_OUTPUTS_DISPLAY_NAME)
                        .progressDisplayName("Cleaning stale outputs");
                }
            });
        }
    }
}
