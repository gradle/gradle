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

import org.gradle.caching.BuildCacheService
import org.gradle.caching.BuildCacheServiceFactory
import org.gradle.caching.configuration.AbstractBuildCache
import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal
import org.gradle.caching.local.LocalBuildCache
import org.gradle.internal.progress.BuildOperationExecutor
import org.gradle.internal.reflect.Instantiator
import spock.lang.Specification

class DefaultCompositeBuildCacheServiceFactoryTest extends Specification {

    private localCacheService = Stub(BuildCacheService) {
        getDescription() >> 'a local build cache'
    }
    private localBuildCacheFactory = Stub(BuildCacheServiceFactory) {
        build(_ as LocalBuildCache) >> localCacheService
    }
    private remoteCacheService = Stub(BuildCacheService) {
        getDescription() >> 'a remote build cache'
    }
    private remoteBuildCacheFactory = Stub(BuildCacheServiceFactory) {
        build(_ as RemoteBuildCache) >> remoteCacheService
    }
    private buildCacheConfiguration = Stub(BuildCacheConfigurationInternal) {
        getBuildCacheServiceFactoryType(LocalBuildCache) >> DefaultLocalBuildCacheServiceFactory
        getBuildCacheServiceFactoryType(RemoteBuildCache) >> RemoteBuildCacheServiceFactory
    }
    private buildOperationExecuter = Mock(BuildOperationExecutor)
    private instantiator = Stub(Instantiator) {
        newInstance(DefaultLocalBuildCacheServiceFactory) >> localBuildCacheFactory
        newInstance(RemoteBuildCacheServiceFactory) >> remoteBuildCacheFactory
    }
    private DefaultCompositeBuildCacheServiceFactory factory = new DefaultCompositeBuildCacheServiceFactory(buildCacheConfiguration, instantiator, buildOperationExecuter)
    CompositeBuildCache composingBuildCache = new CompositeBuildCache()

    def 'local cache service is created'() {
        composingBuildCache.addDelegate(new LocalBuildCache())

        when:
        def buildCacheService = factory.build(composingBuildCache)

        then:
        buildCacheService.description == "a local build cache"
    }

    def 'remote cache service is created'() {
        composingBuildCache.addDelegate(new RemoteBuildCache())

        when:
        def buildCacheService = factory.build(composingBuildCache)

        then:
        buildCacheService.description == "a remote build cache"
    }

    def 'can push to local'() {
        composingBuildCache.addDelegate(new LocalBuildCache(push: true))
        composingBuildCache.addDelegate(new RemoteBuildCache(push: false))

        when:
        def buildCacheService = factory.build(composingBuildCache)

        then:
        buildCacheService.description == 'a local build cache(pushing enabled) and a remote build cache'
    }

    def 'can push to remote'() {
        composingBuildCache.addDelegate(new LocalBuildCache(push: false))
        composingBuildCache.addDelegate(new RemoteBuildCache(push: true))

        when:
        def buildCacheService = factory.build(composingBuildCache)

        then:
        buildCacheService.description == 'a local build cache and a remote build cache(pushing enabled)'
    }

    def 'can pull from local and remote'() {
        composingBuildCache.addDelegate(new LocalBuildCache(push: false))
        composingBuildCache.addDelegate(new RemoteBuildCache(push: false))

        when:
        def buildCacheService = factory.build(composingBuildCache)

        then:
        buildCacheService.description == 'a local build cache and a remote build cache'
    }

    def 'when caching is disabled no services are created'() {
        when:
        def buildCacheService = factory.build(composingBuildCache)

        then:
        buildCacheService.description == 'NO-OP build cache'
    }

    private static class RemoteBuildCache extends AbstractBuildCache {}
    private static class RemoteBuildCacheServiceFactory implements BuildCacheServiceFactory<RemoteBuildCache> {
        @Override
        BuildCacheService build(RemoteBuildCache configuration) {
            return null
        }
    }
}
