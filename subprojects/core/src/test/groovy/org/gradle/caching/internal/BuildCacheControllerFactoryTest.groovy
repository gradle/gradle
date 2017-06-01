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
import org.gradle.caching.BuildCacheService
import org.gradle.caching.BuildCacheServiceFactory
import org.gradle.caching.configuration.AbstractBuildCache
import org.gradle.caching.configuration.internal.DefaultBuildCacheConfiguration
import org.gradle.caching.configuration.internal.DefaultBuildCacheServiceRegistration
import org.gradle.caching.internal.controller.BuildCacheControllerFactory
import org.gradle.caching.local.DirectoryBuildCache
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.testing.internal.util.Specification
import org.gradle.util.Path

class BuildCacheControllerFactoryTest extends Specification {

    def buildCacheEnabled = true
    def startParameter = Mock(StartParameter) {
        getSystemPropertiesArgs() >> [:]
        isBuildCacheEnabled() >> { buildCacheEnabled }
    }

    def buildOperationExecuter = new TestBuildOperationExecutor()
    def temporaryFileProvider = Mock(TemporaryFileProvider)
    def config = new DefaultBuildCacheConfiguration(DirectInstantiator.INSTANCE, [
        new DefaultBuildCacheServiceRegistration(DirectoryBuildCache, TestDirectoryBuildCacheServiceFactory),
        new DefaultBuildCacheServiceRegistration(TestRemoteBuildCache, TestRemoteBuildCacheServiceFactory),

    ])
    def provider = new BuildCacheControllerFactory(config, startParameter, DirectInstantiator.INSTANCE, buildOperationExecuter, temporaryFileProvider)

    private <T extends BuildCacheService> T create(Class<? extends T> serviceType) {
        def service = provider.createBuildCacheService(Path.path("test"))
        assert serviceType.isInstance(service)
        serviceType.cast(service)
    }

    private FinalizeBuildCacheConfigurationBuildOperationType.Result buildOpResult() {
        buildOperationExecuter.log.mostRecentResult(FinalizeBuildCacheConfigurationBuildOperationType)
    }

    def 'local cache service is created when remote is not configured'() {
        when:
        def c = create(BuildCacheService)

        then:
        c.role == "local"

        and:
        with(buildOpResult()) {
            local.type == "directory"
            local.className == DirectoryBuildCache.name
            remote == null
        }
    }

    def 'local cache service is created when remote is disabled'() {
        config.remote(TestRemoteBuildCache).enabled = false

        when:
        def s = create(BuildCacheService)

        then:
        s.role == "local"
        with(buildOpResult()) {
            local.type == "directory"
            local.className == DirectoryBuildCache.name
            remote == null
        }
    }

    def 'remote cache service is created when local is disabled'() {
        config.local.enabled = false
        config.remote(TestRemoteBuildCache)

        when:
        def c = create(BuildCacheService)

        then:
        c.role == "remote"
        with(buildOpResult()) {
            local == null
            remote.type == "remote"
            remote.className == TestRemoteBuildCache.name
        }
    }

    def 'dispatching cache service is created when local and remote are enabled'() {
        config.remote(TestRemoteBuildCache)

        when:
        create(DispatchingBuildCacheService)

        then:
        with(buildOpResult()) {
            local.type == "directory"
            remote.type == "remote"
        }
    }

    def 'when caching is disabled no services are` created'() {
        buildCacheEnabled = false

        expect:
        create(NoOpBuildCacheService)
        with(buildOpResult()) {
            local == null
            remote == null
        }
    }

    static class TestRemoteBuildCache extends AbstractBuildCache {
        String value
    }

    static class TestRemoteBuildCacheServiceFactory implements BuildCacheServiceFactory<TestRemoteBuildCache> {
        @Override
        BuildCacheService createBuildCacheService(TestRemoteBuildCache configuration, BuildCacheServiceFactory.Describer describer) {
            def chain = describer.type("remote")
            if (configuration.value != null) {
                chain.config("value", configuration.value)
            }
            new NoOpBuildCacheService()
        }
    }

    static class TestDirectoryBuildCacheServiceFactory implements BuildCacheServiceFactory<DirectoryBuildCache> {
        @Override
        BuildCacheService createBuildCacheService(DirectoryBuildCache configuration, BuildCacheServiceFactory.Describer describer) {
            def chain = describer.type("directory")
            if (configuration.directory != null) {
                chain.config("location", configuration.directory.toString())
            }

            new NoOpBuildCacheService()
        }
    }

}
