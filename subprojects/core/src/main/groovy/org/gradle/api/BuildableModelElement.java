/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api;

/**
 * A model element that is directly buildable.
 * Such an element mirrors a specified lifecycle task in the DAG, and can accept dependencies which are then associated with the lifecycle task.
 */
@Incubating
public interface BuildableModelElement extends Buildable {
    /**
     * Returns the 'lifecycle' task associated with the construction of this element.
     */
    @Nullable
    Task getBuildTask();

    /**
     * Associates a 'lifecycle' task with the construction of this element.
     */
    void setBuildTask(Task lifecycleTask);

    /**
     * Adds a task that is required for the construction of this element.
     * A task added this way is then added as a dependency of the associated lifecycle task.
     */
    void builtBy(Object... tasks);

    boolean hasBuildDependencies();
}