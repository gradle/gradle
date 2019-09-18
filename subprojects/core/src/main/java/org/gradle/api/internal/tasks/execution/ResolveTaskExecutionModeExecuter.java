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

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskExecutionMode;
import org.gradle.api.internal.changedetection.TaskExecutionModeResolver;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.properties.DefaultTaskProperties;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.internal.tasks.properties.TaskProperties;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullApi
public class ResolveTaskExecutionModeExecuter implements TaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResolveTaskExecutionModeExecuter.class);

    private final PropertyWalker propertyWalker;
    private final TaskExecuter executer;
    private final TaskExecutionModeResolver executionModeResolver;
    private final FileCollectionFactory fileCollectionFactory;

    public ResolveTaskExecutionModeExecuter(TaskExecutionModeResolver executionModeResolver, FileCollectionFactory fileCollectionFactory, PropertyWalker propertyWalker, TaskExecuter executer) {
        this.fileCollectionFactory = fileCollectionFactory;
        this.propertyWalker = propertyWalker;
        this.executer = executer;
        this.executionModeResolver = executionModeResolver;
    }

    @Override
    public TaskExecuterResult execute(final TaskInternal task, TaskStateInternal state, final TaskExecutionContext context) {
        Timer clock = Time.startTimer();
        TaskProperties properties = DefaultTaskProperties.resolve(propertyWalker, fileCollectionFactory, task);
        context.setTaskProperties(properties);
        TaskExecutionMode taskExecutionMode = executionModeResolver.getExecutionMode(task, properties);

        context.setTaskExecutionMode(taskExecutionMode);
        LOGGER.debug("Putting task artifact state for {} into context took {}.", task, clock.getElapsed());
        try {
            return executer.execute(task, state, context);
        } finally {
            context.setTaskExecutionMode(null);
            context.setTaskProperties(null);
            LOGGER.debug("Removed task artifact state for {} from context.", task);
        }
    }

}
