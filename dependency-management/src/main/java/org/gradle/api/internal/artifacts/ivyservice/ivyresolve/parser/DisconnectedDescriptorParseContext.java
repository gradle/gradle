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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;

/**
 * An implementation of {@link DescriptorParseContext} that is useful for parsing an ivy.xml file without attempting to download
 * other resources from a DependencyResolver.
 */
public class DisconnectedDescriptorParseContext implements DescriptorParseContext {
    @Override
    public LocallyAvailableExternalResource getMetaDataArtifact(ModuleComponentIdentifier moduleComponentIdentifier, ArtifactType artifactType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public LocallyAvailableExternalResource getMetaDataArtifact(ModuleDependencyMetadata dependencyMetadata, VersionSelector acceptor, ArtifactType artifactType) {
        throw new UnsupportedOperationException();
    }
}
