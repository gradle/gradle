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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.properties.LifecycleAwareValue;
import org.gradle.api.internal.tasks.properties.TaskProperties;

/**
 * Notifies the task properties of the start and completion of task execution, so they may finalize and cache whatever state is required to efficiently fingerprint inputs and outputs, apply validation or whatever.
 *
 * Currently, this is applied prior to validation, so that all properties are finalized before their value is validated, however we should finalize and validate any property whose value is used to finalize the value of another property.
 */
public class FinalizePropertiesTaskExecuter implements TaskExecuter {
    private final TaskExecuter taskExecuter;

    public FinalizePropertiesTaskExecuter(TaskExecuter taskExecuter) {
        this.taskExecuter = taskExecuter;
    }

    @Override
    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        TaskProperties properties = context.getTaskProperties();
        for (LifecycleAwareValue value : properties.getLifecycleAwareValues()) {
            value.prepareValue();
        }
        try {
            return taskExecuter.execute(task, state, context);
        } finally {
            for (LifecycleAwareValue value : properties.getLifecycleAwareValues()) {
                value.cleanupValue();
            }
        }
    }
}
