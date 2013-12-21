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

package org.gradle.internal.progress;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicLong;

public class OperationsHierarchy {
    final AtomicLong sharedCounter;
    final LinkedList<Long> hierarchy;
    private OperationIdentifier id;

    public OperationsHierarchy(AtomicLong sharedCounter, LinkedList<Long> hierarchy) {
        assert sharedCounter != null;
        assert hierarchy != null;

        this.sharedCounter = sharedCounter;
        this.hierarchy = hierarchy;
    }

    public OperationIdentifier start() {
        if (id == null) {
            Long parent = hierarchy.isEmpty()? null: hierarchy.getLast();
            long operationId = sharedCounter.incrementAndGet();
            hierarchy.addLast(operationId);
            id = new OperationIdentifier(operationId, parent);
        }

        return id;
    }

    public long currentOperationId() {
        assertOperationStarted();
        return id.getId();
    }

    public long completeCurrentOperation() {
        assertOperationStarted();
        assertHierarchyNotEmpty();
        Long last = hierarchy.getLast();
        if (id.getId() == last) {
            //typical scenario
            hierarchy.removeLast();
        } else {
            //this means that we're removing an operation id that has incomplete children
            //this is not strictly correct, we might fail fast here
            //however, this needs some discussion and making sure child operations are always completed before the parent
            //(even in internal error conditions)
            hierarchy.remove(id.getId());
        }
        long out = id.getId();
        id = null;
        return out;
    }

    private void assertOperationStarted() {
        if (id == null) {
            throw new NoActiveOperationFound("Cannot provide current operation id because the operation was not started or it was already completed.");
        }
    }

    private void assertHierarchyNotEmpty() {
        if (hierarchy.isEmpty()) {
            throw new HierarchyEmptyException("Unable to provide operation id because there are no active operations in the hierarchy. Was the hierarchy list tinkered with?");
        }
    }

    static class HierarchyEmptyException extends IllegalStateException {
        public HierarchyEmptyException(String message) {
            super(message);
        }
    }
    static class NoActiveOperationFound extends IllegalStateException {
        public NoActiveOperationFound(String message) {
            super(message);
        }
    }
}
