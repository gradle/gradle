/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.execution;

/**
 * A {@code TaskExecutionResult} contains the result of executing a task.
 */
public interface TaskExecutionResult {
    /**
     * Returns the exception describing the task failure, if any.
     *
     * @return The exception, or null if the task did not fail.
     */
    Throwable getFailure();

    /**
     * Throws the task failure, if any. Does nothing if the task did not fail.
     */
    void rethrowFailure();

    /**
     * Returns a message describing why the task was skipped.
     *
     * @return the mesages. returns null if the task was not skipped.
     */
    String getSkipMessage();
}
