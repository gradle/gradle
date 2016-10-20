/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.internal.concurrent;

public interface ExecutorFactory {
    /**
     * Creates an executor which can run multiple actions concurrently. It is the caller's responsibility to stop the executor.
     *
     * The executor will collect failures thrown by actions and rethrow when the executor is stopped.
     *
     * @param displayName The display name for the this executor. Used for thread names, logging and error message.
     * @return The executor.
     */
    StoppableExecutor create(String displayName);

    /**
     * Creates an executor which can run multiple tasks concurrently. It is the caller's responsibility to stop the executor.
     *
     * The executor will collect failures thrown by actions and rethrow when the executor is stopped.
     *
     * @param displayName The display name for the this executor. Used for thread names, logging and error message.
     * @param fixedSize The maximum number of threads allowed
     *
     * @return The executor.
     */
    StoppableExecutor create(String displayName, int fixedSize);
}
