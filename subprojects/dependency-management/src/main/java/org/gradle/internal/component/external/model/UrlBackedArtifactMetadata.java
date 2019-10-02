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
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;

/**
 * An artifact located relative to some module.
 */
public class UrlBackedArtifactMetadata implements ModuleComponentArtifactMetadata {
    private final ModuleComponentIdentifier componentIdentifier;
    private final String fileName;
    private final String relativeUrl;
    private final ModuleComponentArtifactIdentifier id;

    private IvyArtifactName ivyArtifactName;

    public UrlBackedArtifactMetadata(ModuleComponentIdentifier componentIdentifier, String fileName, String relativeUrl) {
        this.componentIdentifier = componentIdentifier;
        this.fileName = fileName;
        this.relativeUrl = relativeUrl;
        id = createArtifactId(componentIdentifier, fileName);
    }

    private ModuleComponentArtifactIdentifier createArtifactId(ModuleComponentIdentifier componentIdentifier, String fileName) {
        if (componentIdentifier instanceof MavenUniqueSnapshotComponentIdentifier) {
            // This special case is for Maven snapshots with Gradle Module Metadata when we need to remap the file name, which
            // corresponds to the unique timestamp, to the SNAPSHOT version, for backwards compatibility
            return new DefaultModuleComponentArtifactIdentifier(
                componentIdentifier,
                getName()
            );
        }
        return new ModuleComponentFileArtifactIdentifier(componentIdentifier, fileName);
    }

    @Override
    public ModuleComponentIdentifier getComponentId() {
        return componentIdentifier;
    }

    @Override
    public ModuleComponentArtifactIdentifier getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
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
        if (ivyArtifactName == null) {
            ArtifactFile names = new ArtifactFile(relativeUrl, uniqueVersion());
            ivyArtifactName = new DefaultIvyArtifactName(names.getName(), names.getExtension(), names.getExtension(), names.getClassifier());
        }
        return ivyArtifactName;
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return TaskDependencyInternal.EMPTY;
    }

    private String uniqueVersion() {
        if (componentIdentifier instanceof MavenUniqueSnapshotComponentIdentifier) {
            return ((MavenUniqueSnapshotComponentIdentifier) componentIdentifier).getTimestampedVersion();
        }
        return componentIdentifier.getVersion();
    }
}
