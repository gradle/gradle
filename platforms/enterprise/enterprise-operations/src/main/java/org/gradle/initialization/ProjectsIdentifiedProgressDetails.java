/*
 * Copyright 2022 the original author or authors.
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

import java.util.Set;

/**
 * Fired once the hierarchy of projects is finalized for a build in the tree, and before any build operations
 * that reference those projects are executed.
 *
 * @since 8.0
 */
public interface ProjectsIdentifiedProgressDetails {
    /**
     * @see LoadProjectsBuildOperationType.Result#getBuildPath()
     */
    String getBuildPath();

    /**
     * @see LoadProjectsBuildOperationType.Result#getRootProject()
     */
    Project getRootProject();

    interface Project {
        /**
         * @see LoadProjectsBuildOperationType.Result.Project#getName()
         */
        String getName();

        /**
         * @see LoadProjectsBuildOperationType.Result.Project#getPath()
         */
        String getPath();

        /**
         * @see LoadProjectsBuildOperationType.Result.Project#getIdentityPath()
         */
        String getIdentityPath();

        /**
         * @see LoadProjectsBuildOperationType.Result.Project#getProjectDir()
         */
        String getProjectDir();

        /**
         * @see LoadProjectsBuildOperationType.Result.Project#getBuildFile()
         */
        String getBuildFile();

        /**
         * @see LoadProjectsBuildOperationType.Result.Project#getChildren()
         */
        Set<? extends Project> getChildren();
    }
}
