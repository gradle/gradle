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

        ProjectDescriptor getRootProject();

        String getBuildPath();
    }

    public static class ProjectDescriptor {
        final String name;
        final String path;
        final String projectDir;
        final String buildFile;
        final Set<ProjectDescriptor> children;

        public ProjectDescriptor(String name, String path, String projectDir, String buildFile, Set<ProjectDescriptor> children){
            this.name = name;
            this.path = path;
            this.projectDir = projectDir;
            this.buildFile = buildFile;
            this.children = children;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        public String getProjectDir() {
            return projectDir;
        }

        public String getBuildFile() {
            return buildFile;
        }

        public Set<ProjectDescriptor> getChildren() {
            return children;
        }
    }
}
