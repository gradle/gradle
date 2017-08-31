/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.composite.internal;

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.File;
import java.util.Set;

class CompositeProjectComponentArtifactMetadata implements LocalComponentArtifactMetadata, ComponentArtifactIdentifier, DisplayName {
    private final ProjectComponentIdentifier componentIdentifier;
    private final IvyArtifactName ivyArtifactName;
    private final File artifactFile;
    private final Set<String> tasks;

    public CompositeProjectComponentArtifactMetadata(ProjectComponentIdentifier componentIdentifier, IvyArtifactName ivyArtifactName, File artifactFile, Set<String> tasks) {
        this.componentIdentifier = componentIdentifier;
        this.ivyArtifactName = ivyArtifactName;
        this.artifactFile = artifactFile;
        this.tasks = tasks;
    }

    @Override
    public ProjectComponentIdentifier getComponentId() {
        return componentIdentifier;
    }

    @Override
    public ComponentArtifactIdentifier getId() {
        return this;
    }

    @Override
    public IvyArtifactName getName() {
        return ivyArtifactName;
    }

    @Override
    public ProjectComponentIdentifier getComponentIdentifier() {
        return componentIdentifier;
    }

    @Override
    public String getDisplayName() {
        StringBuilder builder = new StringBuilder();
        builder.append(ivyArtifactName.toString());
        builder.append(" (");
        builder.append(componentIdentifier.toString());
        builder.append(")");
        return builder.toString();
    }

    @Override
    public String getCapitalizedDisplayName() {
        return getDisplayName();
    }

    @Override
    public File getFile() {
        return artifactFile;
    }

    public Set<String> getTasks() {
        return tasks;
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return new AbstractTaskDependency() {
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                for (String task : tasks) {
                    context.add(new IncludedBuildTaskReference(componentIdentifier.getBuild().getName(), task));
                }
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CompositeProjectComponentArtifactMetadata)) {
            return false;
        }

        CompositeProjectComponentArtifactMetadata that = (CompositeProjectComponentArtifactMetadata) o;

        return artifactFile.equals(that.artifactFile);

    }

    @Override
    public int hashCode() {
        return artifactFile.hashCode();
    }
}
