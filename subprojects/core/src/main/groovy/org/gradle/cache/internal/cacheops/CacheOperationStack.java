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

    public String getDescription() {
        checkNotEmpty();
        return operations.get(0).description;
    }

    public CacheOperationStack pushLongRunningOperation(String description) {
        operations.add(0, new CacheOperation(description, true));
        return this;
    }

    public void popLongRunningOperation(String description) {
        pop(description, true);
    }

    public boolean isInCacheAction() {
        return !operations.isEmpty() && !operations.get(0).longRunningOperation;
    }

    public boolean isInLongRunningOperation() {
        return !operations.isEmpty() && !isInCacheAction();
    }

    public CacheOperationStack pushCacheAction(String description) {
        operations.add(0, new CacheOperation(description, false));
        return this;
    }

    public void popCacheAction(String description) {
        pop(description, false);
    }

    private CacheOperation pop(String description, boolean longRunningOperation) {
        checkNotEmpty();
        CacheOperation operation = operations.remove(0);
        if (operation.description.equals(description) && operation.longRunningOperation == longRunningOperation) {
            return operation;
        }
        throw new IllegalStateException("Cannot pop operation '" + description + "'. It is not at the top of the stack");
    }

    private void checkNotEmpty() {
        if (operations.isEmpty()) {
            throw new IllegalStateException();
        }
    }
}
