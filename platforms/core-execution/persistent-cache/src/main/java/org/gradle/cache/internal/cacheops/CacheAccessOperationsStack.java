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

public class CacheAccessOperationsStack {
    @SuppressWarnings("ThreadLocalUsage")
    private final ThreadLocal<CacheOperationStack> stackForThread = new ThreadLocal<>();

    public void pushCacheAction() {
        CacheOperationStack stack = getOrCreateStack();
        stack.pushCacheAction();
    }

    public void popCacheAction() {
        CacheOperationStack stack = stackForThread.get();
        if (stack == null) {
            throw new IllegalStateException("Operation stack is empty.");
        }
        stack.popCacheAction();
        if (stack.isEmpty()) {
            stackForThread.remove();
        }
    }

    public boolean isInCacheAction() {
        CacheOperationStack stack = stackForThread.get();
        return stack != null && stack.isInCacheAction();
    }

    private CacheOperationStack getOrCreateStack() {
        CacheOperationStack stack = stackForThread.get();
        if (stack == null) {
            stack = new CacheOperationStack();
            stackForThread.set(stack);
        }
        return stack;
    }
}
