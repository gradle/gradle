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

package org.gradle.internal.logging.console;

import org.gradle.internal.operations.OperationIdentifier;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class ProgressOperations {
    private final Map<OperationIdentifier, ProgressOperation> operationsById = new HashMap<OperationIdentifier, ProgressOperation>();

    public ProgressOperation start(String status, String category, OperationIdentifier operationId, @Nullable OperationIdentifier parentOperationId) {
        ProgressOperation parent = null;
        if (parentOperationId != null) {
            parent = operationsById.get(parentOperationId);
        }
        ProgressOperation operation = new ProgressOperation(status, category, operationId, parent);
        if (parent != null) {
            parent.addChild(operation);
        }
        ProgressOperation previous = operationsById.put(operationId, operation);
        if (previous != null) {
            throw new IllegalStateException("Received start event for an operation that has already started (id: " + operationId + "). Currently in progress=" + operationsById.values());
        }
        return operation;
    }

    public ProgressOperation progress(String description, OperationIdentifier operationId) {
        ProgressOperation op = operationsById.get(operationId);
        if (op == null) {
            throw new IllegalStateException("Received progress event for an unknown operation (id: " + operationId + "). Currently in progress=" + operationsById.values());
        }
        op.setStatus(description);
        return op;
    }

    public ProgressOperation complete(OperationIdentifier operationId) {
        ProgressOperation op = operationsById.remove(operationId);
        if (op == null) {
            throw new IllegalStateException("Received complete event for an unknown operation (id: " + operationId + "). Currently in progress=" + operationsById.values());
        }
        if (op.getParent() != null) {
            op.getParent().removeChild(op);
        }
        return op;
    }
}
