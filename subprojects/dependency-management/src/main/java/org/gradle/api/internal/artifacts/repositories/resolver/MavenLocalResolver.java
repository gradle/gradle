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

import org.gradle.api.Nullable;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.model.MavenModuleResolveMetaData;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetaData;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData;
import org.gradle.internal.resolve.result.DefaultResourceAwareResolveResult;
import org.gradle.internal.resolve.result.ResourceAwareResolveResult;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class MavenLocalResolver extends MavenResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenResolver.class);

    public MavenLocalResolver(String name, URI rootUri, RepositoryTransport transport,
                              LocallyAvailableResourceFinder<ModuleComponentArtifactMetaData> locallyAvailableResourceFinder,
                              FileStore<ModuleComponentArtifactMetaData> artifactFileStore) {
        super(name, rootUri, transport, locallyAvailableResourceFinder, artifactFileStore);
    }

    @Override
    @Nullable
    protected MutableModuleComponentResolveMetaData parseMetaDataFromArtifact(ModuleComponentIdentifier moduleComponentIdentifier, ExternalResourceArtifactResolver artifactResolver, ResourceAwareResolveResult result) {
        MutableModuleComponentResolveMetaData metaData = super.parseMetaDataFromArtifact(moduleComponentIdentifier, artifactResolver, result);
        if (metaData == null) {
            return null;
        }

        if (isOrphanedPom(mavenMetaData(metaData), artifactResolver)) {
            return null;
        }
        return metaData;
    }

    private boolean isOrphanedPom(MavenModuleResolveMetaData metaData, ExternalResourceArtifactResolver artifactResolver) {
        if (metaData.isPomPackaging()) {
            return false;
        }

        // check custom packaging
        ModuleComponentArtifactMetaData artifact;
        if (metaData.isKnownJarPackaging()) {
            artifact = metaData.artifact("jar", "jar", null);
        } else {
            artifact = metaData.artifact(metaData.getPackaging(), metaData.getPackaging(), null);
        }

        if (artifactResolver.artifactExists(artifact, new DefaultResourceAwareResolveResult())) {
            return false;
        }

        LOGGER.debug("POM file found for module '{}' in repository '{}' but no artifact found. Ignoring.", metaData.getId(), getName());
        return true;
    }
}
