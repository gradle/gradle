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

import org.gradle.api.artifacts.ResolvedModuleVersion
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleDescriptorCache
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache

class RefreshWhenMissingInAllRepositoriesCachePolicyTest extends Specification {

    private ArtifactIdentifier artifactIdentifier = Mock()
    private File cachedArtifactFile = Mock()
    private long ageMillis = 1000;
    private ResolvedModuleVersion resolvedModuleVersion = Mock()
    private ModuleVersionIdentifier moduleVersionId = Mock()
    private ModuleRevisionId moduleRevisionId = Mock()
    private ModuleDescriptorCache moduleDescriptorCache = Mock()
    private ModuleResolutionCache moduleResolutionCache = Mock()

    private RefreshWhenMissingInAllRepositoriesCachePolicy policy = new RefreshWhenMissingInAllRepositoriesCachePolicy(moduleResolutionCache, moduleDescriptorCache);
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

    def "mustRefreshArtifact returns false"() {
        expect:
        ! policy.mustRefreshArtifact(artifactIdentifier, cachedArtifactFile, ageMillis);
    }

    def "mustRefreshChangingModule returns false"() {
        expect:
        ! policy.mustRefreshChangingModule(moduleVersionId, resolvedModuleVersion, ageMillis);
    }

    def "mustRefreshModule for null moduleRevisionId returns false"() {
        expect:
        ! policy.mustRefreshModule(moduleVersionId, resolvedModuleVersion, null, ageMillis);
    }

    def "mustRefreshDynamicVersion returns false"(){
        expect:
        ! policy.mustRefreshDynamicVersion(selector, moduleVersionId, ageMillis)
    }

    def "must not refresh when dynamic moduleDescriptor is cached in other repo"() {
        when:
        def refresh = policy.mustRefreshModule(moduleVersionId, resolvedModuleVersion, moduleRevisionId, ageMillis);
        then:
        refresh == false
        1 * moduleResolutionCache.getCachedModuleResolution(repo1, moduleRevisionId) >> cachedModuleResolution
        1 * cachedModuleResolution.resolvedVersion >> moduleRevisionId
        1 * moduleDescriptorCache.getCachedModuleDescriptor(repo1, moduleRevisionId) >> cachedModuleDescriptorFound
        1 * cachedModuleDescriptorFound.missing >> false
    }

    def "must not refresh module when build has started before last cache entry was written"(){
        when:
        def refresh = policy.mustRefreshModule(moduleVersionId, resolvedModuleVersion, moduleRevisionId, 0)
        then:
        refresh == false;
        0 * moduleResolutionCache.getCachedModuleResolution(repo1, moduleRevisionId)
        0 * cachedModuleResolution.resolvedVersion
        0 * moduleDescriptorCache.getCachedModuleDescriptor(repo1, moduleRevisionId)
        0 * cachedModuleDescriptorFound.missing

    }

    def "moduleDescriptorCache is hitten to check module for each repository"() {
        when:
        def refresh = policy.mustRefreshModule(moduleVersionId, resolvedModuleVersion, moduleRevisionId, ageMillis);
        then:
        refresh
        1 * moduleDescriptorCache.getCachedModuleDescriptor(repo1, _) >> cachedModuleDescriptorMissing
        1 * moduleDescriptorCache.getCachedModuleDescriptor(repo2, _) >> cachedModuleDescriptorMissing
        1 * moduleDescriptorCache.getCachedModuleDescriptor(repo3, _) >> cachedModuleDescriptorMissing
        1 * moduleDescriptorCache.getCachedModuleDescriptor(repo4, _) >> cachedModuleDescriptorMissing
        4 * cachedModuleDescriptorMissing.missing >> true
    }

    def "mustRefreshModule returns false after first positive hit"() {
        when:
        def refresh = policy.mustRefreshModule(moduleVersionId, resolvedModuleVersion, moduleRevisionId, ageMillis);
        then:
        !refresh
        1 * moduleDescriptorCache.getCachedModuleDescriptor(repo1, _) >> cachedModuleDescriptorMissing
        1 * cachedModuleDescriptorMissing.missing >> true
        1 * moduleDescriptorCache.getCachedModuleDescriptor(repo2, _) >> cachedModuleDescriptorFound
        1 * cachedModuleDescriptorFound.missing >> false
        0 * moduleDescriptorCache.getCachedModuleDescriptor(repo3, _)
        0 * moduleDescriptorCache.getCachedModuleDescriptor(repo4, _)

    }

}
