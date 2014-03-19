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
package org.gradle.api.internal.artifacts.ivyservice.modulecache;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionRepository;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactIdentifier;

import java.math.BigInteger;
import java.util.Set;

public interface ModuleArtifactsCache {
    CachedArtifacts cacheArtifacts(ModuleVersionRepository repository, ModuleVersionIdentifier moduleMetaDataId, String context, BigInteger descriptorHash, Set<ModuleVersionArtifactIdentifier> artifacts);

    CachedArtifacts getCachedArtifacts(ModuleVersionRepository delegate, ModuleVersionIdentifier moduleMetaDataId, String context);

    interface CachedArtifacts {
        Set<ModuleVersionArtifactIdentifier> getArtifacts();

        BigInteger getDescriptorHash();

        long getAgeMillis();
    }
}
