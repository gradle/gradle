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

import org.gradle.api.Describable;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.internal.Describables;
import org.gradle.util.Path;

/**
 * Identifies a single project within the build and the build tree.
 */
public final class ProjectIdentity implements Describable {

    private final BuildIdentifier buildIdentifier;
    private final Path buildTreePath;
    private final Path projectPath;
    private final String projectName;

    private final Describable displayName;

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

        String prefix = Path.ROOT.equals(buildTreePath) ? "root project" : "project";
        this.displayName = Describables.memoize(Describables.of(prefix, buildTreePath.getPath()));
    }

    /**
     * The identity of the owning build.
     */
    public BuildIdentifier getBuildIdentifier() {
        return buildIdentifier;
    }

    /**
     * The identity of the project within the build tree.
     */
    public Path getBuildTreePath() {
        return buildTreePath;
    }

    /**
     * The identity of the project within the owning build.
     */
    public Path getProjectPath() {
        return projectPath;
    }

    /**
     * The name of the project.
     */
    public String getProjectName() {
        return projectName;
    }

    @Override
    public String getDisplayName() {
        return displayName.getDisplayName();
    }

    @Override
    public String toString() {
        return getDisplayName();
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
        return buildTreePath.equals(that.buildTreePath);
    }

    @Override
    public int hashCode() {
        return buildTreePath.hashCode();
    }

}
