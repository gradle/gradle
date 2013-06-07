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

    private final ThreadLocal<CacheOperationStack> perThradStack = new ThreadLocal<CacheOperationStack>() {
        @Override
        protected CacheOperationStack initialValue() {
            return new CacheOperationStack();
        }
    };

    private final CacheOperationStack globalStack = new CacheOperationStack();

    public void close() {
        perThradStack.remove();
    }

    public void pushCacheAction(String operationDisplayName) {
        globalStack.pushCacheAction(operationDisplayName);
        perThradStack.get().pushCacheAction(operationDisplayName);
    }

    public void popCacheAction(String operationDisplayName) {
        globalStack.popCacheAction(operationDisplayName);
        perThradStack.get().popCacheAction(operationDisplayName);
    }

    public boolean isInCacheAction() {
        return perThradStack.get().isInCacheAction();
    }

    public void pushLongRunningOperation(String operationDisplayName) {
        globalStack.pushLongRunningOperation(operationDisplayName);
        perThradStack.get().pushLongRunningOperation(operationDisplayName);
    }

    public void popLongRunningOperation(String operationDisplayName) {
        globalStack.popLongRunningOperation(operationDisplayName);
        perThradStack.get().popLongRunningOperation(operationDisplayName);
    }

    public String getDescription() {
        return perThradStack.get().getDescription();
    }

    public boolean maybeReentrantLongRunningOperation(String operationDisplayName) {
        if (globalStack.isInLongRunningOperation()) {
            pushLongRunningOperation(operationDisplayName);
            return true;
        }
        return false;
    }
}