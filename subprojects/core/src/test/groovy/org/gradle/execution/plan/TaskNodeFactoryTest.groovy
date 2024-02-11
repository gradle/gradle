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

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.composite.internal.BuildTreeWorkGraphController
import org.gradle.internal.operations.TestBuildOperationExecutor
import spock.lang.Specification

class TaskNodeFactoryTest extends Specification {
    def gradle = Stub(GradleInternal)
    def project = Stub(ProjectInternal)
    TaskNodeFactory factory
    def a = task('a')
    def b = task('b')
    def c = task('c')
    def d = task('d')
    def e = task('e')

    def setup() {
        project.gradle >> gradle
        project.pluginManager >> Stub(PluginManagerInternal)

        factory = new TaskNodeFactory(gradle, Stub(BuildTreeWorkGraphController), Stub(NodeValidator), new TestBuildOperationExecutor(), Stub(ExecutionNodeAccessHierarchies))
    }

    private TaskInternal task(String name) {
        Mock(TaskInternal) {
            getName() >> name
            compareTo(_) >> { args -> name.compareTo(args[0].name) }
            getProject() >> project
        }
    }

    void 'can create a node for a task'() {
        when:
        def node = factory.getOrCreateNode(a)

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
        def node = factory.getOrCreateNode(a)

        then:
        factory.getOrCreateNode(a).is(node)
    }

    void 'can add multiple nodes'() {
        when:
        factory.getOrCreateNode(a)
        factory.getOrCreateNode(b)

        then:
        factory.tasks == [a, b] as Set
    }

    void 'reset state'() {
        when:
        factory.getOrCreateNode(a)
        factory.getOrCreateNode(b)
        factory.getOrCreateNode(c)
        factory.resetState()

        then:
        !factory.tasks
    }
}
