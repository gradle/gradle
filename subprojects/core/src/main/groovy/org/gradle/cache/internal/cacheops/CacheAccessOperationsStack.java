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

import java.util.HashMap;
import java.util.Map;

import static java.lang.Thread.currentThread;

public class CacheAccessOperationsStack {

    private final Map<Thread, CacheOperationStack> perThreadStacks = new HashMap<Thread, CacheOperationStack>();

    public void close() {
        perThreadStacks.remove(currentThread());
    }

    public void pushCacheAction(String operationDisplayName) {
        if (perThreadStacks.containsKey(currentThread())) {
            getCurrentStack().pushCacheAction(operationDisplayName);
        } else {
            perThreadStacks.put(currentThread(), new CacheOperationStack().pushCacheAction(operationDisplayName));
        }
    }

    public void popCacheAction(String operationDisplayName) {
        getCurrentStack().popCacheAction(operationDisplayName);
    }

    public boolean isInCacheAction() {
        return perThreadStacks.containsKey(currentThread()) && getCurrentStack().isInCacheAction();
    }

    public void pushLongRunningOperation(String operationDisplayName) {
        if (perThreadStacks.containsKey(currentThread())) {
            getCurrentStack().pushLongRunningOperation(operationDisplayName);
        } else {
            perThreadStacks.put(currentThread(), new CacheOperationStack().pushLongRunningOperation(operationDisplayName));
        }
    }

    public void popLongRunningOperation(String operationDisplayName) {
        getCurrentStack().popLongRunningOperation(operationDisplayName);
    }

    public String getDescription() {
        return getCurrentStack().getDescription();
    }

    private CacheOperationStack getCurrentStack() {
        if (!perThreadStacks.containsKey(currentThread())) {
            throw new IllegalStateException("operations stack not ready. Was push action invoked?");
        }
        return perThreadStacks.get(currentThread());
    }

    public boolean maybeReentrantLongRunningOperation(String operationDisplayName) {
        boolean atLeastOneLongRunning = false;
        for (Thread thread : perThreadStacks.keySet()) {
            if(perThreadStacks.get(thread).isInCacheAction()) {
                //if any operation is in cache it means we're in cache operation and it isn't a reentrant long running operation
                return false;
            }
            if (perThreadStacks.get(thread).isInLongRunningOperation()) {
                atLeastOneLongRunning = true;
            }
        }

        if (atLeastOneLongRunning) {
            pushLongRunningOperation(operationDisplayName);
            return true;
        } else {
            return false;
        }
    }
}