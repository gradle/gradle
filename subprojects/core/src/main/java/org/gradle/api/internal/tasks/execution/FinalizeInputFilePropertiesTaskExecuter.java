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
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskInputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskStateInternal;

/**
 * Notifies the task input file properties of the start and completion of task execution, so they may finalize and cache whatever state is required to snapshot the file values.
 *
 * Currently, this is applied prior to validation and is only applied to file input properties but should be applied to all properties.
 */
public class FinalizeInputFilePropertiesTaskExecuter implements TaskExecuter {
    private final TaskExecuter taskExecuter;

    public FinalizeInputFilePropertiesTaskExecuter(TaskExecuter taskExecuter) {
        this.taskExecuter = taskExecuter;
    }

    @Override
    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        TaskProperties taskProperties = context.getTaskProperties();
        for (TaskInputFilePropertySpec property : taskProperties.getInputFileProperties()) {
            property.prepareValue();
        }
        try {
            taskExecuter.execute(task, state, context);
        } finally {
            for (TaskInputFilePropertySpec property : taskProperties.getInputFileProperties()) {
                property.cleanupValue();
            }
        }
    }
}
