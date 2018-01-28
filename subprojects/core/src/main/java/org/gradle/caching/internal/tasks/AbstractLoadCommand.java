/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.caching.internal.tasks;

import org.apache.commons.io.FileUtils;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.tasks.execution.TaskOutputChangesListener;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.OutputPropertySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.SortedSet;

public abstract class AbstractLoadCommand<I, O> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLoadCommand.class);

    protected final TaskInternal task;
    private final BuildCacheKey key;
    private final SortedSet<? extends OutputPropertySpec> outputProperties;
    private final FileCollection localStateFiles;
    private final TaskOutputChangesListener taskOutputChangesListener;
    private final TaskArtifactState taskArtifactState;

    public AbstractLoadCommand(BuildCacheKey key, SortedSet<? extends OutputPropertySpec> outputProperties, TaskInternal task, FileCollection localStateFiles, TaskOutputChangesListener taskOutputChangesListener, TaskArtifactState taskArtifactState) {
        this.key = key;
        this.outputProperties = outputProperties;
        this.task = task;
        this.localStateFiles = localStateFiles;
        this.taskOutputChangesListener = taskOutputChangesListener;
        this.taskArtifactState = taskArtifactState;
    }

    public BuildCacheKey getKey() {
        return key;
    }

    public final O performLoad(I input) {
        taskOutputChangesListener.beforeTaskOutputChanged();
        try {
            return performLoad(input, outputProperties, taskArtifactState);
        } catch (Exception e) {
            LOGGER.warn("Cleaning outputs for {} after failed load from cache.", task);
            try {
                cleanupOutputsAfterUnpackFailure();
                taskArtifactState.afterOutputsRemovedBeforeTask();
            } catch (Exception eCleanup) {
                LOGGER.warn("Unrecoverable error during cleaning up after task output unpack failure", eCleanup);
                throw new UnrecoverableTaskOutputUnpackingException(String.format("Failed to unpack outputs for %s, and then failed to clean up; see log above for details", task), e);
            }
            throw new GradleException(String.format("Failed to unpack outputs for %s", task), e);
        } finally {
            cleanLocalState();
        }
    }

    protected abstract O performLoad(I input, SortedSet<? extends OutputPropertySpec> outputProperties, TaskArtifactState taskArtifactState) throws IOException;

    private void cleanLocalState() {
        for (File localStateFile : localStateFiles) {
            try {
                remove(localStateFile);
            } catch (IOException ex) {
                throw new UncheckedIOException(String.format("Failed to clean up local state files for %s: %s", task, localStateFile), ex);
            }
        }
    }

    private void cleanupOutputsAfterUnpackFailure() {
        for (OutputPropertySpec outputProperty : outputProperties) {
            File outputRoot = outputProperty.getOutputRoot();
            try {
                remove(outputRoot);
            } catch (IOException ex) {
                throw new UncheckedIOException(String.format("Failed to clean up files for output property '%s' of %s: %s", outputProperty.getPropertyName(), task, outputRoot), ex);
            }
        }
    }

    private void remove(File file) throws IOException {
        if (file != null && file.exists()) {
            if (file.isDirectory()) {
                FileUtils.cleanDirectory(file);
            } else {
                FileUtils.forceDelete(file);
            }
        }
    }
}
