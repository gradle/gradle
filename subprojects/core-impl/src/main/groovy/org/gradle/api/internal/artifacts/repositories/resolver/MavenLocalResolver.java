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
package org.gradle.api.internal.artifacts.repositories.resolver;

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionMetaData;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class MavenLocalResolver extends MavenResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenResolver.class);

    public MavenLocalResolver(String name, URI rootUri, RepositoryTransport transport,
                              LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder,
                              ModuleMetadataProcessor metadataProcessor
    ) {
        super(name, rootUri, transport, locallyAvailableResourceFinder, metadataProcessor);
    }

    protected void getDependency(DependencyDescriptor dd, BuildableModuleVersionMetaDataResolveResult result) {
        if (isSnapshotVersion(dd)) {
            getSnapshotDependency(dd, result);
        } else {
            resolveIfArtifactPresent(dd, result);
        }
    }

    protected void getSnapshotDependency(DependencyDescriptor dd, BuildableModuleVersionMetaDataResolveResult result) {
        final ModuleRevisionId dependencyRevisionId = dd.getDependencyRevisionId();
        final String uniqueSnapshotVersion = findUniqueSnapshotVersion(dependencyRevisionId);
        if (uniqueSnapshotVersion != null) {
            DependencyDescriptor enrichedDependencyDescriptor = enrichDependencyDescriptorWithSnapshotVersionInfo(dd, dependencyRevisionId, uniqueSnapshotVersion);
            resolveIfArtifactPresent(enrichedDependencyDescriptor, result);
            if (result.getState() == BuildableModuleVersionMetaDataResolveResult.State.Resolved) {
                result.setModuleSource(new TimestampedModuleSource(uniqueSnapshotVersion));
            }
        } else {
            resolveIfArtifactPresent(dd, result);
        }
    }

    protected void resolveIfArtifactPresent(DependencyDescriptor dependencyDescriptor, BuildableModuleVersionMetaDataResolveResult result) {
        ModuleRevisionId moduleRevisionId = dependencyDescriptor.getDependencyRevisionId();
        ResolvedArtifact ivyRef = findIvyFileRef(dependencyDescriptor);

        // get module descriptor
        if (ivyRef == null) {
            super.getDependency(dependencyDescriptor, result);
        } else {
            ModuleVersionMetaData metaData = getArtifactMetadata(ivyRef.getArtifact(), ivyRef.getResource());
            if (!metaData.isMetaDataOnly()) {
                DefaultModuleDescriptor generatedModuleDescriptor = DefaultModuleDescriptor.newDefaultInstance(moduleRevisionId, dependencyDescriptor.getAllDependencyArtifacts());
                ResolvedArtifact artifactRef = findAnyArtifact(generatedModuleDescriptor);
                if (artifactRef != null) {
                    super.getDependency(dependencyDescriptor, result);
                } else {
                    LOGGER.debug("Ivy file found for module '{}' in repository '{}' but no artifact found. Checking next repository.", moduleRevisionId, getName());
                }
            } else {
                super.getDependency(dependencyDescriptor, result);
            }
        }
    }
}
