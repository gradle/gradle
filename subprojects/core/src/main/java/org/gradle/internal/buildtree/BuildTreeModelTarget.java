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

import java.io.File;
import java.util.Objects;

/**
 * Target for model building.
 * <p>
 * The target is identified by minimal information provided via Tooling API,
 * such as the root directory of the target build.
 */
public abstract class BuildTreeModelTarget {

    @SuppressWarnings("StaticInitializerReferencesSubClass")
    private static final Default DEFAULT = new Default();

    public static Default ofDefault() {
        return DEFAULT;
    }

    public static Build ofBuild(File buildRootDir) {
        return new Build(buildRootDir);
    }

    public static Project ofProject(File buildRootDir, String projectPath) {
        return new Project(buildRootDir, Path.path(projectPath));
    }

    public static class Default extends BuildTreeModelTarget {
        private Default() {}

        @Override
        public String toString() {
            return "Default";
        }
    }

    public static class Build extends BuildTreeModelTarget {

        private final File buildRootDir;

        private Build(File buildRootDir) {
            this.buildRootDir = buildRootDir;
        }

        public File getBuildRootDir() {
            return buildRootDir;
        }

        @Override
        public final boolean equals(Object o) {
            if (!(o instanceof Build)) {
                return false;
            }

            Build build = (Build) o;
            return buildRootDir.equals(build.buildRootDir);
        }

        @Override
        public int hashCode() {
            return buildRootDir.hashCode();
        }

        @Override
        public String toString() {
            return "Build{" +
                "buildRootDir=" + buildRootDir +
                '}';
        }
    }

    public static class Project extends BuildTreeModelTarget {

        private final File buildRootDir;
        private final Path projectPath;

        private Project(File buildRootDir, Path projectPath) {
            this.buildRootDir = buildRootDir;
            this.projectPath = projectPath;
        }

        public File getBuildRootDir() {
            return buildRootDir;
        }

        public Path getProjectPath() {
            return projectPath;
        }

        @Override
        public final boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Project)) {
                return false;
            }

            Project that = (Project) o;
            return buildRootDir.equals(that.buildRootDir) && Objects.equals(projectPath, that.projectPath);
        }

        @Override
        public int hashCode() {
            int result = buildRootDir.hashCode();
            result = 31 * result + Objects.hashCode(projectPath);
            return result;
        }

        @Override
        public String toString() {
            return "Project{" +
                "buildRootDir=" + buildRootDir +
                ", projectPath=" + projectPath +
                '}';
        }
    }
}
