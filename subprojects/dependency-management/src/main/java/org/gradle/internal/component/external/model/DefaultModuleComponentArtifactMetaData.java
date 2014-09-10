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
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.internal.component.model.IvyArtifactName;

public class DefaultModuleComponentArtifactMetaData implements ModuleComponentArtifactMetaData {
    private final DefaultModuleComponentArtifactIdentifier id;

    public DefaultModuleComponentArtifactMetaData(ModuleComponentIdentifier componentIdentifier, Artifact artifact) {
        this(new DefaultModuleComponentArtifactIdentifier(componentIdentifier, artifact));
    }

    public DefaultModuleComponentArtifactMetaData(ModuleComponentIdentifier componentIdentifier, IvyArtifactName artifact) {
        this(new DefaultModuleComponentArtifactIdentifier(componentIdentifier, artifact));
    }

    public DefaultModuleComponentArtifactMetaData(ModuleComponentArtifactIdentifier moduleComponentArtifactIdentifier) {
        this.id = (DefaultModuleComponentArtifactIdentifier) moduleComponentArtifactIdentifier;
    }

    @Override
    public String toString() {
        return id.toString();
    }

    public ModuleComponentArtifactIdentifier getId() {
        return id;
    }

    public ComponentIdentifier getComponentId() {
        return id.getComponentIdentifier();
    }

    public ArtifactIdentifier toArtifactIdentifier() {
        return new DefaultArtifactIdentifier(id);
    }

    public Artifact toIvyArtifact() {
        IvyArtifactName ivyArtifactName = id.getName();
        return new DefaultArtifact(IvyUtil.createModuleRevisionId(id.getComponentIdentifier()), null, ivyArtifactName.getName(), ivyArtifactName.getType(), ivyArtifactName.getExtension(), ivyArtifactName.getAttributes());
    }

    public IvyArtifactName getName() {
        return id.getName();
    }
}
