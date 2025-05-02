/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts;

import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.hash.HashCode;

import java.util.Set;

class DefaultCachedArtifacts implements CachedArtifacts {
    private final Set<ComponentArtifactMetadata> artifacts;
    private final HashCode descriptorHash;
    private final long ageMillis;

    DefaultCachedArtifacts(Set<ComponentArtifactMetadata> artifacts, HashCode descriptorHash, long ageMillis) {
        this.ageMillis = ageMillis;
        this.artifacts = artifacts;
        this.descriptorHash = descriptorHash;
    }

    @Override
    public Set<ComponentArtifactMetadata> getArtifacts() {
        return artifacts;
    }

    @Override
    public HashCode getDescriptorHash() {
        return descriptorHash;
    }

    @Override
    public long getAgeMillis() {
        return ageMillis;
    }
}
