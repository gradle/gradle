/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.project;

import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.isolated.models.IsolatedModelRouter;

/**
 * An isolated view of {@link Project} that exposes only those properties that are safe to access from outside of
 * <code>this</code> project, from the perspective of isolated projects.
 *
 * @since 8.8
 */
@Incubating
public interface IsolatedProject {

    /**
     * <p>Returns the name of this project. The project's name is not necessarily unique within a project hierarchy. You
     * should use the {@link #getPath()} method for a unique identifier for the project.
     * If the root project is unnamed and is located on a file system root it will have a randomly-generated name
     * </p>
     *
     * @return The name of this project. Never return null.
     * @since 8.8
     */
    String getName();

    /**
     * <p>Returns the path of this project.  The path is the fully qualified name of the project.</p>
     *
     * @return The path. Never returns null.
     * @since 8.8
     */
    String getPath();

    /**
     * Returns a path to the project for the full build tree.
     *
     * @return The build tree path
     * @since 8.9
     */
    @Incubating
    String getBuildTreePath();

    /**
     * <p>The directory containing the project build file.</p>
     *
     * @return The project directory. Never returns null.
     * @since 8.8
     */
    Directory getProjectDirectory();

    /**
     * <p>Returns the root project for the hierarchy that this project belongs to.  In the case of a single-project
     * build, this method returns this project.</p>
     *
     * @return The root project. Never returns null.
     * @since 8.8
     */
    IsolatedProject getRootProject();

    /**
     * TBD
     *
     * @since 8.12
     */
    IsolatedModelRouter getModels();

    @Override
    int hashCode();

    @Override
    boolean equals(Object obj);
}
