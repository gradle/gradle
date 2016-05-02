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
package org.gradle.cache;

import org.gradle.internal.Factory;

/**
 * Provides synchronised access to a cache.
 */
public interface CacheAccess {
    /**
     * Performs some work against the cache. Acquires exclusive locks the appropriate resources, so that the given action is the only
     * action to execute across all processes (including this one). Releases the locks and all resources at the end of the action.
     *
     * <p>This method is re-entrant, so that an action can call back into this method.</p>
     */
    <T> T useCache(String operationDisplayName, Factory<? extends T> action);

    /**
     * Performs some work against the cache. Acquires exclusive locks the appropriate resources, so that the given action is the only
     * action to execute across all processes (including this one). Releases the locks and all resources at the end of the action.
     *
     * <p>This method is re-entrant, so that an action can call back into this method.</p>
     */
    void useCache(String operationDisplayName, Runnable action);

    /**
     * Performs some long running operation within an action invoked by {@link #useCache(String, org.gradle.internal.Factory)}. Releases all
     * locks while the operation is running, and reacquires the locks at the end of the long running operation.
     *
     * <p>This method is re-entrant, so that an action can call back into this method.</p>
     */
    <T> T longRunningOperation(String operationDisplayName, Factory<? extends T> action);

    /**
     * Performs some long running operation within an action invoked by {@link #useCache(String, org.gradle.internal.Factory)}. Releases all
     * locks while the operation is running, and reacquires the locks at the end of the long running operation.
     *
     * <p>This method is re-entrant, so that an action can call back into this method.</p>
     */
    void longRunningOperation(String operationDisplayName, Runnable action);
}
