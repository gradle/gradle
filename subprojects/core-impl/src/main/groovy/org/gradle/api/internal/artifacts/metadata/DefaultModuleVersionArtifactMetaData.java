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
import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultArtifactIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;

public class DefaultModuleVersionArtifactMetaData implements ModuleVersionArtifactMetaData {
    private final ModuleVersionIdentifier moduleVersionIdentifier;
    private final Artifact artifact;
    private final DefaultModuleVersionArtifactIdentifier id;

    public DefaultModuleVersionArtifactMetaData(Artifact artifact) {
        this(DefaultModuleVersionIdentifier.newId(artifact.getModuleRevisionId()), artifact);
    }

    public DefaultModuleVersionArtifactMetaData(ModuleVersionIdentifier moduleVersionIdentifier, Artifact artifact) {
        this.moduleVersionIdentifier = moduleVersionIdentifier;
        this.artifact = artifact;
        this.id = new DefaultModuleVersionArtifactIdentifier(moduleVersionIdentifier, artifact);
    }

    @Override
    public String toString() {
        return id.toString();
    }

    public Artifact getArtifact() {
        return artifact;
    }

    public ModuleVersionArtifactIdentifier getId() {
        return id;
    }

    public ModuleVersionIdentifier getModuleVersion() {
        return moduleVersionIdentifier;
    }

    public ArtifactIdentifier toArtifactIdentifier() {
        return new DefaultArtifactIdentifier(artifact);
    }

    public IvyArtifactName getName() {
        return id.getName();
    }
}
