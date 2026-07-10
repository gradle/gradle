/*
 * Copyright 2025 the original author or authors.
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

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString
import org.apache.commons.lang3.NotImplementedException
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.WorkNodeAction
import org.gradle.composite.internal.IncludedBuildTaskResource
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.util.Path
import spock.lang.Specification

import static java.lang.System.identityHashCode
import static org.gradle.execution.plan.OrdinalNode.Type.DESTROYER
import static org.gradle.execution.plan.OrdinalNode.Type.PRODUCER

class NodeComparatorTest extends Specification {

    List<Node> expected

    def setup() {
        // ActionNode instances are ordered by identity hash code
        def a1 = createActionNode()
        def a2 = createActionNode()
        def (firstAction, secondAction) = identityHashCode(a1) < identityHashCode(a2)
            ? [a1, a2]
            : [a2, a1]
        expected = [
            createOrdinalNode(1, DESTROYER),
            createOrdinalNode(1, PRODUCER),
            createOrdinalNode(2, DESTROYER),
            createOrdinalNode(2, PRODUCER),
            createResolveMutationsNode(1),
            createResolveMutationsNode(2),
            createCreationOrderedNode(),
            createCreationOrderedNode(),
            createLocalTaskNode(1),
            createLocalTaskNode(2),
            firstAction,
            secondAction,
            createTaskInAnotherBuild(1),
            createTaskInAnotherBuild(2),
        ].asImmutable()
    }

    private static int compare(Node x, Node y) {
        NodeComparator.INSTANCE.compare(x, y)
    }

    def "all elements are mutually comparable"() {
        expect:
        expected.forEach { x ->
            expected.forEach { y ->
                assert compare(x, y) == -compare(y, x)
            }
        }
    }

    def "can be used with TreeSet"() {
        expect:
        withRandomPermutation {
            def tree = new TreeSet<>(NodeComparator.INSTANCE)
            assert tree.addAll(it)
            assert !tree.addAll(it.asReversed())
            assertIdentities(expected, tree.toList())
        }
    }

    def "can be used with sort"() {
        expect:
        withRandomPermutation {
            it.sort(NodeComparator.INSTANCE)
            assertIdentities(expected, it)
        }
    }

    void assertIdentities(List<Node> xs, List<Node> ys) {
        assert xs.size() == ys.size()
        xs.eachWithIndex { Node x, int i ->
            assert x === ys[i], "${xs[i]} !== ${ys[i]}"
        }
    }

    void withRandomPermutation(
        @ClosureParams(value = FromString, options = ["List<org.gradle.execution.plan.Node>"]) Closure test
    ) {
        1000.times {
            test(expected.shuffled())
        }
    }

    protected OrdinalNode createOrdinalNode(int ordinal, OrdinalNode.Type producer) {
        return new OrdinalNode(producer, new OrdinalGroup(ordinal, null))
    }

    protected ResolveMutationsNode createResolveMutationsNode(int index) {
        return new ResolveMutationsNode(
            createLocalTaskNode(index),
            Mock(NodeValidator),
            Mock(BuildOperationRunner),
            Mock(ExecutionNodeAccessHierarchies)
        )
    }

    protected CreationOrderedNode createCreationOrderedNode() {
        return new CreationOrderedNode() {
            @Override
            Throwable getNodeFailure() {
                throw new NotImplementedException()
            }

            @Override
            void resolveDependencies(TaskDependencyResolver dependencyResolver) {
                throw new NotImplementedException()
            }

            @Override
            String toString() {
                "CreationOrderedNode($order)"
            }
        }
    }

    protected ActionNode createActionNode() {
        return new ActionNode(Mock(WorkNodeAction) {
            getOwningProject() >> {
                null
            }
            _ >> { args ->
                throw new RuntimeException("Unexpected method invocation with args: ${args}")
            }
        })
    }

    protected TaskInAnotherBuild createTaskInAnotherBuild(int index) {
        return new TaskInAnotherBuild(path(index), null, null) {
            @Override
            protected IncludedBuildTaskResource getTarget() {
                throw new NotImplementedException()
            }
        }
    }

    protected LocalTaskNode createLocalTaskNode(int index) {
        def path = path(index)
        def task = Mock(TaskInternal) {
            getIdentityPath() >> path
            _ * getPath() >> path
            _ * compareTo(_) >> { TaskInternal other -> (it.getPath() <=> other.getPath()) }
            getProject() >> null
            _ >> { args ->
                throw new RuntimeException("Unexpected method invocation with args: ${args}")
            }
        }

        return new LocalTaskNode(task, null, {})
    }

    private static Path path(int index) {
        Path.path(index.toString())
    }
}
