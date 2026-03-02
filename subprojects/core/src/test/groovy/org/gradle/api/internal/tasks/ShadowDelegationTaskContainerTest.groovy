/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks


import org.gradle.api.Task
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.project.BuildOperationCrossProjectConfigurator
import org.gradle.api.internal.project.CrossProjectModelAccess
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.api.internal.project.taskfactory.TestTaskIdentities
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class ShadowDelegationTaskContainerTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def taskIdentityFactory = TestTaskIdentities.factory()
    def taskFactory = Mock(ITaskFactory)
    def project = Mock(ProjectInternal)
    def buildOperationRunner = new TestBuildOperationRunner()
    def crossProjectModelAccess = Mock(CrossProjectModelAccess)
    def taskShadowingRegistry = new DefaultTaskShadowingRegistry()

    def container = new DefaultTaskContainer(
        project,
        DirectInstantiator.INSTANCE,
        taskIdentityFactory,
        taskFactory,
        new TaskStatistics(),
        buildOperationRunner,
        new BuildOperationCrossProjectConfigurator(buildOperationRunner),
        CollectionCallbackActionDecorator.NOOP,
        crossProjectModelAccess,
        taskShadowingRegistry
    )

    interface PublicTask extends Task {}
    interface InternalTask extends Task {}

    def "uses TaskShadowingRegistry to redirect types and wrap instances"() {
        given:
        def taskName = "shadowed"
        def internalTask = Mock(InternalTask)
        def publicTask = Mock(PublicTask)
        def publicProvider = Mock(TaskProvider)

        taskShadowingRegistry.registerShadowing(PublicTask, InternalTask, { obj, type ->
            if (obj instanceof TaskProvider && type == PublicTask) {
                return publicProvider
            }
            return obj
        })
        publicProvider.get() >> publicTask

        when:
        def provider = container.register(taskName, PublicTask)

        then:
        provider == publicProvider
        provider.get() == publicTask
    }

    def "works for withType"() {
        given:
        def publicCollection = Mock(TaskCollection)
        taskShadowingRegistry.registerShadowing(PublicTask, InternalTask, { obj, type ->
            if (obj instanceof TaskCollection && type == PublicTask) {
                return publicCollection
            }
            return obj
        })

        when:
        def collection = container.withType(PublicTask)

        then:
        collection == publicCollection
    }

    def "works for named"() {
        given:
        def taskName = "shadowed"
        def publicProvider = Mock(TaskProvider)
        taskShadowingRegistry.registerShadowing(Task, Task, { obj, type ->
            if (obj instanceof TaskProvider && type == Task) {
                return publicProvider
            }
            return obj
        })

        when:
        def provider = container.named(taskName)

        then:
        provider == publicProvider
    }
}
