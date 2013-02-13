/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.Task
import spock.lang.Specification

class TaskDependencyGraphTest extends Specification {
    def graph = new TaskDependencyGraph()
    def a = Mock(Task)
    def b = Mock(Task)
    def c = Mock(Task)
    def d = Mock(Task)

    void 'adding nodes'() {
        when:
        graph.addNode(a)
        graph.addNode(b)

        then:
        graph.tasks == [a, b] as Set

        and:
        !graph.getNode(a).successors
        !graph.getNode(b).successors
    }

    void 'adding edges'() {
        when:
        graph.addEdge(a, b)
        graph.addEdge(a, c)

        then:
        graph.tasks == [a, b, c] as Set
        graph.getNode(a).successors*.task == [b, c]
        !graph.getNode(b).successors
        !graph.getNode(c).successors
    }

    void 'nodes without incoming edges'() {
        when:
        graph.addEdge(a, b)
        graph.addNode(c)

        then:
        graph.nodesWithoutIncomingEdges*.task == [a, c]

        when:
        graph.addEdge(d, a)

        then:
        graph.nodesWithoutIncomingEdges*.task == [c, d]
    }

    void 'clear'() {
        when:
        graph.addEdge(a, b)
        graph.addNode(c)
        graph.clear()

        then:
        !graph.tasks
        !graph.nodesWithoutIncomingEdges
    }

    void 'has task'() {
        when:
        graph.addEdge(a, b)
        graph.addNode(c)

        then:
        [a, b, c].every { graph.hasTask(it) }
    }
}
