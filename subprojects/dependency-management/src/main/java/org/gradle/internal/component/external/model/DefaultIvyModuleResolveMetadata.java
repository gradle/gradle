/*
 * Copyright 2014 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.model.ModuleSource;

import java.util.Map;

public class DefaultIvyModuleResolveMetadata extends AbstractModuleComponentResolveMetadata implements IvyModuleResolveMetadata {
    private final ImmutableList<Artifact> artifacts;

    DefaultIvyModuleResolveMetadata(MutableIvyModuleResolveMetadata metadata) {
        super(metadata, metadata.getArtifactDefinitions());
        this.artifacts = metadata.getArtifactDefinitions();
    }

    private DefaultIvyModuleResolveMetadata(DefaultIvyModuleResolveMetadata metadata, ModuleSource source) {
        super(metadata, source);
        this.artifacts = metadata.artifacts;
    }

    @Override
    public DefaultIvyModuleResolveMetadata withSource(ModuleSource source) {
        return new DefaultIvyModuleResolveMetadata(this, source);
    }

    @Override
    public MutableIvyModuleResolveMetadata asMutable() {
        return new DefaultMutableIvyModuleResolveMetadata(this);
    }

    @Override
    public ImmutableList<Artifact> getArtifactDefinitions() {
        return artifacts;
    }

    public String getBranch() {
        return getDescriptor().getBranch();
    }

    public Map<NamespaceId, String> getExtraInfo() {
        return getDescriptor().getExtraInfo();
    }
}
