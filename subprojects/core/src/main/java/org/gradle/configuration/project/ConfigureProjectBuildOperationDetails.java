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

package org.gradle.configuration.project;

import org.gradle.internal.progress.NoResultBuildOperationDetails;
import org.gradle.util.Path;


/**
 * Details about a project being configured.
 *
 * This class is intentionally internal and consumed by the build scan plugin.
 *
 * @since 4.0
 */
public final class ConfigureProjectBuildOperationDetails implements NoResultBuildOperationDetails {
    private final Path buildPath;
    private final Path projectPath;

    ConfigureProjectBuildOperationDetails(Path projectPath, Path buildPath) {
        this.projectPath = projectPath;
        this.buildPath = buildPath;
    }

    public Path getProjectPath() {
        return projectPath;
    }

    public Path getBuildPath() {
        return buildPath;
    }

}
