/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.execution.plan.edges

import org.gradle.execution.plan.Node
import spock.lang.Specification

class NodeDependencySetTest extends Specification {
    def node = Stub(Node)
    def set = new DependencySuccessorsOnlyNodeSet()

    def "does not wait when the set is empty"() {
        expect:
        set.getState(node) == Node.DependenciesState.COMPLETE_AND_SUCCESSFUL
    }

    def "waits until all dependencies have completed successfully"() {
        def dep1 = Stub(Node)
        def dep2 = Stub(Node)

        given:
        _ * node.shouldContinueExecution(_) >> true

        when:
        set.addDependency(dep1)
        set.addDependency(dep2)

        then:
        set.getState(node) == Node.DependenciesState.NOT_COMPLETE

        when:
        set.onNodeComplete(node, dep1)

        then:
        set.getState(node) == Node.DependenciesState.NOT_COMPLETE

        when:
        set.onNodeComplete(node, dep2)

        then:
        set.getState(node) == Node.DependenciesState.COMPLETE_AND_SUCCESSFUL
    }

    def "waits until first failed dependency"() {
        def dep1 = Stub(Node)
        def dep2 = Stub(Node)

        given:
        _ * node.shouldContinueExecution(dep1) >> false
        _ * node.shouldContinueExecution(_) >> true

        when:
        set.addDependency(dep1)
        set.addDependency(dep2)

        then:
        set.getState(node) == Node.DependenciesState.NOT_COMPLETE

        when:
        set.onNodeComplete(node, dep1)

        then:
        set.getState(node) == Node.DependenciesState.COMPLETE_AND_NOT_SUCCESSFUL

        when:
        set.onNodeComplete(node, dep2)

        then:
        set.getState(node) == Node.DependenciesState.COMPLETE_AND_NOT_SUCCESSFUL
    }

    def "ignores nodes that are not members of the set"() {
        def dep1 = Stub(Node)
        def dep2 = Stub(Node)

        when:
        set.addDependency(dep1)
        set.addDependency(dep2)

        then:
        set.getState(node) == Node.DependenciesState.NOT_COMPLETE

        when:
        set.onNodeComplete(node, dep2)

        then:
        set.getState(node) == Node.DependenciesState.COMPLETE_AND_NOT_SUCCESSFUL
    }
}
