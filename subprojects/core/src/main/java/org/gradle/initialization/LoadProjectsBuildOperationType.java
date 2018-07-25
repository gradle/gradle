/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;

import java.util.Set;

/**
 * An operation to load the project structure from the processed settings.
 * Provides details of the project structure without projects being configured.
 *
 * @since 4.2
 */
public final class LoadProjectsBuildOperationType implements BuildOperationType<LoadProjectsBuildOperationType.Details, LoadProjectsBuildOperationType.Result> {

    @UsedByScanPlugin
    public interface Details {
        /**
         * @since 4.6
         */
        String getBuildPath();
    }

    public interface Result {
        /**
         * The path of the build configuration that contains these projects.
         * This will be ':' for top-level builds. Nested builds will have a sub-path.
         *
         * @see org.gradle.api.internal.GradleInternal#getIdentityPath()
         */
        String getBuildPath();

        /**
         * A description of the root Project for this build.
         *
         * @see org.gradle.api.initialization.Settings#getRootProject()
         */
        Project getRootProject();

        interface Project {

            /**
             * The name of the project.
             *
             * @see org.gradle.api.Project#getName()
             */
            String getName();

            /**
             * The path of the project.
             *
             * @see org.gradle.api.Project#getPath()
             */
            String getPath();

            /**
             * The path of the project within the entire build execution.
             * For top-level builds this will be the same as {@link #getPath()}.
             * For nested builds the project path will be prefixed with a build path.
             *
             * @see org.gradle.api.internal.project.ProjectInternal#getIdentityPath()
             */
            String getIdentityPath();

            /**
             * The absolute file path of the project directory.
             *
             * @see org.gradle.api.Project#getProjectDir()
             */
            String getProjectDir();

            /**
             * The absolute file path of the projects build file.
             *
             * @see org.gradle.api.Project#getBuildFile()
             */
            String getBuildFile();

            /**
             * The child projects of this project.
             * No null values.
             * Ordered by project name lexicographically.
             *
             * @see org.gradle.api.Project#getChildProjects()
             */

            Set<Project> getChildren();
        }
    }


}
