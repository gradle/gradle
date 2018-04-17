/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.DefaultTask
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.initialization.ProjectAccessListener
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.model.internal.registry.DefaultModelRegistry
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class DefaultTaskContainerParallelTest extends ConcurrentSpec {
    private taskFactory = Mock(ITaskFactory)
    def modelRegistry = new DefaultModelRegistry(null, null)
    private project = Mock(ProjectInternal, name: "<project>") {
        getModelRegistry() >> modelRegistry
    }
    private accessListener = Mock(ProjectAccessListener)
    private container = new DefaultTaskContainerFactory(modelRegistry, DirectInstantiator.INSTANCE, taskFactory, project, accessListener, new TaskStatistics()).create()

    def "two threads can access a lazy task simultaneously using create provider"() {
        def task = Mock(DefaultTask) {
            getName() >> "foo"
        }
        def taskProvider = container.createLater("foo") { }

        when:
        async {
            start {
                instant.thread1
                thread.blockUntil.thread2
                assert taskProvider.get() == task
            }
            start {
                instant.thread2
                thread.blockUntil.thread1
                assert taskProvider.get() == task
            }
        }

        then:
        1 * taskFactory.create("foo", _) >> task
    }

    def "two threads can access a lazy task simultaneously using get provider"() {
        def task = Mock(DefaultTask) {
            getName() >> "foo"
        }
        container.createLater("foo") { }
        def taskProvider = container.getByNameLater(DefaultTask, "foo")

        when:
        async {
            start {
                instant.thread1
                thread.blockUntil.thread2
                assert taskProvider.get() == task
            }
            start {
                instant.thread2
                thread.blockUntil.thread1
                assert taskProvider.get() == task
            }
        }

        then:
        1 * taskFactory.create("foo", _) >> task
    }

    def "two threads can access a lazy task simultaneously using both create and get providers"() {
        def task = Mock(DefaultTask) {
            getName() >> "foo"
        }
        def createProvider = container.createLater("foo") { }
        def getProvider = container.getByNameLater(DefaultTask, "foo")

        when:
        async {
            start {
                instant.thread1
                thread.blockUntil.thread2
                assert createProvider.get() == task
            }
            start {
                instant.thread2
                thread.blockUntil.thread1
                assert getProvider.get() == task
            }
        }

        then:
        1 * taskFactory.create("foo", _) >> task
    }
}
