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
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.file.collections.ImmutableFileCollection;
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
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
public class ResolveTaskArtifactStateTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResolveTaskArtifactStateTaskExecuter.class);

    private final PropertyWalker propertyWalker;
    private final PathToFileResolver resolver;
    private final TaskExecuter executer;
    private final TaskArtifactStateRepository repository;

    public ResolveTaskArtifactStateTaskExecuter(TaskArtifactStateRepository repository, PathToFileResolver resolver, PropertyWalker propertyWalker, TaskExecuter executer) {
        this.propertyWalker = propertyWalker;
        this.resolver = resolver;
        this.executer = executer;
        this.repository = repository;
    }

    @Override
    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, final TaskExecutionContext context) {
        Timer clock = Time.startTimer();
        TaskProperties taskProperties = DefaultTaskProperties.resolve(propertyWalker, resolver, task);
        context.setTaskProperties(taskProperties);
        TaskArtifactState taskArtifactState = repository.getStateFor(task, taskProperties);
        TaskOutputsInternal outputs = task.getOutputs();

        context.setTaskArtifactState(taskArtifactState);
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
            context.setTaskArtifactState(null);
            context.setTaskProperties(null);
            LOGGER.debug("Removed task artifact state for {} from context.");
        }
    }

}
