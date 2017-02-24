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
import org.gradle.caching.BuildCacheService
import org.gradle.caching.configuration.AbstractBuildCache
import org.gradle.caching.configuration.BuildCache
import org.gradle.caching.configuration.internal.DefaultBuildCacheConfiguration
import org.gradle.caching.local.LocalBuildCache
import org.gradle.internal.progress.BuildOperationExecutor
import org.gradle.internal.reflect.Instantiator
import spock.lang.Specification

class DefaultCompositeBuildCacheServiceFactoryTest extends Specification {
    List<BuildCache> sensedBuildCaches = []

    def startParameter = Mock(StartParameter) {
        getSystemPropertiesArgs() >> [:]
    }
    def buildCacheService = Stub(BuildCacheService) {
        getDescription() >> "mock"
    }
    def buildCache = Mock(LocalBuildCache)
    def instantiator = Mock(Instantiator) {
        newInstance(_) >> buildCache
    }
    def buildCacheConfiguration = new DefaultBuildCacheConfiguration(instantiator, [], startParameter)
    def buildOperationExecuter = Mock(BuildOperationExecutor)
    def factory = new DefaultCompositeBuildCacheServiceFactory(buildCacheConfiguration, instantiator, buildOperationExecuter) {
        BuildCacheService createDecoratedBuildCacheService(BuildCache buildCache) {
            sensedBuildCaches += buildCache
            buildCacheService
        }
    }

    CompositeBuildCache createCompositeBuildCache(localBuildCache, remoteBuildCache) {
        def compositeBuildCache = new CompositeBuildCache()
        compositeBuildCache.local = localBuildCache
        compositeBuildCache.remote = remoteBuildCache
        compositeBuildCache.enabled = true
        compositeBuildCache.push = true
        return compositeBuildCache
    }

    def createConfiguration(type) {
        Stub(type) {
            isEnabled() >> true
        }
    }

    def 'local cache service is created when remote is not configured'() {
        def local = createConfiguration(LocalBuildCache)
        def remote = null
        def compositeBuildCache = createCompositeBuildCache(local, remote)

        when:
        factory.build(compositeBuildCache)
        then:
        sensedBuildCaches == [local]
    }

    def 'local cache service is created when remote is disabled'() {
        def local = createConfiguration(LocalBuildCache)
        def remote = Stub(RemoteBuildCache) {
            isEnabled() >> false
        }
        def compositeBuildCache = createCompositeBuildCache(local, remote)
        when:
        factory.build(compositeBuildCache)
        then:
        sensedBuildCaches == [local]
    }

    def 'remote cache service is created when local is disabled'() {
        def local = Stub(LocalBuildCache) {
            isEnabled() >> false
        }
        def remote = createConfiguration(RemoteBuildCache)
        def compositeBuildCache = createCompositeBuildCache(local, remote)

        when:
        factory.build(compositeBuildCache)
        then:
        sensedBuildCaches == [remote]
    }

    def 'composite cache service is created when local and remote are enabled'() {
        def local = createConfiguration(LocalBuildCache)
        def remote = createConfiguration(RemoteBuildCache)
        def compositeBuildCache = createCompositeBuildCache(local, remote)

        when:
        def buildCacheService = factory.build(compositeBuildCache)
        then:
        sensedBuildCaches == [local, remote]
        buildCacheService.description == "mock (pushing enabled) and mock"
    }

    def 'when caching is disabled no services are created'() {
        def compositeBuildCache = new CompositeBuildCache()
        compositeBuildCache.enabled = false
        when:
        def buildCacheService = factory.build(compositeBuildCache)

        then:
        buildCacheService.description == 'NO-OP build cache'
    }

    private static class RemoteBuildCache extends AbstractBuildCache {}
}
