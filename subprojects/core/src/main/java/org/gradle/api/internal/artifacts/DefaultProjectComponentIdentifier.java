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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.util.Path;

public class DefaultProjectComponentIdentifier implements ProjectComponentIdentifierInternal {
    private final BuildIdentifier buildIdentifier;
    private final Path projectPath;
    private final Path identityPath;
    private final String projectName;
    private String displayName;

    public DefaultProjectComponentIdentifier(BuildIdentifier buildIdentifier, Path identityPath, Path projectPath, String projectName) {
        assert buildIdentifier != null : "build cannot be null";
        assert identityPath != null : "identity path cannot be null";
        assert projectPath != null : "project path cannot be null";
        assert projectName != null : "project name cannot be null";
        this.identityPath = identityPath;
        this.projectName = projectName;
        this.buildIdentifier = buildIdentifier;
        this.projectPath = projectPath;
    }

    @Override
    public String getDisplayName() {
        if (displayName == null) {
            displayName = "project " + identityPath.getPath();
        }
        return displayName;
    }

    @Override
    public BuildIdentifier getBuild() {
        return buildIdentifier;
    }

    @Override
    public Path getIdentityPath() {
        return identityPath;
    }

    @Override
    public String getProjectPath() {
        return projectPath.getPath();
    }

    public Path projectPath() {
        return projectPath;
    }

    @Override
    public String getBuildTreePath() {
        return identityPath.getPath();
    }

    @Override
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

        DefaultProjectComponentIdentifier that = (DefaultProjectComponentIdentifier) o;
        return identityPath.equals(that.identityPath);
    }

    @Override
    public int hashCode() {
        return identityPath.hashCode();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
