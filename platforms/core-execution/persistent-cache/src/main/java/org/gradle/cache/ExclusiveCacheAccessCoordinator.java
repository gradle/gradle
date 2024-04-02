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

import java.util.function.Supplier;

/**
 * Provides synchronised access to a cache.
 */
public interface ExclusiveCacheAccessCoordinator {
    /**
     * Performs some work against the cache. Acquires exclusive locks on the appropriate resources, so that the given action is the only action to execute across all processes (including this one). Releases the locks and all resources at the end of the action.
     *
     * <p>This method is re-entrant, so that an action can call back into this method.</p>
     *
     * <p>Note: this method differs from {@link #withFileLock(Supplier)} in that this method also blocks other threads from this process and all threads from other processes from accessing the cache.</p>
     */
    <T> T useCache(Supplier<? extends T> action);

    /**
     * Performs some work against the cache. Acquires exclusive locks on the appropriate resources, so that the given action is the only action to execute across all processes (including this one). Releases the locks and all resources at the end of the action.
     *
     * <p>This method is re-entrant, so that an action can call back into this method.</p>
     *
     * <p>Note: this method differs from {@link #withFileLock(Runnable)} in that this method also blocks other threads from this process and all threads from other processes from accessing the cache.</p>
     */
    void useCache(Runnable action);

    /**
     * Performs some work against the cache. Acquires exclusive locks on the appropriate file resources, so that only the actions from this process may run. Releases the locks and all resources at the end of the action. Allows other threads from this process to execute, but does not allow any threads from any other process to run.
     *
     * <p>This method is re-entrant, so that an action can call back into this method.</p>
     */
    <T> T withFileLock(Supplier<? extends T> action);

    /**
     * Performs some work against the cache. Acquires exclusive locks on the appropriate file resources, so that only the actions from this process may run. Releases the locks and all resources at the end of the action. Allows other threads from this process to execute, but does not allow any threads from any other process to run.
     *
     * <p>This method is re-entrant, so that an action can call back into this method.</p>
     */
    void withFileLock(Runnable action);

}
