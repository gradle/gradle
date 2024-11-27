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

package org.gradle.internal.buildtree;

import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Objects;

/**
 * Either a build or a project as a target for model building.
 * <p>
 * The target is identified by minimal information provided via Tooling API,
 * such as the root directory of the target build.
 */
public class BuildTreeModelTarget {

    public static BuildTreeModelTarget ofBuild(File buildRootDir) {
        return new BuildTreeModelTarget(buildRootDir, null);
    }

    public static BuildTreeModelTarget ofProject(File buildRootDir, String projectPath) {
        return new BuildTreeModelTarget(buildRootDir, Path.path(projectPath));
    }

    private final File buildRootDir;
    @Nullable
    private final Path projectPath;

    public BuildTreeModelTarget(File buildRootDir, @Nullable Path projectPath) {
        this.buildRootDir = buildRootDir;
        this.projectPath = projectPath;
    }

    public File getBuildRootDir() {
        return buildRootDir;
    }

    @Nullable
    public Path getProjectPath() {
        return projectPath;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BuildTreeModelTarget)) {
            return false;
        }

        BuildTreeModelTarget that = (BuildTreeModelTarget) o;
        return buildRootDir.equals(that.buildRootDir) && Objects.equals(projectPath, that.projectPath);
    }

    @Override
    public int hashCode() {
        int result = buildRootDir.hashCode();
        result = 31 * result + Objects.hashCode(projectPath);
        return result;
    }
}
