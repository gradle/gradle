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
     * Locates the state object that owns the given public project model.
     */
    ProjectState stateFor(Project project);

    /**
     * Locates the state object that owns the project with the given identifier.
     */
    ProjectState stateFor(ProjectComponentIdentifier identifier);

    /**
     * Locates the state object for the given project.
     */
    ProjectState stateFor(BuildIdentifier buildIdentifier, Path projectPath);

    /**
     * Registers a project.
     */
    void register(BuildState owner, ProjectInternal project);

    /**
     * Registers the projects of a build.
     */
    void registerProjects(BuildState build);
}
