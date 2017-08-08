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

import java.io.File;
import java.util.Set;

/**
 * Details about a Settings being configured.
 *
 * @since 4.2
 */
public final class ConfigureSettingsBuildOperationType implements BuildOperationType<ConfigureSettingsBuildOperationType.Details, ConfigureSettingsBuildOperationType.Result> {
    public interface Details {
    }

    public interface Result {
        ProjectDetails getRootProjectDescriptor();
    }

    public static class ProjectDetails {
        public final String name;
        public final String path;
        public final File projectDir;
        public final File buildFile;
        public final Set<ProjectDetails> children;

        public ProjectDetails(String name, String path, File projectDir, File buildFile, Set<ProjectDetails> children){
            this.name = name;
            this.path = path;
            this.projectDir = projectDir;
            this.buildFile = buildFile;
            this.children = children;
        }
    }
}
