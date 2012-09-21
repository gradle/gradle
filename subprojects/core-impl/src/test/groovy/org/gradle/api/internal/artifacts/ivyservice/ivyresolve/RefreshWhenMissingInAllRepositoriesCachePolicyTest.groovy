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

import spock.lang.Specification
import org.gradle.api.artifacts.ArtifactIdentifier
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy
import org.gradle.api.artifacts.ResolvedModuleVersion
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleDescriptorCache
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache

class RefreshWhenMissingInAllRepositoriesCachePolicyTest extends Specification {

    private ArtifactIdentifier artifactIdentifier = Mock()
    private File cachedArtifactFile = Mock()
    private long ageMillis = 0;
    private CachePolicy delegateCachePolicy = Mock()
    private ResolvedModuleVersion resolvedModuleVersion = Mock()
    private ModuleVersionIdentifier moduleVersionId = Mock()
    private ModuleRevisionId moduleRevisionId = Mock()
    private ModuleDescriptorCache moduleDescriptorCache = Mock()
    private ModuleResolutionCache moduleResolutionCache = Mock()

    private RefreshWhenMissingInAllRepositoriesCachePolicy policy = new RefreshWhenMissingInAllRepositoriesCachePolicy(delegateCachePolicy, moduleResolutionCache, moduleDescriptorCache);
    private ModuleVersionSelector selector = Mock()
    ModuleVersionRepository repo1 = Mock()
    ModuleVersionRepository repo2 = Mock()
    ModuleVersionRepository repo3 = Mock()
    ModuleVersionRepository repo4 = Mock()
    ModuleDescriptorCache.CachedModuleDescriptor cachedModuleDescriptorFound = Mock()
    ModuleDescriptorCache.CachedModuleDescriptor cachedModuleDescriptorMissing = Mock()
    ModuleResolutionCache.CachedModuleResolution cachedModuleResolution = Mock()

    def setup(){
        policy.registerRepository(repo1)
        policy.registerRepository(repo2)
        policy.registerRepository(repo3)
        policy.registerRepository(repo4)
    }

    def "mustRefreshArtifact is delegates to underlaying cachingPolicy"() {
        when:
        policy.mustRefreshArtifact(artifactIdentifier, cachedArtifactFile, ageMillis);
        then:
        1 * delegateCachePolicy.mustRefreshArtifact(artifactIdentifier, cachedArtifactFile, ageMillis);
    }

    def "mustRefreshChangingModule is delegates to underlaying cachingPolicy"() {
        when:
        policy.mustRefreshChangingModule(moduleVersionId, resolvedModuleVersion, ageMillis)
        then:
        1 * delegateCachePolicy.mustRefreshChangingModule(moduleVersionId, resolvedModuleVersion, ageMillis);
    }

    def "mustRefreshDynamicVersion is delegates to underlaying cachingPolicy"() {
        when:
        policy.mustRefreshDynamicVersion(selector, moduleVersionId, ageMillis)
        then:
        1 * delegateCachePolicy.mustRefreshDynamicVersion(selector, moduleVersionId, ageMillis);
    }

    def "with positive mustRefreshModule on underlaying CachePolicy moduleDescriptor cache is not hitten"() {
        when:
        def refresh = policy.mustRefreshModule(moduleVersionId, resolvedModuleVersion, moduleRevisionId, ageMillis);
        then:
        refresh
        1 * delegateCachePolicy.mustRefreshModule(moduleVersionId, resolvedModuleVersion, moduleRevisionId, ageMillis) >> true
        0 * moduleDescriptorCache.getCachedModuleDescriptor(_, _)
    }

    def "with negative mustRefreshModule on underlaying CachePolicy moduleDescriptor cache is hitten for first positive hit"() {
        when:
        policy.mustRefreshModule(moduleVersionId, resolvedModuleVersion, moduleRevisionId, ageMillis);
        then:
        1 * delegateCachePolicy.mustRefreshModule(moduleVersionId, resolvedModuleVersion, moduleRevisionId, ageMillis) >> false
        4 * moduleDescriptorCache.getCachedModuleDescriptor(_, _)
    }

    def "must not refresh when dynamic moduleDescriptor is cached in other repo"() {
        when:
        def refresh = policy.mustRefreshModule(moduleVersionId, resolvedModuleVersion, moduleRevisionId, ageMillis);
        then:
        refresh == false
        1 * delegateCachePolicy.mustRefreshModule(moduleVersionId, resolvedModuleVersion, moduleRevisionId, ageMillis) >> false
        1 * moduleResolutionCache.getCachedModuleResolution(repo1, moduleRevisionId) >> cachedModuleResolution
        1 * cachedModuleResolution.resolvedVersion >> moduleRevisionId
        1 * moduleDescriptorCache.getCachedModuleDescriptor(repo1, moduleRevisionId) >> cachedModuleDescriptorFound
        1 * cachedModuleDescriptorFound.missing >> false
    }

    def "with negative mustRefreshModule on underlaying CachePolicy moduleDescriptor cache is hitten to check other repositories"() {
        when:
        def refresh = policy.mustRefreshModule(moduleVersionId, resolvedModuleVersion, moduleRevisionId, ageMillis);
        then:
        refresh == false
        1 * delegateCachePolicy.mustRefreshModule(moduleVersionId, resolvedModuleVersion, moduleRevisionId, ageMillis) >> false
        1 * moduleDescriptorCache.getCachedModuleDescriptor(repo1, moduleRevisionId) >> null
        1 * cachedModuleDescriptorMissing.isMissing() >> true
        1 * moduleDescriptorCache.getCachedModuleDescriptor(repo2, _) >> cachedModuleDescriptorMissing
        1 * moduleDescriptorCache.getCachedModuleDescriptor(repo3, _) >> cachedModuleDescriptorFound
        1 * cachedModuleDescriptorFound.isMissing() >> false
        0 * moduleDescriptorCache.getCachedModuleDescriptor(repo4, _) >> null
    }
}
