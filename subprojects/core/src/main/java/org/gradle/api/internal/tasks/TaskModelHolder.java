/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.model.TaskModel;
import org.gradle.api.internal.tasks.schema.TaskInstanceSchema;

public class TaskModelHolder<M extends TaskModel> {
    private final TaskInternal task;
    private final TaskModel.Extractor<M> extractor;
    private M model;

    public TaskModelHolder(TaskInternal task, TaskModel.Extractor<M> extractor) {
        this.task = task;
        this.extractor = extractor;
    }

    public M getModel() {
        // TODO Do we need thread safety here?
        // TODO Do we need to refresh the task schema every time we get the model here?
        TaskInstanceSchema schema = task.getInstanceSchema();
        if (model == null || model.getSchema() != schema) {
            model = extractor.extract(schema);
        }
        return model;
    }
}
