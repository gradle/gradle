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

package org.gradle.internal.component.model;

import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;

public class DefaultModuleDescriptorArtifactMetadata implements ModuleDescriptorArtifactMetadata {
    private final DefaultModuleComponentArtifactMetadata delegate;

    public DefaultModuleDescriptorArtifactMetadata(DefaultModuleComponentArtifactMetadata delegate) {
        this.delegate = delegate;
    }

    @Override
    public ModuleComponentArtifactIdentifier getId() {
        return delegate.getId();
    }

    @Override
    public ArtifactIdentifier toArtifactIdentifier() {
        return delegate.toArtifactIdentifier();
    }

    @Override
    public ComponentIdentifier getComponentId() {
        return delegate.getComponentId();
    }

    @Override
    public IvyArtifactName getName() {
        return delegate.getName();
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return delegate.getBuildDependencies();
    }
}
