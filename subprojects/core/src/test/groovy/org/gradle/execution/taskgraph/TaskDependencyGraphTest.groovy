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

package org.gradle.execution.taskgraph

import org.gradle.api.internal.TaskInternal
import spock.lang.Specification

class TaskDependencyGraphTest extends Specification {
    def graph = new TaskDependencyGraph()
    def a = task('a')
    def b = task('b')
    def c = task('c')
    def d = task('d')

    private TaskInternal task(String name) {
        Mock(TaskInternal) {
            getName() >> name
            compareTo(_) >> { args -> name.compareTo(args[0].name)}
        }
    }

    void 'adding nodes'() {
        when:
        def node = graph.addNode(a)

        then:
        graph.getNode(a).is(node)
        graph.addNode(a).is(node)
        !node.required
        node.softSuccessors.empty
        node.hardSuccessors.empty
    }

    void 'can add multiple nodes'() {
        when:
        graph.addNode(a)
        graph.addNode(b)

        then:
        graph.tasks == [a, b] as Set
    }

    void 'adding hard edges'() {
        when:
        def nodeA = graph.addNode(a)
        graph.addHardEdge(nodeA, c)
        graph.addHardEdge(nodeA, b)

        then:
        with graph, {
            tasks == [a, c, b] as Set
            getNode(a).hardSuccessors*.task == [b, c]
            [b, c].every { !getNode(it).hardSuccessors }
            [a, b, c].every { !getNode(it).softSuccessors }
        }
    }

    void 'adding soft edges'() {
        when:
        def nodeA = graph.addNode(a)
        graph.addSoftEdge(nodeA, c)
        graph.addSoftEdge(nodeA, b)

        then:
        with graph, {
            tasks == [a, c, b] as Set
            getNode(a).softSuccessors*.task == [b, c]
            [b, c].every { !getNode(it).softSuccessors }
            [a, b, c].every { !getNode(it).hardSuccessors }
        }
    }

    void 'clear'() {
        when:
        def nodeA = graph.addNode(a)
        graph.addHardEdge(nodeA, b)
        graph.addNode(c)
        graph.clear()

        then:
        !graph.tasks
    }

    void 'has task'() {
        when:
        def nodeA = graph.addNode(a)
        graph.addHardEdge(nodeA, b)
        graph.addNode(c)

        then:
        [a, b, c].every { graph.hasTask(it) }
    }
}
