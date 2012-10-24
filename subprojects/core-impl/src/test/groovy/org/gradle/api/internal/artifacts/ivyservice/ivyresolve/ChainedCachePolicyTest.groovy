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
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ArtifactIdentifier
import org.gradle.api.artifacts.ResolvedModuleVersion
import org.apache.ivy.core.module.id.ModuleRevisionId

public class ChainedCachePolicyTest extends Specification {

    private CachePolicy cachePolicy1 = Mock()
    private CachePolicy cachePolicy2 = Mock()

    private ModuleVersionSelector selector = Mock()
    private ModuleVersionIdentifier moduleId = Mock()
    private long ageMillis = 0;

    private File cachedArtifactfile = Mock()
    private ArtifactIdentifier artifactIdentifier = Mock()
    private ResolvedModuleVersion resolvedModuleVersion = Mock()
    private ModuleRevisionId moduleRevisionId = Mock()

    private ChainedCachePolicy chainedCachePolicy = new ChainedCachePolicy(cachePolicy1, cachePolicy2);


    def "chained mustRefreshDynamicVersion calls stop on first positive delegate call"(){
        when:
        chainedCachePolicy.mustRefreshDynamicVersion(selector, moduleId, ageMillis)
        then:
        1 * cachePolicy1.mustRefreshDynamicVersion(selector, moduleId, ageMillis) >> true
        0 * cachePolicy2.mustRefreshDynamicVersion(selector, moduleId, ageMillis) >> true
    }

    def "mustRefreshDynamicVersion returns false if all sub cachepolicies return false"(){
        when:
        1 * cachePolicy1.mustRefreshDynamicVersion(selector, moduleId, ageMillis) >> false
        1 * cachePolicy2.mustRefreshDynamicVersion(selector, moduleId, ageMillis) >> false
        then:
        !chainedCachePolicy.mustRefreshDynamicVersion(selector, moduleId, ageMillis)
    }

    def "chained mustRefreshArtifact calls stop on first positive delegate call"(){
        when:
        chainedCachePolicy.mustRefreshArtifact(artifactIdentifier, cachedArtifactfile, ageMillis)
        then:
        1 * cachePolicy1.mustRefreshArtifact(artifactIdentifier, cachedArtifactfile, ageMillis) >> true
        0 * cachePolicy2.mustRefreshArtifact(artifactIdentifier, cachedArtifactfile, ageMillis) >> true
    }

    def "mustRefreshArtifact returns false if all sub cachepolicies return false"(){
        when:
        1 * cachePolicy1.mustRefreshArtifact(artifactIdentifier, cachedArtifactfile, ageMillis) >> false
        1 * cachePolicy2.mustRefreshArtifact(artifactIdentifier, cachedArtifactfile, ageMillis) >> false
        then:
        !chainedCachePolicy.mustRefreshArtifact(artifactIdentifier, cachedArtifactfile, ageMillis)
    }

    def "chained mustRefreshChangingModule calls stop on first positive delegate call"(){
        when:
        chainedCachePolicy.mustRefreshChangingModule(moduleId, resolvedModuleVersion, ageMillis)
        then:
        1 * cachePolicy1.mustRefreshChangingModule(moduleId, resolvedModuleVersion, ageMillis) >> true
        0 * cachePolicy1.mustRefreshChangingModule(moduleId, resolvedModuleVersion, ageMillis) >> true
    }

    def "mustRefreshChangingModule returns false if all sub cachepolicies return false"(){
        when:
        1 * cachePolicy1.mustRefreshChangingModule(moduleId, resolvedModuleVersion, ageMillis) >> false
        1 * cachePolicy2.mustRefreshChangingModule(moduleId, resolvedModuleVersion, ageMillis) >> false
        then:
        !chainedCachePolicy.mustRefreshChangingModule(moduleId, resolvedModuleVersion, ageMillis)
    }

    def "chained mustRefreshModule calls stop on first positive delegate call"(){
        when:
        chainedCachePolicy.mustRefreshModule(moduleId, resolvedModuleVersion, moduleRevisionId, ageMillis)
        then:
        1 * cachePolicy1.mustRefreshModule(moduleId, resolvedModuleVersion, moduleRevisionId, ageMillis) >> true
        0 * cachePolicy1.mustRefreshModule(moduleId, resolvedModuleVersion, moduleRevisionId, ageMillis) >> true
    }

    def "mustRefreshModule returns false if all sub cachepolicies return false"(){
        when:
        1 * cachePolicy1.mustRefreshModule(moduleId, resolvedModuleVersion, moduleRevisionId, ageMillis) >> false
        0 * cachePolicy2.mustRefreshModule(moduleId, resolvedModuleVersion, moduleRevisionId, ageMillis) >> false
        then:
        !cachePolicy1.mustRefreshModule(moduleId, resolvedModuleVersion, moduleRevisionId, ageMillis)
    }
}
