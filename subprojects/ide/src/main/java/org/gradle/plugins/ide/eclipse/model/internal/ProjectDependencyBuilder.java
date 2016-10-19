/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model.internal;

import org.gradle.composite.internal.CompositeBuildIdeProjectResolver;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.plugins.ide.eclipse.model.ProjectDependency;
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency;

public class ProjectDependencyBuilder {
    private final CompositeBuildIdeProjectResolver ideProjectResolver;

    public ProjectDependencyBuilder(CompositeBuildIdeProjectResolver ideProjectResolver) {
        this.ideProjectResolver = ideProjectResolver;
    }

    public ProjectDependency build(IdeProjectDependency dependency) {
        return buildProjectDependency(determineTargetProjectPath(dependency));
    }

    private String determineTargetProjectPath(IdeProjectDependency dependency) {
        ComponentArtifactMetadata eclipseProjectArtifact = ideProjectResolver.findArtifact(dependency.getProjectId(), "eclipse.project");
        String targetProjectName = eclipseProjectArtifact == null ? dependency.getProjectName() : eclipseProjectArtifact.getName().getName();
        return "/" + targetProjectName;
    }

    private ProjectDependency buildProjectDependency(String path) {
        final ProjectDependency out = new ProjectDependency(path);
        out.setExported(false);
        return out;
    }
}
