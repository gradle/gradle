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
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

/**
 * Identifies a single project within the build and the build tree.
 * <p>
 * Consider a build tree with two builds: the root build and an included build, where both
 * builds have a root project and a subproject. The paths for each project are as follows:
 * <table>
 *     <caption>Project identity path values</caption>
 *     <tr>
 *         <th>Build path</th>
 *         <th>Project path</th>
 *         <th>Identity path</th>
 *     </tr>
 *     <tr>
 *         <th>:</th>
 *         <th>:</th>
 *         <th>:</th>
 *     </tr>
 *     <tr>
 *         <th>:</th>
 *         <th>:subproject</th>
 *         <th>:subproject</th>
 *     </tr>
 *     <tr>
 *         <th>:included</th>
 *         <th>:</th>
 *         <th>:included</th>
 *     </tr>
 *     <tr>
 *         <th>:included</th>
 *         <th>:subproject</th>
 *         <th>:included:subproject</th>
 *     </tr>
 * </table>
 */
public final class ProjectIdentity implements DisplayName {

    private final BuildIdentifier buildIdentifier;
    private final Path buildPath;
    private final Path buildTreePath;
    private final Path projectPath;
    private final String projectName;

    private final DisplayName displayName;

    public ProjectIdentity(
        BuildIdentifier buildIdentifier,
        Path buildTreePath,
        Path projectPath,
        String projectName
    ) {
        this.buildIdentifier = buildIdentifier;
        this.buildPath = Path.path(buildIdentifier.getBuildPath()); // TODO: Construct BuildIdentifier from the raw path, don't derive this path from the identifier's string
        this.buildTreePath = buildTreePath;
        this.projectPath = projectPath;
        this.projectName = projectName;

        // TODO: This is inconsistent with DefaultProject.getDisplayName.
        // We should change this to match that of DefaultProject.
        String prefix = Path.ROOT.equals(buildTreePath) ? "root project" : "project";
        this.displayName = Describables.memoize(Describables.of(prefix, buildTreePath.getPath()));
    }

    /**
     * The identity of the build that owns this project.
     * <p>
     * Prefer {@link #getBuildPath()}.
     */
    public BuildIdentifier getBuildIdentifier() {
        return buildIdentifier;
    }

    /**
     * The path of the build that owns this project.
     */
    public Path getBuildPath() {
        return buildPath;
    }

    /**
     * The identity of the project within its owning build.
     */
    public Path getProjectPath() {
        return projectPath;
    }

    /**
     * The identity of the project within the build tree.
     * <p>
     * This is the owning build path plus the project path.
     */
    public Path getBuildTreePath() {
        return buildTreePath;
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
    public String getCapitalizedDisplayName() {
        return displayName.getCapitalizedDisplayName();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public boolean equals(@Nullable Object o) {
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
