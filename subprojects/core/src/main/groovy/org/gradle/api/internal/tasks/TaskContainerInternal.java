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
package org.gradle.api.internal.tasks;

import org.gradle.api.internal.DynamicObject;
import org.gradle.api.tasks.TaskContainer;

public interface TaskContainerInternal extends TaskContainer, TaskResolver {
    DynamicObject getTasksAsDynamicObject();

    /**
     * Force the entire graph to come into existence.
     *
     * Tasks may have dependencies that are abstract (e.g. a dependency on a task _name_). Calling this method
     * will force all task dependencies to be actualised, which may mean new tasks are created because of things
     * like task rules etc.
     */
    void actualize();
}
