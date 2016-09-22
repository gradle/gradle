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

import org.gradle.api.Task;

/**
 * <p>A {@code TaskActionListener} is notified of the actions that a task performs.</p>
 */
public interface TaskActionListener {
    /**
     * This method is called immediately before the task starts performing its actions.
     *
     * @param task The task which is to perform some actions.
     */
    void beforeActions(Task task);

    /**
     * This method is called immediately after the task has completed performing its actions.
     *
     * @param task The task which has performed some actions.
     */
    void afterActions(Task task);
}
