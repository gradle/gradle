/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.internal

import org.gradle.StartParameter
import org.gradle.api.internal.file.TemporaryFileProvider
import org.gradle.caching.configuration.AbstractBuildCache
import org.gradle.caching.configuration.BuildCache
import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal
import org.gradle.caching.local.DirectoryBuildCache
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.reflect.Instantiator
import spock.lang.Specification

class BuildCacheServiceProviderTest extends Specification {
    List<BuildCache> sensedBuildCaches = []

    boolean buildCacheEnabled = true

    def startParameter = Mock(StartParameter) {
        getSystemPropertiesArgs() >> [:]
        isBuildCacheEnabled() >> { buildCacheEnabled }
    }
    def buildCacheService = Stub(RoleAwareBuildCacheService) {
        getRole() >> "mock"
        getDescription() >> "mock"
    }
    def buildCache = Mock(DirectoryBuildCache)
    def instantiator = Mock(Instantiator) {
        newInstance(_) >> buildCache
    }
    def local = createConfiguration(DirectoryBuildCache)
    def remote
    def buildCacheConfiguration = Mock(BuildCacheConfigurationInternal) {
        isPullDisabled() >> false
        isPushDisabled() >> false
        getLocal() >> { local }
        getRemote() >> { remote }
    }
    def buildOperationExecuter = Mock(BuildOperationExecutor)
    def temporaryFileProvider = Mock(TemporaryFileProvider)
    def provider = new BuildCacheServiceProvider(buildCacheConfiguration, startParameter, instantiator, buildOperationExecuter, temporaryFileProvider) {
        @Override
        RoleAwareBuildCacheService createDecoratedBuildCacheService(String role, BuildCache buildCache) {
            sensedBuildCaches += buildCache
            buildCacheService
        }
    }

    def createConfiguration(Class<? extends BuildCache> type) {
        Stub(type) {
            isEnabled() >> true
        }
    }

    def 'local cache service is created when remote is not configured'() {
        local = createConfiguration(DirectoryBuildCache)
        remote = null

        when:
        provider.createBuildCacheService()
        then:
        sensedBuildCaches == [local]
    }

    def 'local cache service is created when remote is disabled'() {
        local = createConfiguration(DirectoryBuildCache)
        remote = Stub(RemoteBuildCache) {
            isEnabled() >> false
        }
        when:
        provider.createBuildCacheService()
        then:
        sensedBuildCaches == [local]
    }

    def 'remote cache service is created when local is disabled'() {
        local = Stub(DirectoryBuildCache) {
            isEnabled() >> false
        }
        remote = createConfiguration(RemoteBuildCache)

        when:
        provider.createBuildCacheService()
        then:
        sensedBuildCaches == [remote]
    }

    def 'dispatching cache service is created when local and remote are enabled'() {
        local = createConfiguration(DirectoryBuildCache)
        remote = createConfiguration(RemoteBuildCache)

        when:
        def buildCacheService = provider.createBuildCacheService()
        then:
        sensedBuildCaches == [local, remote]
        buildCacheService instanceof DispatchingBuildCacheService
    }

    def 'when caching is disabled no services are created'() {
        buildCacheEnabled = false

        when:
        def buildCacheService = provider.createBuildCacheService()

        then:
        buildCacheService instanceof NoOpBuildCacheService
    }

    private static class RemoteBuildCache extends AbstractBuildCache {}
}
