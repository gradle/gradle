/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugins.ide.internal.resolver.model;

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;

public class IdeProjectDependency extends IdeDependency {
    private final ProjectComponentIdentifier projectId;
    private final String projectName;

    public IdeProjectDependency(ProjectComponentIdentifier projectId, String projectName) {
        this.projectId = projectId;
        this.projectName = projectName;
    }

    public IdeProjectDependency(ProjectComponentIdentifier projectId) {
        this.projectId = projectId;
        this.projectName = determineProjectName(projectId);
    }

    public ProjectComponentIdentifier getProjectId() {
        return projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    private static String determineProjectName(ProjectComponentIdentifier projectId) {
        assert !projectId.getBuild().isCurrentBuild();
        String projectPath = projectId.getProjectPath();
        if (projectPath.equals(":")) {
            return projectId.getBuild().getName();
        }
        int index = projectPath.lastIndexOf(':');
        return projectPath.substring(index + 1);
    }
}
