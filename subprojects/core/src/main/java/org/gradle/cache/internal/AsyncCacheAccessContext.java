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

package org.gradle.cache.internal;

import java.util.HashMap;
import java.util.Map;

/**
 * Thread-local context that gets passed over to the worker thread of CacheAccessWorker
 */
public class AsyncCacheAccessContext {
    private static final ThreadLocal<AsyncCacheAccessContext> CONTEXT_THREAD_LOCAL = new ThreadLocal<AsyncCacheAccessContext>();
    private final Map<String, Object> contextMap;

    public static AsyncCacheAccessContext current() {
        return CONTEXT_THREAD_LOCAL.get();
    }

    public static boolean createWhenMissing() {
        if (current() != null) {
            return false;
        }
        AsyncCacheAccessContext context = new AsyncCacheAccessContext();
        CONTEXT_THREAD_LOCAL.set(context);
        return true;
    }

    public static void apply(AsyncCacheAccessContext context) {
        CONTEXT_THREAD_LOCAL.set(context);
    }

    public static void remove() {
        CONTEXT_THREAD_LOCAL.remove();
    }

    public static String createKey(Class<?> ownerClass, String name) {
        return ownerClass.getName() + "." + name;
    }

    private AsyncCacheAccessContext() {
        this(new HashMap<String, Object>());
    }

    private AsyncCacheAccessContext(HashMap<String, Object> contextMap) {
        this.contextMap = contextMap;
    }

    public void put(String key, Object value) {
        contextMap.put(key, value);
    }

    public <T> T get(String key, Class<T> type) {
        return type.cast(contextMap.get(key));
    }

    public void remove(String key) {
        contextMap.remove(key);
    }

    public static AsyncCacheAccessContext copyOfCurrent() {
        AsyncCacheAccessContext current = current();
        if (current == null) {
            return null;
        }
        return current.copy();
    }

    private AsyncCacheAccessContext copy() {
        return new AsyncCacheAccessContext(new HashMap<String, Object>(contextMap));
    }
}
