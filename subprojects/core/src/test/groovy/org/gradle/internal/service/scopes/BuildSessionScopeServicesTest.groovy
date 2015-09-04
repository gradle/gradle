/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.service.scopes

import org.gradle.StartParameter
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.classpath.DefaultModuleRegistry
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.file.FileResolver
import org.gradle.cache.CacheRepository
import org.gradle.cache.internal.CacheFactory
import org.gradle.cache.internal.DefaultCacheRepository
import org.gradle.deployment.internal.DefaultDeploymentRegistry
import org.gradle.deployment.internal.DeploymentRegistry
import org.gradle.internal.service.ServiceRegistry
import org.gradle.messaging.remote.MessagingServer
import org.gradle.process.internal.DefaultWorkerProcessFactory
import org.gradle.process.internal.WorkerProcessBuilder
import org.gradle.process.internal.child.WorkerProcessClassPathProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class BuildSessionScopeServicesTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    ServiceRegistry parent = Stub()
    StartParameter startParameter = new StartParameter()
    BuildSessionScopeServices registry = new BuildSessionScopeServices(parent, startParameter)

    def setup() {
        startParameter.gradleUserHomeDir = tmpDir.testDirectory
        parent.get(CacheFactory) >> Stub(CacheFactory)
        parent.get(ModuleRegistry) >> new DefaultModuleRegistry()
        parent.get(FileResolver) >> Stub(FileResolver)
    }

    def "provides a DeploymentRegistry" () {
        expect:
        registry.get(DeploymentRegistry) instanceof DefaultDeploymentRegistry
        registry.get(DeploymentRegistry) == registry.get(DeploymentRegistry)
    }

    def "provides a CacheRepository"() {
        expect:
        registry.get(CacheRepository) instanceof DefaultCacheRepository
        registry.get(CacheRepository) == registry.get(CacheRepository)
    }

    def "provides a WorkerProcessBuilder factory"() {
        setup:
        expectParentServiceLocated(MessagingServer)

        expect:
        registry.getFactory(WorkerProcessBuilder) instanceof DefaultWorkerProcessFactory
        registry.getFactory(WorkerProcessBuilder) == registry.getFactory(WorkerProcessBuilder)
    }

    def "provides a ClassPathRegistry" () {
        expect:
        registry.get(ClassPathRegistry) instanceof ClassPathRegistry
        registry.get(ClassPathRegistry) == registry.get(ClassPathRegistry)
    }

    def "provides a WorkerProcessClassPathProvider" () {
        expect:
        registry.get(WorkerProcessClassPathProvider) instanceof WorkerProcessClassPathProvider
        registry.get(WorkerProcessClassPathProvider) == registry.get(WorkerProcessClassPathProvider)
    }

    private <T> T expectParentServiceLocated(Class<T> type) {
        T t = Mock(type)
        parent.get(type) >> t
        t
    }
}
