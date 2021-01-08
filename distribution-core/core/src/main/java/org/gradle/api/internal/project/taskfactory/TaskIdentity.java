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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.util.Path;

import java.util.concurrent.atomic.AtomicLong;

@UsedByScanPlugin
public final class TaskIdentity<T extends Task> {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    public final Class<T> type;
    public final String name;
    public final Path projectPath; // path within its build (i.e. including project path)
    public final Path identityPath; // path within the build tree (i.e. including project path)
    public final Path buildPath; // path of the owning build

    /**
     * Tasks can be replaced in Gradle, meaning there can be two different tasks with the same path/type.
     * This allows identifying a precise instance.
     */
    public final long uniqueId;

    @VisibleForTesting
    TaskIdentity(Class<T> type, String name, Path projectPath, Path identityPath, Path buildPath, long uniqueId) {
        this.name = name;
        this.projectPath = projectPath;
        this.identityPath = identityPath;
        this.buildPath = buildPath;
        this.type = type;
        this.uniqueId = uniqueId;
    }

    public static <T extends Task> TaskIdentity<T> create(String name, Class<T> type, ProjectInternal project) {
        return new TaskIdentity<T>(
            type,
            name,
            project.projectPath(name),
            project.identityPath(name),
            project.getGradle().getIdentityPath(),
            SEQUENCE.getAndIncrement()
        );
    }

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
        return (int) (uniqueId ^ (uniqueId >>> 32));
    }

    @Override
    public String toString() {
        return "TaskIdentity{path=" + identityPath + ", type=" + type + ", uniqueId=" + uniqueId + '}';
    }

    public String getTaskPath() {
        return projectPath.getPath();
    }

    public String getProjectPath() {
        return projectPath.getParent().getPath();
    }

    public String getIdentityPath() {
        return identityPath.getPath();
    }

    public String getBuildPath() {
        return buildPath.getPath();
    }
}
