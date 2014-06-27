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

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResourceAwareResolveResult;
import org.gradle.api.internal.artifacts.metadata.DefaultModuleVersionArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.internal.resource.LocallyAvailableExternalResource;

class MavenUniqueSnapshotExternalResourceArtifactResolver implements ExternalResourceArtifactResolver {
    private final ExternalResourceArtifactResolver delegate;
    private final String timestamp;

    public MavenUniqueSnapshotExternalResourceArtifactResolver(ExternalResourceArtifactResolver delegate, String timestamp) {
        this.delegate = delegate;
        this.timestamp = timestamp;
    }

    public boolean artifactExists(ModuleVersionArtifactMetaData artifact, ResourceAwareResolveResult result) {
        return delegate.artifactExists(timestamp(artifact), result);
    }

    public LocallyAvailableExternalResource resolveArtifact(ModuleVersionArtifactMetaData artifact, ResourceAwareResolveResult result) {
        return delegate.resolveArtifact(timestamp(artifact), result);
    }

    public LocallyAvailableExternalResource resolveMetaDataArtifact(ModuleVersionArtifactMetaData artifact, ResourceAwareResolveResult result) {
        return delegate.resolveMetaDataArtifact(timestamp(artifact), result);
    }

    protected ModuleVersionArtifactMetaData timestamp(ModuleVersionArtifactMetaData artifact) {
        MavenUniqueSnapshotComponentIdentifier snapshotComponentIdentifier =
                new MavenUniqueSnapshotComponentIdentifier(artifact.getId().getComponentIdentifier(), timestamp);
        return new DefaultModuleVersionArtifactMetaData(snapshotComponentIdentifier, artifact.getName());
    }
}
