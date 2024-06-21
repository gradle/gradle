/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.util.Path;

import java.util.Objects;

public final class ProjectIdentity {

    private final BuildIdentifier buildIdentifier;
    private final Path buildTreePath;
    private final Path projectPath;
    private final String projectName;

    public ProjectIdentity(
        BuildIdentifier buildIdentifier,
        Path buildTreePath,
        Path projectPath,
        String projectName
    ) {
        this.buildIdentifier = buildIdentifier;
        this.buildTreePath = buildTreePath;
        this.projectPath = projectPath;
        this.projectName = projectName;
    }

    public BuildIdentifier getBuildIdentifier() {
        return buildIdentifier;
    }

    public Path getBuildTreePath() {
        return buildTreePath;
    }

    public Path getProjectPath() {
        return projectPath;
    }

    public String getProjectName() {
        return projectName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProjectIdentity that = (ProjectIdentity) o;
        return Objects.equals(buildTreePath, that.buildTreePath);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(buildTreePath);
    }

}
