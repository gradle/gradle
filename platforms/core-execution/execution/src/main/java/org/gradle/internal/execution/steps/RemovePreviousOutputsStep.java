/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.OutputsCleaner;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.SnapshotUtil;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.Set;

/**
 * When executed non-incrementally remove previous outputs owned by the work unit.
 */
public class RemovePreviousOutputsStep<C extends ChangingOutputsContext, R extends Result> implements Step<C, R> {

    private final Deleter deleter;
    private final OutputChangeListener outputChangeListener;
    private final Step<? super C, ? extends R> delegate;

    public RemovePreviousOutputsStep(
        Deleter deleter,
        OutputChangeListener outputChangeListener,
        Step<? super C, ? extends R> delegate
    ) {
        this.deleter = deleter;
        this.outputChangeListener = outputChangeListener;
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        if (!context.isIncrementalExecution()) {
            if (work.shouldCleanupOutputsOnNonIncrementalExecution()) {
                boolean hasOverlappingOutputs = context.getBeforeExecutionState()
                    .flatMap(BeforeExecutionState::getDetectedOverlappingOutputs)
                    .isPresent();
                if (hasOverlappingOutputs) {
                    cleanupOverlappingOutputs(context, work);
                } else {
                    cleanupExclusivelyOwnedOutputs(context, work);
                }
            }
        }
        return delegate.execute(work, context);
    }

    private void cleanupOverlappingOutputs(BeforeExecutionContext context, UnitOfWork work) {
        context.getPreviousExecutionState().ifPresent(previousOutputs -> {
            Set<File> outputDirectoriesToPreserve = new HashSet<>();
            work.visitOutputs(context.getMutableWorkspaceLocation(), new UnitOfWork.OutputVisitor() {
                @Override
                public void visitOutputProperty(String propertyName, TreeType type, UnitOfWork.OutputFileValueSupplier value) {
                    File root = value.getValue();
                    switch (type) {
                        case FILE:
                            File parentFile = root.getParentFile();
                            if (parentFile != null) {
                                outputDirectoriesToPreserve.add(parentFile);
                            }
                            break;
                        case DIRECTORY:
                            outputDirectoriesToPreserve.add(root);
                            break;
                        default:
                            throw new AssertionError();
                    }
                }
            });
            OutputsCleaner cleaner = new OutputsCleaner(
                deleter,
                file -> true,
                dir -> !outputDirectoriesToPreserve.contains(dir)
            );
            for (FileSystemSnapshot snapshot : previousOutputs.getOutputFilesProducedByWork().values()) {
                try {
                    // Previous outputs can be in a different place than the current outputs
                    outputChangeListener.invalidateCachesFor(SnapshotUtil.rootIndex(snapshot).keySet());
                    cleaner.cleanupOutputs(snapshot);
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to clean up output files for " + work.getDisplayName(), e);
                }
            }
        });
    }

    private void cleanupExclusivelyOwnedOutputs(BeforeExecutionContext context, UnitOfWork work) {
        work.visitOutputs(context.getMutableWorkspaceLocation(), new UnitOfWork.OutputVisitor() {
            @Override
            public void visitOutputProperty(String propertyName, TreeType type, UnitOfWork.OutputFileValueSupplier value) {
                File root = value.getValue();
                if (root.exists()) {
                    try {
                        switch (type) {
                            case FILE:
                                deleter.delete(root);
                                break;
                            case DIRECTORY:
                                deleter.ensureEmptyDirectory(root);
                                break;
                            default:
                                throw new AssertionError();
                        }
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                }
            }
        });
    }
}
