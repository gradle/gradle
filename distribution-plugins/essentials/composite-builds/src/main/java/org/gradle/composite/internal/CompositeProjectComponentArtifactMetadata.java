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
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.File;

class CompositeProjectComponentArtifactMetadata implements LocalComponentArtifactMetadata, ComponentArtifactIdentifier, DisplayName {
    private final ProjectComponentIdentifier componentIdentifier;
    private final LocalComponentArtifactMetadata delegate;
    private final File file;

    public CompositeProjectComponentArtifactMetadata(ProjectComponentIdentifier componentIdentifier, LocalComponentArtifactMetadata delegate, File file) {
        this.componentIdentifier = componentIdentifier;
        this.delegate = delegate;
        this.file = file;
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
        return delegate.getName();
    }

    @Override
    public ProjectComponentIdentifier getComponentIdentifier() {
        return componentIdentifier;
    }

    @Override
    public String getDisplayName() {
        return delegate.getId().getDisplayName();
    }

    @Override
    public String getCapitalizedDisplayName() {
        return Describables.of(delegate.getId()).getCapitalizedDisplayName();
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return delegate.getBuildDependencies();
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
        return delegate.equals(that.delegate);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
}
