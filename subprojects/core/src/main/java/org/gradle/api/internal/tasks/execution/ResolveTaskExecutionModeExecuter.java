/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableCollection;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.changedetection.TaskExecutionMode;
import org.gradle.api.internal.changedetection.TaskExecutionModeResolver;
import org.gradle.api.internal.file.collections.ImmutableFileCollection;
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.properties.DefaultTaskProperties;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.internal.tasks.properties.TaskProperties;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

@NonNullApi
public class ResolveTaskExecutionModeExecuter implements TaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResolveTaskExecutionModeExecuter.class);

    private final PropertyWalker propertyWalker;
    private final PathToFileResolver fileResolver;
    private final TaskExecuter executer;
    private final TaskExecutionModeResolver executionModeResolver;

    public ResolveTaskExecutionModeExecuter(TaskExecutionModeResolver executionModeResolver, PathToFileResolver fileResolver, PropertyWalker propertyWalker, TaskExecuter executer) {
        this.propertyWalker = propertyWalker;
        this.fileResolver = fileResolver;
        this.executer = executer;
        this.executionModeResolver = executionModeResolver;
    }

    @Override
    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, final TaskExecutionContext context) {
        Timer clock = Time.startTimer();
        TaskProperties properties = DefaultTaskProperties.resolve(propertyWalker, fileResolver, task);
        context.setTaskProperties(properties);
        TaskExecutionMode taskExecutionMode = executionModeResolver.getExecutionMode(task, properties);
        TaskOutputsInternal outputs = task.getOutputs();

        context.setTaskExecutionMode(taskExecutionMode);
        outputs.setPreviousOutputFiles(new LazilyInitializedFileCollection() {
            @Override
            public FileCollection createDelegate() {
                AfterPreviousExecutionState previousExecution = context.getAfterPreviousExecution();
                if (previousExecution == null) {
                    return ImmutableFileCollection.of();
                }
                ImmutableCollection<FileCollectionFingerprint> outputFingerprints = previousExecution.getOutputFileProperties().values();
                Set<File> outputs = new HashSet<File>();
                for (FileCollectionFingerprint fileCollectionFingerprint : outputFingerprints) {
                    for (String absolutePath : fileCollectionFingerprint.getFingerprints().keySet()) {
                        outputs.add(new File(absolutePath));
                    }
                }
                return ImmutableFileCollection.of(outputs);
            }

            @Override
            public String getDisplayName() {
                return "previous output files";
            }
        });
        LOGGER.debug("Putting task artifact state for {} into context took {}.", task, clock.getElapsed());
        try {
            return executer.execute(task, state, context);
        } finally {
            outputs.setPreviousOutputFiles(null);
            context.setTaskExecutionMode(null);
            context.setTaskProperties(null);
            LOGGER.debug("Removed task artifact state for {} from context.");
        }
    }

}
