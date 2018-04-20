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
     * Returns all projects from all implicitly included builds in the build tree.
     */
    Collection<? extends ProjectState> getAllImplicitProjects();

    /**
     * Returns all projects from all explicitly included builds in the build tree.
     */
    Collection<? extends ProjectState> getAllExplicitProjects();

    /**
     * Locates the state object that owns a given project object.
     */
    ProjectState stateFor(Project project);
}
