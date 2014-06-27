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

package org.gradle.api.internal.artifacts.metadata;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultModuleVersionPublishMetaData implements BuildableModuleVersionPublishMetaData {
    private final ModuleVersionIdentifier id;
    private final Map<ModuleVersionArtifactIdentifier, ModuleVersionArtifactPublishMetaData> artifactsById = new LinkedHashMap<ModuleVersionArtifactIdentifier, ModuleVersionArtifactPublishMetaData>();

    public DefaultModuleVersionPublishMetaData(ModuleVersionIdentifier id) {
        this.id = id;
    }

    public ModuleVersionIdentifier getId() {
        return id;
    }

    public void addArtifact(Artifact artifact, File file) {
        DefaultModuleVersionArtifactPublishMetaData publishMetaData = new DefaultModuleVersionArtifactPublishMetaData(id, artifact, file);
        artifactsById.put(publishMetaData.getId(), publishMetaData);
    }

    public void addArtifact(ModuleVersionArtifactPublishMetaData artifact) {
        artifactsById.put(artifact.getId(), artifact);
    }

    public Collection<ModuleVersionArtifactPublishMetaData> getArtifacts() {
        return artifactsById.values();
    }

    public ModuleVersionArtifactPublishMetaData getArtifact(ModuleVersionArtifactIdentifier artifactIdentifier) {
        return artifactsById.get(artifactIdentifier);
    }

    private static class DefaultModuleVersionArtifactPublishMetaData implements ModuleVersionArtifactPublishMetaData {
        private final DefaultModuleVersionArtifactIdentifier id;
        private final Artifact artifact;
        private final File file;

        private DefaultModuleVersionArtifactPublishMetaData(ModuleVersionIdentifier moduleVersionIdentifier, Artifact artifact, File file) {
            this.id = new DefaultModuleVersionArtifactIdentifier(DefaultModuleComponentIdentifier.newId(moduleVersionIdentifier), artifact);
            this.artifact = artifact;
            this.file = file;
        }

        public IvyArtifactName getArtifactName() {
            return id.getName();
        }

        public Artifact toIvyArtifact() {
            return artifact;
        }

        public ModuleVersionArtifactIdentifier getId() {
            return id;
        }

        public File getFile() {
            return file;
        }
    }
}
