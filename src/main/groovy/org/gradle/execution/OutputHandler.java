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
package org.gradle.execution;

import org.gradle.api.Task;

/**
 * @author Hans Dockter
 */
public interface OutputHandler {
    /**
     * Returns whether a task produces output or not. If this property is true, Gradle will store the execution
     * history for this task which can be used by other tasks to decide whether they should do work or not.
     * If this property is set to true, the {@link org.gradle.api.Task#getDidWork()} should have a custom implementation.
     */
    boolean getHasOutput();

    /**
     * Persists the history of the task execution.
     *
     * @param successful Whether the task execution was successful
     */
    void writeHistory(boolean successful);
}
