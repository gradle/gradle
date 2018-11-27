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
package org.gradle.api.internal.artifacts.dsl.dependencies;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.project.ProjectInternal;

import javax.annotation.Nullable;

public interface ProjectFinder {
    /**
     * Locates the project with the provided path, failing if not found.
     *
     * @param path Can be relative or absolute
     * @return The project belonging to the path, never null.
     */
    ProjectInternal getProject(String path);

    /**
     * Locates the project with the provided path, or <code>null</code> if not found.
     *
     * @param path Can be relative or absolute
     * @return The project belonging to the path, or null if not found.
     */
    @Nullable
    ProjectInternal findProject(String path);

    /**
     * Locates the project in the provided build with the provided path, or <code>null</code> if not found.
     *
     * @param build The build id of the build containing the project
     * @param path Needs to be absolute
     * @return The project belonging to the path in the build, or null if not found.
     */
    @Nullable
    ProjectInternal findProject(BuildIdentifier build, String path);
}
