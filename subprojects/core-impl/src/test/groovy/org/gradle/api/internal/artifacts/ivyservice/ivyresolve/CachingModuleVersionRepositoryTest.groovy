/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleVersionResolver
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetaDataCache
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactIdentifier
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData
import org.gradle.api.internal.externalresource.cached.CachedArtifactIndex
import org.gradle.api.internal.externalresource.ivy.ArtifactAtRepositoryKey
import org.gradle.api.internal.externalresource.metadata.DefaultExternalResourceMetaData
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData
import org.gradle.util.BuildCommencedTimeProvider
import spock.lang.Specification
import spock.lang.Unroll

class CachingModuleVersionRepositoryTest extends Specification {

    final realRepo = Mock(ModuleVersionRepository)
    final moduleResolutionCache = Mock(ModuleResolutionCache)
    final moduleDescriptorCache = Mock(ModuleMetaDataCache)
    final artifactAtRepositoryCache = Mock(CachedArtifactIndex)
    final resolver = Mock(DependencyToModuleVersionResolver)
    final cachePolicy = Mock(CachePolicy)
    CachingModuleVersionRepository repo = new CachingModuleVersionRepository(realRepo, moduleResolutionCache, moduleDescriptorCache, artifactAtRepositoryCache,
            resolver, cachePolicy, new BuildCommencedTimeProvider())
    int descriptorHash = 1234
    CachingModuleVersionRepository.CachingModuleSource moduleSource = Mock()

    @Unroll
    "last modified date is cached - lastModified = #lastModified"(Date lastModified) {
        given:
        ExternalResourceMetaData externalResourceMetaData = new DefaultExternalResourceMetaData("remote url", lastModified, -1, null, null)
        File file = new File("local")
        BuildableArtifactResolveResult result = Mock()
        def artifactId = Stub(ModuleVersionArtifactIdentifier)
        def artifact = Stub(ModuleVersionArtifactMetaData) {
            getId() >> artifactId
        }
        ArtifactAtRepositoryKey atRepositoryKey = new ArtifactAtRepositoryKey("repo-id", artifactId)

        and:
        _ * realRepo.id >> "repo-id"
        _ * realRepo.isLocal() >> false
        _ * moduleSource.descriptorHash >> descriptorHash
        _ * moduleSource.isChangingModule >> true
        _ * artifactAtRepositoryCache.lookup(atRepositoryKey) >> null
        _ * realRepo.resolve(artifact, result, null)
        _ * result.file >> file
        _ * result.externalResourceMetaData >> externalResourceMetaData

        when:
        repo.resolve(artifact, result, moduleSource)

        then:
        1 * artifactAtRepositoryCache.store(atRepositoryKey, file, descriptorHash)
        0 * moduleDescriptorCache._
        where:
        lastModified << [new Date(), null]
    }
}
