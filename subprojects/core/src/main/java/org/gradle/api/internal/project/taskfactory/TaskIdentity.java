/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.project.taskfactory;

import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.util.Path;

@UsedByScanPlugin
public final class TaskIdentity<T extends Task> {

    // TODO #34344: Remove these public fields and String-typed getters.

    /**
     * The type of the task.
     *
     * @deprecated Use {@link #getTaskType()} instead.
     */
    @Deprecated
    public final Class<T> type;

    /**
     * The name of the task.
     *
     * @deprecated Use {@link #getName()} instead.
     */
    @Deprecated
    public final String name;

    /**
     * The path of the task within the build.
     *
     * @deprecated Use {@link #getPath()} instead.
     */
    @Deprecated
    @UsedByScanPlugin("ImportJUnitXmlReports")
    public final Path projectPath;

    /**
     * The path of the task within the build tree.
     *
     * @deprecated Use {@link #getBuildTreePath()} instead.
     */
    @Deprecated
    public final Path identityPath;

    /**
     * The path of the owning build.
     *
     * @deprecated Use {@link #getProjectIdentity()} instead.
     */
    @Deprecated
    @UsedByScanPlugin("ImportJUnitXmlReports")
    public final Path buildPath;

    /**
     * A unique identifier for the task within the build.
     *
     * @deprecated Use {@link #getId()} instead.
     */
    @Deprecated
    public final long uniqueId;

    private final ProjectIdentity projectIdentity;

    TaskIdentity(Class<T> type, String name, ProjectIdentity projectIdentity, long uniqueId) {
        this.name = name;
        this.type = type;
        this.projectIdentity = projectIdentity;
        this.uniqueId = uniqueId;

        this.projectPath = projectIdentity.getProjectPath().child(name);
        this.identityPath = projectIdentity.getBuildTreePath().child(name);
        this.buildPath = projectIdentity.getBuildPath();
    }

    /**
     * Get the name of the task.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the unique identifier for the task within the build.
     * <p>
     * Tasks can be replaced in Gradle, meaning there can be two different tasks with the same path/type.
     * This allows identifying a precise instance.
     */
    public long getId() {
        return uniqueId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TaskIdentity<?> that = (TaskIdentity<?>) o;
        return this.uniqueId == that.uniqueId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(uniqueId);
    }

    @Override
    public String toString() {
        return "TaskIdentity{path=" + identityPath + ", type=" + type + ", uniqueId=" + uniqueId + '}';
    }

    /**
     * Get the path of this task within the build.
     */
    public Path getPath() {
        return projectPath;
    }

    /**
     * Get the path of this task within the build tree.
     */
    public Path getBuildTreePath() {
        return identityPath;
    }

    /**
     * Get the path of this task within the build.
     *
     * @deprecated Use {@link #getPath()} instead.
     */
    @Deprecated
    public String getTaskPath() {
        return getPath().asString();
    }

    /**
     * @deprecated Use {@link #getProjectIdentity()} instead.
     */
    @Deprecated
    public String getProjectPath() {
        return getProjectIdentity().getProjectPath().asString();
    }

    /**
     * Get the path of this task within the build tree.
     *
     * @deprecated Use {@link #getBuildTreePath()} instead.
     */
    @Deprecated
    public String getIdentityPath() {
        return getBuildTreePath().asString();
    }

    /**
     * @deprecated Use {@link #getProjectIdentity()} instead.
     */
    @Deprecated
    public String getBuildPath() {
        return getProjectIdentity().getBuildPath().asString();
    }

    /**
     * Get the type of the task.
     */
    public Class<T> getTaskType() {
        return type;
    }

    /**
     * Get the identity of the project that owns this task.
     */
    public ProjectIdentity getProjectIdentity() {
        return projectIdentity;
    }

}
