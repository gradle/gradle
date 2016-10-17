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

import org.gradle.internal.Factory;

public interface AsyncCacheAccess {
    /**
     * Submits the given action for execution without waiting for the result.
     *
     * An implementation may execute the action immediately or later. All actions submitted by this method must complete before any action submitted to {@link #read(Factory)} is executed. Actions submitted using this method must run in the order that they are submitted.
     */
    void enqueue(Runnable task);

    /**
     * Runs the given action, blocking until the result is available.
     *
     * All actions submitted using {@link #enqueue(Runnable)} must complete before the action is executed.
     */
    <T> T read(Factory<T> task);

    /**
     * Blocks until all submitted actions have completed. Rethrows any update failure.
     */
    void flush();
}
