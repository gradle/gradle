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

package org.gradle.caching.internal.controller

import org.gradle.StartParameter
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.file.TemporaryFileProvider
import org.gradle.caching.BuildCacheEntryReader
import org.gradle.caching.BuildCacheEntryWriter
import org.gradle.caching.BuildCacheException
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.BuildCacheService
import org.gradle.caching.BuildCacheServiceFactory
import org.gradle.caching.configuration.AbstractBuildCache
import org.gradle.caching.configuration.internal.DefaultBuildCacheConfiguration
import org.gradle.caching.configuration.internal.DefaultBuildCacheServiceRegistration
import org.gradle.caching.internal.FinalizeBuildCacheConfigurationBuildOperationType
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
    def gradle = Mock(GradleInternal) {
        getStartParameter() >> startParameter
        getIdentityPath() >> Path.path("test")
    }

    def buildOperationExecuter = new TestBuildOperationExecutor()
    def temporaryFileProvider = Mock(TemporaryFileProvider)
    def config = new DefaultBuildCacheConfiguration(DirectInstantiator.INSTANCE, [
        new DefaultBuildCacheServiceRegistration(DirectoryBuildCache, TestDirectoryBuildCacheServiceFactory),
        new DefaultBuildCacheServiceRegistration(TestRemoteBuildCache, TestRemoteBuildCacheServiceFactory),

    ])

    private DefaultBuildCacheController createController() {
        createController(DefaultBuildCacheController)
    }

    private <T extends BuildCacheController> T createController(Class<T> controllerType) {
        def controller = BuildCacheControllerFactory.create(
            buildOperationExecuter,
            gradle,
            config,
            temporaryFileProvider,
            DirectInstantiator.INSTANCE
        )
        assert controllerType.isInstance(controller)
        controllerType.cast(controller)
    }

    private FinalizeBuildCacheConfigurationBuildOperationType.Result buildOpResult() {
        buildOperationExecuter.log.mostRecentResult(FinalizeBuildCacheConfigurationBuildOperationType)
    }

    def 'local cache service is created when remote is not configured'() {
        when:
        def c = createController()

        then:
        c.local.service != null
        c.remote.service == null

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
        def c = createController()

        then:
        c.local.service != null
        c.remote.service == null
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
        def c = createController()

        then:
        c.remote.service instanceof TestBuildCacheService
        with(buildOpResult()) {
            local == null
            remote.type == "remote"
            remote.className == TestRemoteBuildCache.name
        }
    }

    def 'can enable both'() {
        config.remote(TestRemoteBuildCache)

        when:
        def c = createController()

        then:
        c.local.service != null
        c.remote.service != null
        with(buildOpResult()) {
            local.type == "directory"
            remote.type == "remote"
        }
    }

    def 'when caching is disabled no services are created'() {
        buildCacheEnabled = false

        expect:
        createController(NoOpBuildCacheController)
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
            new TestBuildCacheService()
        }
    }

    static class TestBuildCacheService implements BuildCacheService {

        @Override
        boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
            return false
        }

        @Override
        void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {

        }

        @Override
        void close() throws IOException {

        }
    }

    static class TestDirectoryBuildCacheServiceFactory implements BuildCacheServiceFactory<DirectoryBuildCache> {
        @Override
        BuildCacheService createBuildCacheService(DirectoryBuildCache configuration, BuildCacheServiceFactory.Describer describer) {
            def chain = describer.type("directory")
            if (configuration.directory != null) {
                chain.config("location", configuration.directory.toString())
            }

            new TestBuildCacheService()
        }
    }

}
