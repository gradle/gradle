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

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.File;

/**
 * Represents an unknown local artifact, referenced from a dependency definition.
 */
public class MissingLocalArtifactMetadata implements LocalComponentArtifactMetadata, ComponentArtifactIdentifier, DisplayName {
    private final ComponentIdentifier componentIdentifier;
    private final IvyArtifactName name;

    public MissingLocalArtifactMetadata(ComponentIdentifier componentIdentifier, IvyArtifactName artifactName) {
        this.componentIdentifier = componentIdentifier;
        this.name = artifactName;
    }

    @Override
    public String getDisplayName() {
        return name + " (" + componentIdentifier.getDisplayName()+ ")";
    }

    @Override
    public String getCapitalizedDisplayName() {
        return getDisplayName();
    }

    @Override
    public File getFile() {
        return null;
    }

    @Override
    public IvyArtifactName getName() {
        return name;
    }

    @Override
    public ComponentIdentifier getComponentIdentifier() {
        return componentIdentifier;
    }

    @Override
    public ComponentArtifactIdentifier getId() {
        return this;
    }

    @Override
    public ComponentIdentifier getComponentId() {
        return componentIdentifier;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return TaskDependencyInternal.EMPTY;
    }

    @Override
    public int hashCode() {
        return componentIdentifier.hashCode() ^ name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        MissingLocalArtifactMetadata other = (MissingLocalArtifactMetadata) obj;
        return other.componentIdentifier.equals(componentIdentifier) && other.name.equals(name);
    }
}
