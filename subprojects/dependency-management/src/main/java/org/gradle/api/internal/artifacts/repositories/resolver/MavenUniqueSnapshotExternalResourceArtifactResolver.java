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

import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetaData;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetaData;
import org.gradle.internal.resolve.result.ResourceAwareResolveResult;
import org.gradle.internal.resource.LocallyAvailableExternalResource;

class MavenUniqueSnapshotExternalResourceArtifactResolver implements ExternalResourceArtifactResolver {
    private final ExternalResourceArtifactResolver delegate;
    private final String timestamp;

    public MavenUniqueSnapshotExternalResourceArtifactResolver(ExternalResourceArtifactResolver delegate, String timestamp) {
        this.delegate = delegate;
        this.timestamp = timestamp;
    }

    public boolean artifactExists(ModuleComponentArtifactMetaData artifact, ResourceAwareResolveResult result) {
        return delegate.artifactExists(timestamp(artifact), result);
    }

    public LocallyAvailableExternalResource resolveArtifact(ModuleComponentArtifactMetaData artifact, ResourceAwareResolveResult result) {
        return delegate.resolveArtifact(timestamp(artifact), result);
    }

    public LocallyAvailableExternalResource resolveMetaDataArtifact(ModuleComponentArtifactMetaData artifact, ResourceAwareResolveResult result) {
        return delegate.resolveMetaDataArtifact(timestamp(artifact), result);
    }

    protected ModuleComponentArtifactMetaData timestamp(ModuleComponentArtifactMetaData artifact) {
        MavenUniqueSnapshotComponentIdentifier snapshotComponentIdentifier =
                new MavenUniqueSnapshotComponentIdentifier(artifact.getId().getComponentIdentifier(), timestamp);
        return new DefaultModuleComponentArtifactMetaData(snapshotComponentIdentifier, artifact.getName());
    }
}
