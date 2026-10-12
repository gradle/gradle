/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.workers.internal;

import org.gradle.internal.operations.BuildOperationIdRef;
import org.gradle.internal.operations.OperationIdentifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Holds the identifier of the build operation of the work request being processed by a worker process.
 * <p>
 * Worker processes have no root build operation; the operation of the current work request is the
 * closest live ancestor to attribute events to when the producing thread has no current operation.
 * An instance is created per work request, on the request thread, capturing that thread's current
 * operation.
 */
@NullMarked
public class WorkRequestBuildOperationRef implements BuildOperationIdRef {

    private final @Nullable OperationIdentifier requestBuildOperationId;

    public WorkRequestBuildOperationRef(@Nullable OperationIdentifier requestBuildOperationId) {
        this.requestBuildOperationId = requestBuildOperationId;
    }

    /**
     * Returns the identifier of the work request's build operation.
     *
     * @return the identifier captured when the work request started, or {@code null} if the request thread had no current operation
     */
    @Nullable
    @Override
    public OperationIdentifier getId() {
        return requestBuildOperationId;
    }
}
