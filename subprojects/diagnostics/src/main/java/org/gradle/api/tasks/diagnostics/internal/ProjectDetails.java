/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal;

import org.gradle.api.Project;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Provides common projections for selected project properties.
 */
public interface ProjectDetails {

    String getDisplayName();

    @Nullable
    String getDescription();

    static ProjectDetails of(Project project) {
        return withDisplayNameAndDescription(project);
    }

    static ProjectNameAndPath withNameAndPath(Project project) {
        return new ProjectNameAndPath(project);
    }

    static ProjectDisplayNameAndDescription withDisplayNameAndDescription(Project project) {
        return new ProjectDisplayNameAndDescription(project);
    }

    class ProjectDisplayNameAndDescription implements ProjectDetails {
        private final String displayName;
        private final String description;

        private ProjectDisplayNameAndDescription(Project project) {
            displayName = project.getDisplayName();
            description = project.getDescription();
        }

        public String getDisplayName() {
            return displayName;
        }

        @Nullable
        public String getDescription() {
            return description;
        }

        @Override
        public int hashCode() {
            return Objects.hash(displayName, description);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ProjectDisplayNameAndDescription)) {
                return false;
            }
            ProjectDisplayNameAndDescription that = (ProjectDisplayNameAndDescription) obj;
            return Objects.equals(displayName, that.displayName) && Objects.equals(description, that.description);
        }
    }
    class ProjectNameAndPath extends ProjectDisplayNameAndDescription {
        private final String name;
        private final String path;

        private ProjectNameAndPath(Project project) {
            super(project);
            this.name = project.getName();
            this.path = project.getPath();
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, path, super.hashCode());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!super.equals(obj)) {
                return false;
            }
            if (!(obj instanceof ProjectNameAndPath)) {
                return false;
            }
            ProjectNameAndPath that = (ProjectNameAndPath) obj;
            return Objects.equals(name, that.name) && Objects.equals(path, that.path);
        }
    }
}
