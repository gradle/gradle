/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.cache.internal.cacheops;

import java.util.ArrayList;
import java.util.List;

class CacheOperationStack {
    private final List<CacheOperation> operations = new ArrayList<CacheOperation>();

    public CacheOperationStack pushLongRunningOperation() {
        operations.add(0, new CacheOperation(true));
        return this;
    }

    public void popLongRunningOperation() {
        pop(true);
    }

    public boolean isInCacheAction() {
        return !operations.isEmpty() && !operations.get(0).longRunningOperation;
    }

    public boolean isInLongRunningOperation() {
        return !operations.isEmpty() && !isInCacheAction();
    }

    public CacheOperationStack pushCacheAction() {
        operations.add(0, new CacheOperation(false));
        return this;
    }

    public CacheOperation popCacheAction() {
        return pop(false);
    }

    private CacheOperation pop(boolean longRunningOperation) {
        checkNotEmpty();
        CacheOperation operation = operations.remove(0);
        if (operation.longRunningOperation == longRunningOperation) {
            return operation;
        }
        throw new IllegalStateException(String.format("Unexpected operation %s at the top of the stack.", operation));
    }

    private void checkNotEmpty() {
        if (operations.isEmpty()) {
            throw new IllegalStateException("Operation stack is empty.");
        }
    }

    public boolean isEmpty() {
        return operations.isEmpty();
    }
}
