/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.build;

import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.Factory;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.Consumer;

public interface BuildProjectRegistry {
    /**
     * Returns the root project of this build.
     */
    ProjectState getRootProject();

    /**
     * Returns all projects in this build, in public iteration order.
     */
    Set<? extends ProjectState> getAllProjects();

    /**
     * Locates a project of this build, failing if the project is not present.
     *
     * @param projectPath The path relative to the root project of this build.
     */
    ProjectState getProject(Path projectPath);

    /**
     * Locates a project of this build, returning null if the project is not present.
     *
     * @param projectPath The path relative to the root project of this build.
     */
    @Nullable
    ProjectState findProject(Path projectPath);

    /**
     * Allows a section of code to run against the mutable state of all projects of this build. No other thread will be able to access the state of any project of this build while the given action is running.
     *
     * <p>Any attempt to lock a project by some other thread will block while the given action is running. This includes calls to {@link ProjectState#applyToMutableState(Consumer)}.
     */
    void withMutableStateOfAllProjects(Runnable runnable);

    /**
     * Allows a section of code to run against the mutable state of all projects of this build. No other thread will be able to access the state of any project of this build while the given action is running.
     *
     * <p>Any attempt to lock a project by some other thread will block while the given action is running. This includes calls to {@link ProjectState#applyToMutableState(Consumer)}.
     */
    <T> T withMutableStateOfAllProjects(Factory<T> factory);
}
