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

package org.gradle.execution.plan

import spock.lang.Issue
import spock.lang.Specification

class MissingTaskDependencyDetectorTest extends Specification {

    // How often the search asked each node for its successors, by node name
    Map<String, Integer> expansions = [:].withDefault { 0 }

    def "a node runs after its direct and transitive dependencies"() {
        given:
        def transitive = node("transitive")
        def direct = node("direct", transitive)
        def search = new MissingTaskDependencyDetector.DependencySearch(node("node", direct))

        expect:
        search.runsAfter(direct)
        search.runsAfter(transitive)
    }

    def "a node does not run after nodes that are not its dependencies"() {
        given:
        def dependency = node("dependency")
        def start = node("node", dependency)
        // `other` depends on `start`, but `start` does not depend on `other`
        def other = node("other", start)
        def search = new MissingTaskDependencyDetector.DependencySearch(start)

        expect:
        !search.runsAfter(other)
        search.runsAfter(dependency)
    }

    def "a node counts as running after itself"() {
        given:
        def start = node("node")
        def search = new MissingTaskDependencyDetector.DependencySearch(start)

        expect:
        search.runsAfter(start)
        expansions == [:]
    }

    @Issue("https://github.com/gradle/gradle/issues/39729")
    def "search follows a reference to a task in another build to its target"() {
        given:
        def transitive = node("transitive")
        def target = node("target", transitive)
        def search = new MissingTaskDependencyDetector.DependencySearch(node("node", inAnotherBuild(target)))

        expect:
        search.runsAfter(target)
        search.runsAfter(transitive)
    }

    @Issue("https://github.com/gradle/gradle/issues/39729")
    def "search expands each node at most once across all queries"() {
        given:
        def first = node("first")
        def second = node("second")
        // Two paths lead to the shared node
        def shared = node("shared", first, second)
        def left = node("left", shared)
        def right = node("right", shared)
        def other = node("other")
        def search = new MissingTaskDependencyDetector.DependencySearch(node("node", left, inAnotherBuild(right)))

        when:
        def answers = (1..10).collect { [first, second, shared, left, right, other].collect { search.runsAfter(it) } }

        then:
        answers.every { it == [true, true, true, true, true, false] }
        expansions == [node: 1, left: 1, right: 1, shared: 1, first: 1, second: 1]
    }

    def "search only expands nodes until it finds the dependency"() {
        given:
        def transitive = node("transitive")
        def direct = node("direct", transitive)
        def search = new MissingTaskDependencyDetector.DependencySearch(node("node", direct))

        expect:
        search.runsAfter(direct)
        expansions == [node: 1]
    }

    private TaskNode node(String name, Node... successors) {
        return Stub(TaskNode) {
            getHardSuccessors() >> {
                expansions[name] = expansions[name] + 1
                return successors.toList()
            }
        }
    }

    private TaskInAnotherBuild inAnotherBuild(TaskNode target) {
        return Stub(TaskInAnotherBuild) {
            getTargetNode() >> target
        }
    }

}
