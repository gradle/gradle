/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultArtifactIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.dsl.ArtifactFile;
import org.gradle.api.internal.tasks.TaskDependencies;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;

/**
 * An artifact located relative to some module.
 */
public class UrlBackedArtifactMetadata implements ModuleComponentArtifactMetadata {
    private final ModuleComponentIdentifier componentIdentifier;
    private final String relativeUrl;
    private final ModuleComponentFileArtifactIdentifier id;

    public UrlBackedArtifactMetadata(ModuleComponentIdentifier componentIdentifier, String fileName, String relativeUrl) {
        this.componentIdentifier = componentIdentifier;
        this.relativeUrl = relativeUrl;
        id = new ModuleComponentFileArtifactIdentifier(componentIdentifier, fileName);
    }

    @Override
    public ModuleComponentIdentifier getComponentId() {
        return componentIdentifier;
    }

    @Override
    public ModuleComponentArtifactIdentifier getId() {
        return id;
    }

    public String getRelativeUrl() {
        return relativeUrl;
    }

    @Override
    public ArtifactIdentifier toArtifactIdentifier() {
        ArtifactFile names = new ArtifactFile(relativeUrl, componentIdentifier.getVersion());
        return new DefaultArtifactIdentifier(DefaultModuleVersionIdentifier.newId(componentIdentifier), names.getName(), names.getExtension(), names.getExtension(), names.getClassifier());
    }

    @Override
    public IvyArtifactName getName() {
        ArtifactFile names = new ArtifactFile(relativeUrl, componentIdentifier.getVersion());
        return new DefaultIvyArtifactName(names.getName(), names.getExtension(), names.getExtension(), names.getClassifier());
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return TaskDependencies.EMPTY;
    }
}
