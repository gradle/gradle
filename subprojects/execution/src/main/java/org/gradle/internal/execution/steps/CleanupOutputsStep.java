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

import com.google.common.collect.ImmutableSet;
import org.gradle.internal.execution.IncrementalContext;
import org.gradle.internal.execution.InputChangesContext;
import org.gradle.internal.execution.Result;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.impl.OutputsCleaner;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.util.GFileUtils;

import java.io.File;

public class CleanupOutputsStep implements Step<InputChangesContext, Result> {

    private final Step<? super InputChangesContext, ? extends Result> delegate;

    public CleanupOutputsStep(Step<? super InputChangesContext, ? extends Result> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Result execute(InputChangesContext context) {
        if (!context.isIncrementalExecution()) {
            UnitOfWork work = context.getWork();
            if (work.isCleanupOutputsOnNonIncrementalExecution()) {
                if (work.isOverlappingOutputsDetected()) {
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
            ImmutableSet.Builder<File> builder = ImmutableSet.builder();
            work.visitOutputProperties((name, type, roots) -> {
                if (type == TreeType.DIRECTORY) {
                    builder.addAll(roots);
                }
                if (type == TreeType.FILE) {
                    for (File root : roots) {
                        File parentFile = root.getParentFile();
                        if (parentFile != null) {
                            builder.add(parentFile);
                        }
                    }
                }
            });
            ImmutableSet<File> preparedOutputDirectories = builder.build();
            OutputsCleaner cleaner = new OutputsCleaner(file -> true, file -> !preparedOutputDirectories.contains(file));
            for (FileCollectionFingerprint fileCollectionFingerprint : previousOutputs.getOutputFileProperties().values()) {
                cleaner.cleanupOutputs(fileCollectionFingerprint);
            }
        });
    }

    private void cleanupExclusiveOutputs(UnitOfWork work) {
        work.visitOutputProperties((name, type, roots) -> {
            for (File root : roots) {
                if (root.exists()) {
                    if (type == TreeType.DIRECTORY) {
                        GFileUtils.cleanDirectory(root);
                    } else {
                        GFileUtils.deleteFileQuietly(root);
                    }
                }
            }
        });
    }
}
