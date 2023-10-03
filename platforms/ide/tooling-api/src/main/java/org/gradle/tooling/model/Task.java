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
package org.gradle.tooling.model;

import org.gradle.api.Incubating;

import javax.annotation.Nullable;

/**
 * Represents a task which is executable by Gradle.
 *
 * <p>Note: {@code Task} extends {@code Launchable} since 1.12.</p>
 *
 * @since 1.0-milestone-3
 */
public interface Task extends Launchable {
    /**
     * Returns the path of this task. This is a fully qualified unique name for this task.
     *
     * @return The path of this task.
     * @since 1.0-milestone-3
     */
    String getPath();

    /**
     * Returns the path of this task within the build tree. This is a unique name for this task within the composite build.
     *
     * @return The path of this task in the composite build.
     * @throws org.gradle.tooling.model.UnsupportedMethodException When the target Gradle version does not support this method.
     * @since 8.2
     */
    @Incubating
    String getBuildTreePath();

    /**
     * Returns the name of this task. Note that the name is not necessarily a unique identifier for the task.
     *
     * @return The name of this task.
     * @since 1.0-milestone-3
     */
    String getName();

    /**
     * Returns the description of this task, or {@code null} if it has no description.
     *
     * @return The description of this task, or {@code null} if it has no description.
     * @since 1.0-milestone-3
     */
    @Override
    @Nullable
    String getDescription();

    /**
     * Returns the group a task belongs to.
     *
     * @return the group a task belongs to.
     * @since 2.5
     */
    @Nullable
    String getGroup();
}
