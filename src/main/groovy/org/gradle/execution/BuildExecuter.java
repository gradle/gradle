/*
 * Copyright 2008 the original author or authors.
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

import org.gradle.api.Project;

/**
 * Selects and executes the groups of tasks requested for a build.
 */
public interface BuildExecuter {
    /**
     * Returns true if this selector has another group of tasks to execute.
     */
    boolean hasNext();

    /**
     * Moves to the next group of tasks to execute.  The project is not rebuilt while executing a single group of
     * tasks, but may be rebuilt between groups.
     */
    void select(Project project);

    /**
     * Returns the description of the current group.
     */
    String getDescription();

    /**
     * Executes the current group of tasks.
     */
    void execute(DefaultTaskExecuter executer);

    /**
     * Returns true if the project heirarchiy should be reloaded before executing the current group, false if not.
     */
    boolean requiresProjectReload();
}
