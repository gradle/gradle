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

    public void pushCacheAction() {
        getStackForCurrentThread().pushCacheAction();
    }

    public void popCacheAction() {
        CacheOperationStack stack = getStackForCurrentThread();
        stack.popCacheAction();
        if (stack.isEmpty()) {
            perThreadStacks.remove(currentThread());
        }
    }

    public boolean isInCacheAction() {
        CacheOperationStack stack = perThreadStacks.get(currentThread());
        return stack != null && stack.isInCacheAction();
    }

    public void pushLongRunningOperation() {
        getStackForCurrentThread().pushLongRunningOperation();
    }

    public void popLongRunningOperation() {
        CacheOperationStack stack = getStackForCurrentThread();
        stack.popLongRunningOperation();
        if (stack.isEmpty()) {
            perThreadStacks.remove(currentThread());
        }
    }

    private CacheOperationStack getStackForCurrentThread() {
        CacheOperationStack stack = perThreadStacks.get(currentThread());
        if (stack == null) {
            stack = new CacheOperationStack();
            perThreadStacks.put(currentThread(), stack);
        }
        return stack;
    }
}
