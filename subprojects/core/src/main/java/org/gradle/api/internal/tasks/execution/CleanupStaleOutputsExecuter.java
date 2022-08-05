/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.execution;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.properties.FilePropertySpec;
import org.gradle.api.internal.tasks.properties.TaskProperties;
import org.gradle.internal.execution.BuildOutputCleanupRegistry;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.history.OutputFilesRepository;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class CleanupStaleOutputsExecuter implements TaskExecuter {

    public static final String CLEAN_STALE_OUTPUTS_DISPLAY_NAME = "Clean stale outputs";

    private final Logger logger = LoggerFactory.getLogger(CleanupStaleOutputsExecuter.class);
    private final BuildOperationExecutor buildOperationExecutor;
    private final Deleter deleter;
    private final OutputChangeListener outputChangeListener;
    private final TaskExecuter executer;
    private final OutputFilesRepository outputFilesRepository;
    private final BuildOutputCleanupRegistry cleanupRegistry;

    public CleanupStaleOutputsExecuter(
        BuildOperationExecutor buildOperationExecutor,
        BuildOutputCleanupRegistry cleanupRegistry,
        Deleter deleter,
        OutputChangeListener outputChangeListener,
        OutputFilesRepository outputFilesRepository,
        TaskExecuter executer
    ) {
        this.cleanupRegistry = cleanupRegistry;
        this.buildOperationExecutor = buildOperationExecutor;
        this.deleter = deleter;
        this.outputChangeListener = outputChangeListener;
        this.executer = executer;
        this.outputFilesRepository = outputFilesRepository;
    }

    @Override
    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        if (!task.getReasonNotToTrackState().isPresent()) {
            cleanupStaleOutputs(context);
        }
        return executer.execute(task, state, context);
    }

    private void cleanupStaleOutputs(TaskExecutionContext context) {
        Set<File> filesToDelete = new HashSet<>();
        TaskProperties properties = context.getTaskProperties();
        for (FilePropertySpec outputFileSpec : properties.getOutputFileProperties()) {
            FileCollection files = outputFileSpec.getPropertyFiles();
            for (File file : files) {
                if (cleanupRegistry.isOutputOwnedByBuild(file) && !outputFilesRepository.isGeneratedByGradle(file) && file.exists()) {
                    filesToDelete.add(file);
                }
            }
        }
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
                        if (file.exists()) {
                            logger.info("Deleting stale output file: {}", file.getAbsolutePath());
                            deleter.deleteRecursively(file);
                        }
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
