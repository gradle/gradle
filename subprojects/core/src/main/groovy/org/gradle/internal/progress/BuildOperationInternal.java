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
package org.gradle.internal.progress;

import org.gradle.api.Nullable;

public final class BuildOperationInternal {
    private final Object id;
    private final Object parentId;
    private final BuildOperationType operationType;
    private final Object payload;
    private final long startTime;
    private final long endTime;

    public BuildOperationInternal(Object id, Object parentId, BuildOperationType operationType, Object payload, long startTime) {
        this(id, parentId, operationType, payload, startTime, 0);
    }

    public BuildOperationInternal(Object id, Object parentId, BuildOperationType operationType, Object payload, long startTime, long endTime) {
        this.id = id;
        this.parentId = parentId;
        this.operationType = operationType;
        this.payload = payload;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Object getId() {
        return id;
    }

    @Nullable
    public Object getParentId() {
        return parentId;
    }

    public BuildOperationType getOperationType() {
        return operationType;
    }

    public Object getPayload() {
        return payload;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }
}
