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
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultLocalComponentMetaData implements MutableLocalComponentMetaData {
    private final Map<ModuleVersionArtifactIdentifier, DefaultLocalArtifactMetaData> artifactsById = new LinkedHashMap<ModuleVersionArtifactIdentifier, DefaultLocalArtifactMetaData>();
    private final DefaultModuleDescriptor moduleDescriptor;
    private final ModuleVersionIdentifier id;

    public DefaultLocalComponentMetaData(DefaultModuleDescriptor moduleDescriptor) {
        this.moduleDescriptor = moduleDescriptor;
        id = DefaultModuleVersionIdentifier.newId(moduleDescriptor.getModuleRevisionId());
    }

    public ModuleVersionIdentifier getId() {
        return id;
    }

    public DefaultModuleDescriptor getModuleDescriptor() {
        return moduleDescriptor;
    }

    public void addArtifact(Artifact artifact, File file) {
        DefaultLocalArtifactMetaData publishMetaData = new DefaultLocalArtifactMetaData(id, artifact, file);
        artifactsById.put(publishMetaData.getId(), publishMetaData);
    }

    public Collection<? extends LocalArtifactMetaData> getArtifacts() {
        return artifactsById.values();
    }

    public LocalArtifactMetaData getArtifact(ModuleVersionArtifactIdentifier artifactIdentifier) {
        return artifactsById.get(artifactIdentifier);
    }

    public ModuleVersionMetaData toResolveMetaData() {
        // TODO:ADAM - need to clone the descriptor
        return new ModuleDescriptorAdapter(id, moduleDescriptor);
    }

    public BuildableModuleVersionPublishMetaData toPublishMetaData() {
        DefaultModuleVersionPublishMetaData publishMetaData = new DefaultModuleVersionPublishMetaData(id);
        for (DefaultLocalArtifactMetaData artifact : artifactsById.values()) {
            publishMetaData.addArtifact(artifact.artifact, artifact.file);
        }
        return publishMetaData;
    }

    private static class DefaultLocalArtifactMetaData implements LocalArtifactMetaData {
        private final ModuleVersionArtifactIdentifier id;
        private final Artifact artifact;
        private final File file;

        private DefaultLocalArtifactMetaData(ModuleVersionIdentifier moduleVersionIdentifier, Artifact artifact, File file) {
            this.id = new DefaultModuleVersionArtifactIdentifier(moduleVersionIdentifier, artifact);
            this.artifact = artifact;
            this.file = file;
        }

        public ModuleVersionArtifactIdentifier getId() {
            return id;
        }

        public File getFile() {
            return file;
        }
    }
}
