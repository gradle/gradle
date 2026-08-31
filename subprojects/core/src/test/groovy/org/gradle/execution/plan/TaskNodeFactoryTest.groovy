/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.execution.plan

import org.gradle.api.Task
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.taskfactory.TestTaskIdentities
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.util.Path
import org.gradle.util.TestUtil
import spock.lang.Specification

class TaskNodeFactoryTest extends Specification {
    TaskNodeFactory factory
    def a = task('a')
    def b = task('b')
    def c = task('c')
    def d = task('d')
    def e = task('e')

    def setup() {
        factory = new TaskNodeFactory(Stub(BuildStateRegistry), Stub(NodeValidator), new TestBuildOperationRunner(), Stub(ExecutionNodeAccessHierarchies), TestUtil.problemsService(), TestUtil.inMemoryCacheFactory())
    }

    private TaskInternal task(String name) {
        def project = Mock(ProjectInternal) {
            getProjectIdentity() >> ProjectIdentity.forRootProject(Path.ROOT, "root")
        }
        Mock(TaskInternal) {
            getName() >> name
            compareTo(_) >> { args -> name.compareTo(args[0].name) }
            getProject() >> project
            getTaskIdentity() >> TestTaskIdentities.create(name, Task.class, project)
        }
    }

    void 'can create a node for a task'() {
        when:
        def node = factory.getOrCreateLocalNode(a)

        then:
        !node.inKnownState
        node.dependencyPredecessors.empty
        node.mustSuccessors.empty
        node.dependencySuccessors.empty
        node.shouldSuccessors.empty
        node.finalizingSuccessors.empty
        node.finalizers.empty
    }

    void 'caches node for a given task'() {
        when:
        def node = factory.getOrCreateLocalNode(a)

        then:
        factory.getOrCreateLocalNode(a).is(node)
    }

    void 'can add multiple nodes'() {
        when:
        def first = factory.getOrCreateLocalNode(a)
        def second = factory.getOrCreateLocalNode(b)

        then:
        first != second
    }

    void 'reset state'() {
        when:
        def first = factory.getOrCreateLocalNode(a)
        def second = factory.getOrCreateLocalNode(a)
        factory.discardAll()
        def afterReset = factory.getOrCreateLocalNode(a)

        then:
        second.is(first)
        !first.is(afterReset)
    }

}
