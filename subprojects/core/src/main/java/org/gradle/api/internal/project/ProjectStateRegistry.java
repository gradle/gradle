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
package org.gradle.api.internal.project;

import org.gradle.api.Project;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.initialization.DefaultProjectDescriptor;
import org.gradle.internal.Factory;
import org.gradle.internal.build.BuildState;
import org.gradle.util.Path;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Collection;

/**
 * A registry of all of the projects present in a build tree.
 */
@ThreadSafe
public interface ProjectStateRegistry {
    /**
     * Returns all projects in the build tree.
     */
    Collection<? extends ProjectState> getAllProjects();

    /**
     * Locates the state object that owns the given public project model. Can use {@link ProjectInternal#getMutationState()} instead.
     */
    ProjectState stateFor(Project project) throws IllegalArgumentException;

    /**
     * Locates the state object that owns the project with the given identifier.
     */
    ProjectState stateFor(ProjectComponentIdentifier identifier) throws IllegalArgumentException;

    /**
     * Locates the state object for the given project.
     */
    ProjectState stateFor(BuildIdentifier buildIdentifier, Path projectPath) throws IllegalArgumentException;

    /**
     * Registers the projects of a build.
     */
    void registerProjects(BuildState build);

    /**
     * Registers a single project.
     */
    void registerProject(BuildState owner, DefaultProjectDescriptor projectDescriptor);

    /**
     * Allows a section of code to be run with state locking disabled.  This should be used to allow
     * deprecated practices that we eventually want to retire.
     */
    void withLenientState(Runnable runnable);

    /**
     * Creates the object with state locking disabled.  This should be used to allow
     * deprecated practices that we eventually want to retire.
     */
    <T> T withLenientState(Factory<T> factory);

    /**
     * Returns a {@link SafeExclusiveLock}.
     */
    SafeExclusiveLock newExclusiveOperationLock();

    /**
     * Represents a lock that can be used to perform safe concurrent execution in light of the possibility that a project
     * lock might be released during execution.  Specifically, it avoids blocking on the lock while holding the project lock.
     */
    interface SafeExclusiveLock {
        /**
         * Safely waits for the lock before executing the given action.
         */
        void withLock(Runnable runnable);
    }
}
