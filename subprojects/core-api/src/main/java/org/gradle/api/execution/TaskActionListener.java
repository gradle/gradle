/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.internal.DeprecatedInGradleScope;
import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scope;

/**
 * <p>A {@code TaskActionListener} is notified of the actions that a task performs.</p>
 *
 * <p>This listener type is not supported when the configuration cache is enabled.
 * To run code right before and after a task executes, use a {@link org.gradle.api.tasks.TaskLifecycleListener}.
 * It is called in more cases and at different points than this listener:</p>
 * <ul>
 *     <li>It is also called for tasks whose actions do not run, such as up-to-date tasks or tasks loaded from the build cache.
 *     {@link org.gradle.api.tasks.TaskState#getSkipped()} tells whether the actions ran.</li>
 *     <li>{@code beforeExecution} is called before the task inputs are snapshotted, while {@link #beforeActions(Task)} is called after.</li>
 *     <li>{@code afterExecution} is called after the task outputs are recorded, so it must not write to them, while {@link #afterActions(Task)} is called before.</li>
 * </ul>
 *
 * @see org.gradle.api.tasks.TaskLifecycleListener
 * @deprecated This type is not supported when configuration caching is enabled.
 * @since 0.9
 */
@EventScope(Scope.Build.class)
@Deprecated
@DeprecatedInGradleScope
public interface TaskActionListener {
    /**
     * This method is called immediately before the task starts performing its actions.
     *
     * @param task The task which is to perform some actions.
     * @since 0.9
     */
    void beforeActions(Task task);

    /**
     * This method is called immediately after the task has completed performing its actions.
     *
     * @param task The task which has performed some actions.
     * @since 0.9
     */
    void afterActions(Task task);
}
