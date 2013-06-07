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

/**
 * By Szczepan Faber on 6/7/13
 */
public class CacheAccessOperationsStack {

    private final ThreadLocal<CacheOperationStack> operationStack = new ThreadLocal<CacheOperationStack>() {
        @Override
        protected CacheOperationStack initialValue() {
            return new CacheOperationStack();
        }
    };

    private int longRunningOperations;

    public void close() {
        operationStack.remove();
    }

    public void pushCacheAction(String operationDisplayName) {
        operationStack.get().pushCacheAction(operationDisplayName);
    }

    public void popCacheAction(String operationDisplayName) {
        operationStack.get().popCacheAction(operationDisplayName);
    }

    public boolean isInCacheAction() {
        return operationStack.get().isInCacheAction();
    }

    public void pushLongRunningOperation(String operationDisplayName) {
        longRunningOperations++;
        operationStack.get().pushLongRunningOperation(operationDisplayName);
    }

    public void popLongRunningOperation(String operationDisplayName) {
        longRunningOperations--;
        operationStack.get().popLongRunningOperation(operationDisplayName);
    }

    public String getDescription() {
        return operationStack.get().getDescription();
    }

    public boolean maybeReentrantLongRunningOperation(String operationDisplayName) {
        if (longRunningOperations > 0) {
            pushLongRunningOperation(operationDisplayName);
            return true;
        }
        return false;
    }
}