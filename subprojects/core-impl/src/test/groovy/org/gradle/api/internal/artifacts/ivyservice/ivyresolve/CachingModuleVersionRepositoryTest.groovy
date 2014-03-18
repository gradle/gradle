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
import org.gradle.api.Transformer
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleVersionsCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetaDataCache
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactIdentifier
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData
import org.gradle.api.internal.externalresource.cached.CachedArtifactIndex
import org.gradle.api.internal.externalresource.ivy.ArtifactAtRepositoryKey
import org.gradle.util.BuildCommencedTimeProvider
import spock.lang.Specification
import spock.lang.Unroll

class CachingModuleVersionRepositoryTest extends Specification {
    def realRepo = Stub(ModuleVersionRepository) {
        getId() >> "repo-id"
    }
    def moduleResolutionCache = Stub(ModuleVersionsCache)
    def moduleDescriptorCache = Mock(ModuleMetaDataCache)
    def artifactAtRepositoryCache = Mock(CachedArtifactIndex)
    def cachePolicy = Stub(CachePolicy)
    def metadataProcessor = Stub(ModuleMetadataProcessor)
    def moduleExtractor = Mock(Transformer)
    def repo = new CachingModuleVersionRepository(realRepo, moduleResolutionCache, moduleDescriptorCache, artifactAtRepositoryCache,
            cachePolicy, new BuildCommencedTimeProvider(), metadataProcessor, moduleExtractor)

    @Unroll
    def "last modified date is cached - lastModified = #lastModified"() {
        given:
        def artifactId = Stub(ModuleVersionArtifactIdentifier)
        def artifact = Stub(ModuleVersionArtifactMetaData) {
            getId() >> artifactId
        }

        def file = new File("local")
        def result = Stub(BuildableArtifactResolveResult) {
            getFile() >> file
            getFailure() >> null
        }

        def descriptorHash = 1234G
        def moduleSource = Stub(CachingModuleVersionRepository.CachingModuleSource) {
            getDescriptorHash() >> descriptorHash
        }

        ArtifactAtRepositoryKey atRepositoryKey = new ArtifactAtRepositoryKey("repo-id", artifactId)

        when:
        repo.resolve(artifact, result, moduleSource)

        then:
        1 * artifactAtRepositoryCache.store(atRepositoryKey, file, descriptorHash)
        0 * moduleDescriptorCache._

        where:
        lastModified << [new Date(), null]
    }
}
