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
 * Details about a Settings being configured.
 *
 * @since 4.2
 */
public final class ConfigureSettingsBuildOperationType implements BuildOperationType<ConfigureSettingsBuildOperationType.Details, ConfigureSettingsBuildOperationType.Result> {

    @UsedByScanPlugin
    public interface Details {
        /**
         * The absolute path to the settings directory.
         */
        String getSettingsDir();

        /**
         * The absolute path to the settings file.
         */
        String getSettingsFile();
    }

    public interface Result {

        /**
         * A description of the root Project.
         *
         * @see org.gradle.api.initialization.Settings#getRootProject()
         */
        ProjectDescription getRootProject();

        /**
         * The build path of the root Project.
         *
         * @see org.gradle.api.internal.GradleInternal#getIdentityPath()
         */
        String getBuildPath();

        interface ProjectDescription {

            /**
             * The name of the project.
             *
             * @see org.gradle.api.initialization.ProjectDescriptor#getName()
             */
            String getName();

            /**
             * The path of the project.
             *
             * @see org.gradle.api.initialization.ProjectDescriptor#getPath()
             */
            String getPath();

            /**
             * The absolute file path of the project directory.
             *
             * @see org.gradle.api.initialization.ProjectDescriptor#getProjectDir()
             */
            String getProjectDir();

            /**
             * The absolute file path of the projects build file.
             *
             * @see org.gradle.api.initialization.ProjectDescriptor#getBuildFile()
             */
            String getBuildFile();

            /**
             * The child projects of this project.
             * No null values.
             * Ordered by project name lexicographically.
             *
             * @see org.gradle.api.initialization.ProjectDescriptor#getChildren()
             */

            Set<ProjectDescription> getChildren();
        }
    }


}
