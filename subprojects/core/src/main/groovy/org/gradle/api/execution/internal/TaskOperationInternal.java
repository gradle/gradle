/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.execution.internal;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.internal.progress.IdentifiableOperation;

public class TaskOperationInternal implements IdentifiableOperation {
    private final String id;
    private final TaskInternal task;
    private final TaskStateInternal state;
    private final String parentId;

    public TaskOperationInternal(String id, TaskInternal task, TaskStateInternal state, String parentId) {
        this.id = id;
        this.task = task;
        this.state = state;
        this.parentId = parentId;
    }

    public Object getId() {
        return id;
    }

    @Override
    public Object getParentId() {
        return parentId;
    }

    public TaskInternal getTask() {
        return task;
    }

    public TaskStateInternal getState() {
        return state;
    }
}
