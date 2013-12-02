/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.logging.internal.progress;

import java.util.HashMap;
import java.util.Map;

public class ProgressOperations {

    private final Map<Long, ProgressOperation> operationsById = new HashMap<Long, ProgressOperation>();

    public ProgressOperation start(String description, String status, long operationId, Long parentOperationId) {
        ProgressOperation parent = null;
        if (parentOperationId != null) {
            parent = operationsById.get(parentOperationId);
        }
        ProgressOperation operation = new ProgressOperation(description, status, parent);
        operationsById.put(operationId, operation);
        return operation;
    }

    public ProgressOperation progress(String description, long operationId) {
        ProgressOperation op = operationsById.get(operationId);
        if (op == null) {
            throw new IllegalStateException("Received progress event for an unknown operation (id: " + operationId + ")");
        }
        op.setStatus(description);
        return op;
    }

    public ProgressOperation complete(long operationId) {
        ProgressOperation op = operationsById.remove(operationId);
        if (op == null) {
            throw new IllegalStateException("Received complete event for an unknown operation (id: " + operationId + ")");
        }
        return op;
    }
}
