/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.File;

public class PublishArtifactLocalArtifactMetadata implements LocalComponentArtifactMetadata, ComponentArtifactIdentifier {
    private final ComponentIdentifier componentIdentifier;
    private final PublishArtifact publishArtifact;

    public PublishArtifactLocalArtifactMetadata(ComponentIdentifier componentIdentifier, PublishArtifact publishArtifact) {
        this.componentIdentifier = componentIdentifier;
        this.publishArtifact = publishArtifact;
    }

    public String getDisplayName() {
        StringBuilder result = new StringBuilder();
        result.append(getName());
        result.append(" (")
              .append(componentIdentifier.getDisplayName())
              .append(")");
        return result.toString();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public ComponentIdentifier getComponentIdentifier() {
        return componentIdentifier;
    }

    @Override
    public File getFile() {
        return publishArtifact.getFile();
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
    public IvyArtifactName getName() {
        return DefaultIvyArtifactName.forPublishArtifact(publishArtifact);
    }

    @Override
    public int hashCode() {
        return componentIdentifier.hashCode() ^ publishArtifact.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        PublishArtifactLocalArtifactMetadata other = (PublishArtifactLocalArtifactMetadata) obj;
        return other.componentIdentifier.equals(componentIdentifier) && other.publishArtifact.equals(publishArtifact);
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return publishArtifact.getBuildDependencies();
    }
}
