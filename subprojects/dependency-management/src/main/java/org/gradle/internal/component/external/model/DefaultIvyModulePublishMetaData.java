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

import org.apache.ivy.core.module.descriptor.Artifact;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultIvyModulePublishMetaData implements BuildableIvyModulePublishMetaData {
    private final ModuleVersionIdentifier id;
    private final Map<ModuleComponentArtifactIdentifier, IvyModuleArtifactPublishMetaData> artifactsById = new LinkedHashMap<ModuleComponentArtifactIdentifier, IvyModuleArtifactPublishMetaData>();

    public DefaultIvyModulePublishMetaData(ModuleVersionIdentifier id) {
        this.id = id;
    }

    public ModuleVersionIdentifier getId() {
        return id;
    }

    public void addArtifact(Artifact artifact, File file) {
        DefaultIvyModuleArtifactPublishMetaData publishMetaData = new DefaultIvyModuleArtifactPublishMetaData(id, artifact, file);
        artifactsById.put(publishMetaData.getId(), publishMetaData);
    }

    public void addArtifact(IvyModuleArtifactPublishMetaData artifact) {
        artifactsById.put(artifact.getId(), artifact);
    }

    public Collection<IvyModuleArtifactPublishMetaData> getArtifacts() {
        return artifactsById.values();
    }

    public IvyModuleArtifactPublishMetaData getArtifact(ModuleComponentArtifactIdentifier artifactIdentifier) {
        return artifactsById.get(artifactIdentifier);
    }

    private static class DefaultIvyModuleArtifactPublishMetaData implements IvyModuleArtifactPublishMetaData {
        private final DefaultModuleComponentArtifactIdentifier id;
        private final Artifact artifact;
        private final File file;

        private DefaultIvyModuleArtifactPublishMetaData(ModuleVersionIdentifier moduleVersionIdentifier, Artifact artifact, File file) {
            this.id = new DefaultModuleComponentArtifactIdentifier(DefaultModuleComponentIdentifier.newId(moduleVersionIdentifier), artifact);
            this.artifact = artifact;
            this.file = file;
        }

        public IvyArtifactName getArtifactName() {
            return id.getName();
        }

        public Artifact toIvyArtifact() {
            return artifact;
        }

        public ModuleComponentArtifactIdentifier getId() {
            return id;
        }

        public File getFile() {
            return file;
        }
    }
}
