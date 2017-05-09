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
import org.gradle.api.internal.file.TemporaryFileProvider
import org.gradle.cache.CacheRepository
import org.gradle.cache.internal.CacheFactory
import org.gradle.cache.internal.DefaultCacheRepository
import org.gradle.deployment.internal.DefaultDeploymentRegistry
import org.gradle.deployment.internal.DeploymentRegistry
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.internal.jvm.inspection.JvmVersionDetector
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.progress.DefaultBuildOperationExecutor
import org.gradle.internal.remote.MessagingServer
import org.gradle.internal.resources.ProjectLeaseRegistry
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.time.TimeProvider
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.process.internal.JavaExecHandleFactory
import org.gradle.process.internal.health.memory.MemoryManager
import org.gradle.process.internal.worker.DefaultWorkerProcessFactory
import org.gradle.process.internal.worker.WorkerProcessFactory
import org.gradle.process.internal.worker.child.WorkerProcessClassPathProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class BuildSessionScopeServicesTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    ServiceRegistry parent = Stub()
    StartParameter startParameter = new StartParameter()
    BuildSessionScopeServices registry = new BuildSessionScopeServices(parent, startParameter, ClassPath.EMPTY)

    def setup() {
        startParameter.gradleUserHomeDir = tmpDir.testDirectory
        parent.get(CacheFactory) >> Stub(CacheFactory)
        parent.get(ModuleRegistry) >> new DefaultModuleRegistry(CurrentGradleInstallation.get())
        parent.get(FileResolver) >> Stub(FileResolver)
        parent.get(OutputEventListener) >> Stub(OutputEventListener)
    }

    def "provides a DeploymentRegistry"() {
        expect:
        registry.get(DeploymentRegistry) instanceof DefaultDeploymentRegistry
        registry.get(DeploymentRegistry) == registry.get(DeploymentRegistry)
    }

    def "provides a CacheRepository"() {
        expect:
        registry.get(CacheRepository) instanceof DefaultCacheRepository
        registry.get(CacheRepository) == registry.get(CacheRepository)
    }

    def "provides a BuildOperationWorkerRegistry"() {
        expect:
        registry.get(WorkerLeaseRegistry) instanceof DefaultWorkerLeaseService
        registry.get(WorkerLeaseRegistry) == registry.get(WorkerLeaseRegistry)
    }

    def "provides a ProjectLockService"() {
        expect:
        registry.get(ProjectLeaseRegistry) instanceof DefaultWorkerLeaseService
        registry.get(ProjectLeaseRegistry) == registry.get(ProjectLeaseRegistry)
    }

    def "provides a BuildOperationExecutor"() {
        given:
        def listenerManager = Mock(ListenerManager)
        listenerManager.createChild() >> Mock(ListenerManager)
        _ * parent.get(ListenerManager) >> listenerManager
        _ * parent.get(TimeProvider) >> Mock(TimeProvider)
        _ * parent.get(ProgressLoggerFactory) >> Mock(ProgressLoggerFactory)
        _ * parent.get(StartParameter) >> Mock(StartParameter)
        _ * parent.get(ExecutorFactory) >> Mock(ExecutorFactory)

        expect:
        registry.get(BuildOperationExecutor) instanceof DefaultBuildOperationExecutor
        registry.get(BuildOperationExecutor) == registry.get(BuildOperationExecutor)
    }

    def "provides a WorkerProcessBuilder factory"() {
        setup:
        expectParentServiceLocated(MessagingServer)
        expectParentServiceLocated(TemporaryFileProvider)
        expectParentServiceLocated(JavaExecHandleFactory)
        expectParentServiceLocated(JvmVersionDetector)
        expectParentServiceLocated(MemoryManager)

        expect:
        registry.get(WorkerProcessFactory) instanceof DefaultWorkerProcessFactory
        registry.get(WorkerProcessFactory) == registry.get(WorkerProcessFactory)
    }

    def "provides a ClassPathRegistry"() {
        expect:
        registry.get(ClassPathRegistry) instanceof ClassPathRegistry
        registry.get(ClassPathRegistry) == registry.get(ClassPathRegistry)
    }

    def "provides a WorkerProcessClassPathProvider"() {
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
