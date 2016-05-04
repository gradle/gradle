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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.internal.component.local.model.LocalComponentArtifactIdentifier;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.File;
import java.util.Set;

public class CompositeProjectComponentArtifactMetaData implements ComponentArtifactMetaData, LocalComponentArtifactIdentifier {
    private final ProjectComponentIdentifier componentIdentifier;
    private final IvyArtifactName ivyArtifactName;
    private final File artifactFile;
    private final File rootDirectory;
    private final Set<String> tasks;

    public CompositeProjectComponentArtifactMetaData(ProjectComponentIdentifier componentIdentifier, IvyArtifactName ivyArtifactName, File artifactFile, File rootDirectory, Set<String> tasks) {
        this.componentIdentifier = componentIdentifier;
        this.ivyArtifactName = ivyArtifactName;
        this.artifactFile = artifactFile;
        this.rootDirectory = rootDirectory;
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
    public File getFile() {
        return artifactFile;
    }

    public File getRootDirectory() {
        return rootDirectory;
    }

    public Set<String> getTasks() {
        return tasks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CompositeProjectComponentArtifactMetaData)) {
            return false;
        }

        CompositeProjectComponentArtifactMetaData that = (CompositeProjectComponentArtifactMetaData) o;

        return artifactFile.equals(that.artifactFile);

    }

    @Override
    public int hashCode() {
        return artifactFile.hashCode();
    }
}
