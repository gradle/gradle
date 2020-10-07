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
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * A registry of all of the projects present in a build tree.
 */
@ThreadSafe
@ServiceScope(Scopes.BuildTree.class)
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
     * Allows a section of code to run against the mutable state of all projects. No other thread will be able to access the state of any project while the given action is running.
     *
     * <p>Any attempt to lock a project by some other thread will fail while the given action is running. This includes calls to {@link ProjectState#applyToMutableState(Consumer)}.
     */
    void withMutableStateOfAllProjects(Runnable runnable);

    /**
     * Allows a section of code to run against the mutable state of all projects. No other thread will be able to access the state of any project while the given action is running.
     *
     * <p>Any attempt to lock a project by some other thread will fail while the given action is running. This includes calls to {@link ProjectState#applyToMutableState(Consumer)}.
     */
    <T> T withMutableStateOfAllProjects(Factory<T> factory);

    /**
     * Allows the given code to access the mutable state of any project, regardless of which other threads may be accessing the project.
     *
     * DO NOT USE THIS METHOD. It is here to allow some very specific backwards compatibility.
     */
    <T> T allowUncontrolledAccessToAnyProject(Factory<T> factory);

    /**
     * Allows the given code to access the mutable state of any project, regardless of which other threads may be accessing the project.
     *
     * DO NOT USE THIS METHOD. It is here to allow some very specific backwards compatibility.
     */
    void allowUncontrolledAccessToAnyProject(Runnable runnable);
}
