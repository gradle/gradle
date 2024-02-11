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
import org.gradle.util.internal.GUtil;

import java.util.HashSet;
import java.util.Set;

public class ProgressOperation {

    private String status;
    private final String category;
    private final OperationIdentifier operationId;
    private final ProgressOperation parent;
    private Set<ProgressOperation> children;

    public ProgressOperation(String status, String category, OperationIdentifier operationId, ProgressOperation parent) {
        this.status = status;
        this.category = category;
        this.operationId = operationId;
        this.parent = parent;
    }

    @Override
    public String toString() {
        return String.format("id=%s, category=%s, status=%s", operationId, category, status);
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        if (GUtil.isTrue(status)) {
            return status;
        }
        return null;
    }

    public String getCategory() {
        return category;
    }

    public OperationIdentifier getOperationId() {
        return operationId;
    }

    public ProgressOperation getParent() {
        return parent;
    }

    public boolean addChild(ProgressOperation operation) {
        if (children == null) {
            children = new HashSet<ProgressOperation>();
        }
        return children.add(operation);
    }

    public boolean removeChild(ProgressOperation operation) {
        if (children == null) {
            throw new IllegalStateException(String.format("Cannot remove child operation [%s] from operation with no children [%s]", operation, this));
        }
        return children.remove(operation);
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }
}
