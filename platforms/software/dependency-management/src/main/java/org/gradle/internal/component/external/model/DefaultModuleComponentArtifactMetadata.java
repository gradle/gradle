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

package org.gradle.internal.component.external.model;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import javax.annotation.Nullable;
import java.util.Optional;

public class DefaultModuleComponentArtifactMetadata implements ModuleComponentArtifactMetadata {
    private final DefaultModuleComponentArtifactIdentifier id;
    private final ComponentArtifactMetadata alternativeArtifact;

    public DefaultModuleComponentArtifactMetadata(ModuleComponentIdentifier componentIdentifier, IvyArtifactName artifact) {
        this(componentIdentifier, artifact, null);
    }

    public DefaultModuleComponentArtifactMetadata(ModuleComponentIdentifier componentIdentifier, IvyArtifactName artifact, @Nullable ComponentArtifactMetadata alternativeArtifact) {
        this(new DefaultModuleComponentArtifactIdentifier(componentIdentifier, artifact), alternativeArtifact);
    }

    public DefaultModuleComponentArtifactMetadata(ModuleComponentArtifactIdentifier moduleComponentArtifactIdentifier) {
        this(moduleComponentArtifactIdentifier, null);
    }

    public DefaultModuleComponentArtifactMetadata(ModuleComponentArtifactIdentifier moduleComponentArtifactIdentifier, @Nullable ComponentArtifactMetadata alternativeArtifact) {
        this.id = (DefaultModuleComponentArtifactIdentifier) moduleComponentArtifactIdentifier;
        this.alternativeArtifact = alternativeArtifact;
    }

    @Override
    public String toString() {
        return id.toString();
    }

    @Override
    public ModuleComponentArtifactIdentifier getId() {
        return id;
    }

    @Override
    public ComponentIdentifier getComponentId() {
        return id.getComponentIdentifier();
    }

    @Override
    public IvyArtifactName getName() {
        return id.getName();
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return TaskDependencyInternal.EMPTY;
    }

    @Override
    public Optional<ComponentArtifactMetadata> getAlternativeArtifact() {
        return Optional.ofNullable(alternativeArtifact);
    }
}
