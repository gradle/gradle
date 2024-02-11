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

import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.UrlBackedArtifactMetadata;
import org.gradle.internal.resolve.result.ResourceAwareResolveResult;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;

class MavenUniqueSnapshotExternalResourceArtifactResolver implements ExternalResourceArtifactResolver {
    private final ExternalResourceArtifactResolver delegate;
    private final MavenUniqueSnapshotModuleSource snapshot;

    public MavenUniqueSnapshotExternalResourceArtifactResolver(ExternalResourceArtifactResolver delegate, MavenUniqueSnapshotModuleSource snapshot) {
        this.delegate = delegate;
        this.snapshot = snapshot;
    }

    @Override
    public boolean artifactExists(ModuleComponentArtifactMetadata artifact, ResourceAwareResolveResult result) {
        if (artifact instanceof UrlBackedArtifactMetadata) {
            return delegate.artifactExists(artifact, result);
        } else {
            return delegate.artifactExists(timestamp(artifact), result);
        }
    }

    @Override
    public LocallyAvailableExternalResource resolveArtifact(ModuleComponentArtifactMetadata artifact, ResourceAwareResolveResult result) {
        if (artifact instanceof UrlBackedArtifactMetadata) {
            return delegate.resolveArtifact(artifact, result);
        } else {
            return delegate.resolveArtifact(timestamp(artifact), result);
        }
    }

    protected ModuleComponentArtifactMetadata timestamp(ModuleComponentArtifactMetadata artifact) {
        MavenUniqueSnapshotComponentIdentifier snapshotComponentIdentifier =
                new MavenUniqueSnapshotComponentIdentifier(artifact.getId().getComponentIdentifier(), snapshot.getTimestamp());
        return new DefaultModuleComponentArtifactMetadata(snapshotComponentIdentifier, artifact.getName());
    }
}
