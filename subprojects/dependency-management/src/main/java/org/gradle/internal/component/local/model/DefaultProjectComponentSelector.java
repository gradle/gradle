/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.internal.component.local.model;

import com.google.common.base.Objects;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.initialization.IncludedBuild;

public class DefaultProjectComponentSelector implements ProjectComponentSelector {
    private final BuildIdentifier build;
    private final String projectPath;

    public DefaultProjectComponentSelector(BuildIdentifier build, String projectPath) {
        assert build != null : "build cannot be null";
        assert projectPath != null : "project path cannot be null";
        this.build = build;
        this.projectPath = projectPath;
    }

    public String getDisplayName() {
        if (!build.isCurrentBuild()) {
            return "project " + build.getName() + ":" + projectPath;
        }
        return "project " + projectPath;
    }

    @Override
    public BuildIdentifier getBuild() {
        return build;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public boolean matchesStrictly(ComponentIdentifier identifier) {
        assert identifier != null : "identifier cannot be null";

        if (identifier instanceof ProjectComponentIdentifier) {
            ProjectComponentIdentifier projectComponentIdentifier = (ProjectComponentIdentifier) identifier;
            return Objects.equal(build, projectComponentIdentifier.getBuild())
                && Objects.equal(projectPath, projectComponentIdentifier.getProjectPath());
        }

        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultProjectComponentSelector)) {
            return false;
        }
        DefaultProjectComponentSelector that = (DefaultProjectComponentSelector) o;
        return Objects.equal(build, that.build)
            && Objects.equal(projectPath, that.projectPath);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(build, projectPath);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public static ProjectComponentSelector newSelector(String projectPath) {
        return new DefaultProjectComponentSelector(new CurrentBuildIdentifier(), projectPath);
    }

    public static ProjectComponentSelector newSelector(Project project) {
        return newSelector(project.getPath());
    }

    public static ProjectComponentSelector newSelector(IncludedBuild build, String projectPath) {
        if (build == null) {
            return newSelector(projectPath);
        }
        return new DefaultProjectComponentSelector(new DefaultBuildIdentifier(build.getName()), projectPath);
    }

    public static ProjectComponentSelector newSelector(BuildIdentifier build, String projectPath) {
        return new DefaultProjectComponentSelector(build, projectPath);
    }

    public static ProjectComponentSelector newSelector(IncludedBuild build, ProjectComponentSelector selector) {
        return newSelector(build, selector.getProjectPath());
    }

    public static ProjectComponentSelector newSelector(ProjectComponentIdentifier projectId) {
        return new DefaultProjectComponentSelector(projectId.getBuild(), projectId.getProjectPath());
    }
}
