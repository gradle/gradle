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
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.artifacts.ResolvedModuleVersion
import org.gradle.api.artifacts.ModuleVersionIdentifier

public class OnlyOncePerRepositoryRefreshModuleCachePolicyTest extends Specification {

    static final String REPOSITORY_ID = "REPO_ID"
    ModuleVersionRepository moduleVersionRepository = Mock()
    CachePolicy delegateCachePolicy = Mock()
    CachePolicyLookupRegistry repositoryModuleLookUpRegistry = Mock();


    private ModuleVersionIdentifier moduleVersionId = Mock()
    private ModuleRevisionId moduleRevisionId = Mock()
    private ResolvedModuleVersion resolvedModuleVersion = Mock()
    private long ageMillis = 1000

    private OnlyOncePerRepositoryRefreshModuleCachePolicy cachePolicy = new OnlyOncePerRepositoryRefreshModuleCachePolicy(moduleVersionRepository, repositoryModuleLookUpRegistry, delegateCachePolicy)

    def setup() {
        _ * moduleVersionRepository.getId() >> REPOSITORY_ID
        _ * delegateCachePolicy.mustRefreshModule(moduleVersionId, resolvedModuleVersion, moduleRevisionId, ageMillis) >> true
    }

    def "registers cachepolicy evaluaton per repo and module"() {
        setup:
        1 * repositoryModuleLookUpRegistry.get(moduleRevisionId) >> []
        when:
        cachePolicy.mustRefreshModule(moduleVersionId, resolvedModuleVersion, moduleRevisionId, ageMillis)
        then:
        1 * delegateCachePolicy.mustRefreshModule(moduleVersionId, resolvedModuleVersion, moduleRevisionId, ageMillis)
        1 * repositoryModuleLookUpRegistry.add(moduleRevisionId, REPOSITORY_ID)
    }

    def "delegate cachePolicy not evaluated for registered cachePolicy evaluations"() {
        setup:
        1 * repositoryModuleLookUpRegistry.get(moduleRevisionId) >> [REPOSITORY_ID]
        when:
        !cachePolicy.mustRefreshModule(moduleVersionId, resolvedModuleVersion, moduleRevisionId, ageMillis)
        then:
        0 * delegateCachePolicy.mustRefreshModule(moduleVersionId, resolvedModuleVersion, moduleRevisionId, ageMillis)
        0 * repositoryModuleLookUpRegistry.add(moduleRevisionId, REPOSITORY_ID)
    }
}
