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

import org.gradle.api.internal.artifacts.metadata.DefaultModuleVersionArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.externalresource.LocallyAvailableExternalResource;

class MavenUniqueSnapshotExternalResourceArtifactResolver implements ExternalResourceArtifactResolver {
    private final ExternalResourceArtifactResolver delegate;
    private final String timestamp;

    public MavenUniqueSnapshotExternalResourceArtifactResolver(ExternalResourceArtifactResolver delegate, String timestamp) {
        this.delegate = delegate;
        this.timestamp = timestamp;
    }

    public boolean artifactExists(ModuleVersionArtifactMetaData artifact) {
        return delegate.artifactExists(timestamp(artifact));
    }

    public LocallyAvailableExternalResource resolveArtifact(ModuleVersionArtifactMetaData artifact) {
        return delegate.resolveArtifact(timestamp(artifact));
    }

    public LocallyAvailableExternalResource resolveMetaDataArtifact(ModuleVersionArtifactMetaData artifact) {
        return delegate.resolveMetaDataArtifact(timestamp(artifact));
    }

    protected ModuleVersionArtifactMetaData timestamp(ModuleVersionArtifactMetaData artifact) {
        MavenUniqueSnapshotComponentIdentifier snapshotComponentIdentifier =
                new MavenUniqueSnapshotComponentIdentifier(artifact.getId().getComponentIdentifier(), timestamp);
        return new DefaultModuleVersionArtifactMetaData(snapshotComponentIdentifier, artifact.getName());
    }
}
