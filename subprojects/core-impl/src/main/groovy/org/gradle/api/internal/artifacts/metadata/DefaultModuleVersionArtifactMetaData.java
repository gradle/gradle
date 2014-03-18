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

public class DefaultModuleVersionArtifactMetaData implements ModuleVersionArtifactMetaData {
    private final DefaultModuleVersionArtifactIdentifier id;

    public DefaultModuleVersionArtifactMetaData(ModuleVersionIdentifier moduleVersionIdentifier, Artifact artifact) {
        this.id = new DefaultModuleVersionArtifactIdentifier(moduleVersionIdentifier, artifact);
    }

    public DefaultModuleVersionArtifactMetaData(ModuleVersionArtifactIdentifier moduleVersionArtifactIdentifier) {
        this.id = (DefaultModuleVersionArtifactIdentifier) moduleVersionArtifactIdentifier;
    }

    @Override
    public String toString() {
        return id.toString();
    }

    public ModuleVersionArtifactIdentifier getId() {
        return id;
    }

    public ModuleVersionIdentifier getModuleVersion() {
        return id.getModuleVersionIdentifier();
    }

    public ArtifactIdentifier toArtifactIdentifier() {
        return new DefaultArtifactIdentifier(id);
    }

    public IvyArtifactName getName() {
        return id.getName();
    }
}
