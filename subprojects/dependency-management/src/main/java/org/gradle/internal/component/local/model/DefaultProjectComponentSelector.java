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
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.initialization.BuildIdentity;

public class DefaultProjectComponentSelector implements ProjectComponentSelector {
    private final String projectPath;
    private final String displayName;
    private final BuildIdentifier buildIdentifier;

    public DefaultProjectComponentSelector(BuildIdentifier buildIdentifier, String projectPath) {
        assert buildIdentifier != null : "build cannot be null";
        assert projectPath != null : "project path cannot be null";
        this.buildIdentifier = buildIdentifier;
        this.projectPath = projectPath;
        this.displayName = createDisplayName(buildIdentifier.getName(), projectPath);
    }

    private static String createDisplayName(String buildName, String projectPath) {
        if (":".equals(buildName)) {
            return "project " + projectPath;
        }
        return "project :" + buildName + projectPath;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BuildIdentifier getBuildIdentifier() {
        return buildIdentifier;
    }

    @Override
    public String getBuildName() {
        return buildIdentifier.getName();
    }

    public String getProjectPath() {
        return projectPath;
    }

    public boolean matchesStrictly(ComponentIdentifier identifier) {
        assert identifier != null : "identifier cannot be null";

        if (identifier instanceof DefaultProjectComponentIdentifier) {
            DefaultProjectComponentIdentifier projectComponentIdentifier = (DefaultProjectComponentIdentifier) identifier;
            return Objects.equal(buildIdentifier, projectComponentIdentifier.getBuild())
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
        return Objects.equal(buildIdentifier, that.buildIdentifier)
            && Objects.equal(projectPath, that.projectPath);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(buildIdentifier, projectPath);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public static ProjectComponentSelector newSelector(Project project) {
        BuildIdentifier buildId = ((ProjectInternal) project).getServices().get(BuildIdentity.class).getCurrentBuild();
        return new DefaultProjectComponentSelector(buildId, project.getPath());
    }

    public static ProjectComponentSelector newSelector(BuildIdentifier build, String projectPath) {
        return new DefaultProjectComponentSelector(build, projectPath);
    }

    public static ProjectComponentSelector newSelector(ProjectComponentIdentifier projectId) {
        return new DefaultProjectComponentSelector(projectId.getBuild(), projectId.getProjectPath());
    }
}
