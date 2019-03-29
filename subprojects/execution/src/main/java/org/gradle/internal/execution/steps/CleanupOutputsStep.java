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

import com.google.common.collect.Iterables;
import org.gradle.internal.execution.IncrementalContext;
import org.gradle.internal.execution.InputChangesContext;
import org.gradle.internal.execution.Result;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.impl.OutputsCleaner;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.Set;

public class CleanupOutputsStep<C extends InputChangesContext, R extends Result> implements Step<C, R> {

    private final Step<? super C, ? extends R> delegate;

    public CleanupOutputsStep(Step<? super C, ? extends R> delegate) {
        this.delegate = delegate;
    }

    @Override
    public R execute(C context) {
        boolean incremental = context.getInputChanges()
            .map(inputChanges -> inputChanges.isIncremental())
            .orElse(false);
        if (!incremental) {
            UnitOfWork work = context.getWork();
            if (work.shouldCleanupOutputsOnNonIncrementalExecution()) {
                if (work.hasOverlappingOutputs()) {
                    cleanupOverlappingOutputs(context, work);
                } else {
                    cleanupExclusiveOutputs(work);
                }
            }
        }
        return delegate.execute(context);
    }

    private void cleanupOverlappingOutputs(IncrementalContext context, UnitOfWork work) {
        context.getAfterPreviousExecutionState().ifPresent(previousOutputs -> {
            Set<File> outputDirectoriesToPreserve = new HashSet<>();
            work.visitOutputProperties((name, type, roots) -> {
                switch (type) {
                    case FILE:
                        for (File root : roots) {
                            File parentFile = root.getParentFile();
                            if (parentFile != null) {
                                outputDirectoriesToPreserve.add(parentFile);
                            }
                        }
                        break;
                    case DIRECTORY:
                        Iterables.addAll(outputDirectoriesToPreserve, roots);
                        break;
                    default:
                        throw new AssertionError();
                }
            });
            OutputsCleaner cleaner = new OutputsCleaner(file -> true, dir -> !outputDirectoriesToPreserve.contains(dir));
            for (FileCollectionFingerprint fileCollectionFingerprint : previousOutputs.getOutputFileProperties().values()) {
                try {
                    cleaner.cleanupOutputs(fileCollectionFingerprint);
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to clean up output files for " + work.getDisplayName(), e);
                }
            }
        });
    }

    private void cleanupExclusiveOutputs(UnitOfWork work) {
        work.visitOutputProperties((name, type, roots) -> {
            for (File root : roots) {
                if (root.exists()) {
                    switch (type) {
                        case FILE:
                            GFileUtils.forceDelete(root);
                            break;
                        case DIRECTORY:
                            GFileUtils.cleanDirectory(root);
                            break;
                        default:
                            throw new AssertionError();
                    }
                }
            }
        });
    }
}
