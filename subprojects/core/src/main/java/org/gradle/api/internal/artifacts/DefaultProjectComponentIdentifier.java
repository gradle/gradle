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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.util.Path;

public class DefaultProjectComponentIdentifier implements ProjectComponentIdentifierInternal {

    private final ProjectIdentity projectIdentity;

    public DefaultProjectComponentIdentifier(ProjectIdentity projectIdentity) {
        this.projectIdentity = projectIdentity;
    }

    /**
     * Prefer {@link #DefaultProjectComponentIdentifier(ProjectIdentity)}.
     */
    @VisibleForTesting
    public DefaultProjectComponentIdentifier(BuildIdentifier buildIdentifier, Path identityPath, Path projectPath, String projectName) {
        this(new ProjectIdentity(buildIdentifier, identityPath, projectPath, projectName));
    }

    @Override
    public ProjectIdentity getProjectIdentity() {
        return projectIdentity;
    }

    @Override
    public String getDisplayName() {
        return projectIdentity.getDisplayName();
    }

    @Override
    public BuildIdentifier getBuild() {
        return projectIdentity.getBuildIdentifier();
    }

    @Override
    public Path getIdentityPath() {
        return projectIdentity.getBuildTreePath();
    }

    @Override
    public String getProjectPath() {
        return projectIdentity.getProjectPath().getPath();
    }

    @Override
    public String getBuildTreePath() {
        return projectIdentity.getBuildTreePath().getPath();
    }

    @Override
    public String getProjectName() {
        return projectIdentity.getProjectName();
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
        return projectIdentity.equals(that.projectIdentity);
    }

    @Override
    public int hashCode() {
        return projectIdentity.hashCode();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
