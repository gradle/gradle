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
import org.gradle.internal.progress.IdentifiableOperation;

public final class TaskOperationInternal implements IdentifiableOperation {
    private final Object id;
    private final TaskInternal task;
    private final Object parentId;

    public TaskOperationInternal(Object id, TaskInternal task, Object parentId) {
        this.id = id;
        this.task = task;
        this.parentId = parentId;
    }

    @Override
    public Object getId() {
        return id;
    }

    public TaskInternal getTask() {
        return task;
    }

    @Override
    public Object getParentId() {
        return parentId;
    }
}
