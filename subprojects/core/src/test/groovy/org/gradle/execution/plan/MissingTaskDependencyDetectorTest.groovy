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

import com.google.common.collect.ImmutableSet
import org.gradle.internal.reflect.validation.TypeValidationContext
import spock.lang.Specification

class MissingTaskDependencyDetectorTest extends Specification {

    def outputHierarchy = Mock(ExecutionNodeAccessHierarchy)
    def inputHierarchy = Mock(ExecutionNodeAccessHierarchies.InputNodeAccessHierarchy)
    def detector = new MissingTaskDependencyDetector(outputHierarchy, inputHierarchy)
    def validationContext = Mock(TypeValidationContext)

    def "treats a direct task-in-another-build dependency on the producer as a declared dependency without searching the graph"() {
        given:
        def producer = Mock(LocalTaskNode)
        def otherBuildDependency = Mock(TaskInAnotherBuild) {
            getTargetNode() >> producer
        }
        def consumer = Mock(LocalTaskNode) {
            getDependencySuccessors() >> ImmutableSet.of(otherBuildDependency)
        }

        when:
        detector.visitUnfilteredInputLocation(consumer, validationContext, "location")

        then:
        1 * outputHierarchy.getNodesAccessing("location") >> ImmutableSet.of(producer)

        and: "no implicit dependency problem is reported"
        0 * validationContext.visitPropertyError(_)

        and: "the fast path short-cuts the breadth-first search"
        0 * consumer.getHardSuccessors()
    }

    def "treats a direct dependency on the producer as a declared dependency without searching the graph"() {
        given:
        def producer = Mock(LocalTaskNode)
        def consumer = Mock(LocalTaskNode) {
            getDependencySuccessors() >> ImmutableSet.of(producer)
        }

        when:
        detector.visitUnfilteredInputLocation(consumer, validationContext, "location")

        then:
        1 * outputHierarchy.getNodesAccessing("location") >> ImmutableSet.of(producer)

        and:
        0 * validationContext.visitPropertyError(_)

        and:
        0 * consumer.getHardSuccessors()
    }

    def "still finds a producer that is only reachable through the breadth-first search"() {
        given:
        def producer = Mock(LocalTaskNode)
        def intermediate = Mock(LocalTaskNode) {
            getHardSuccessors() >> [producer]
        }
        def consumer = Mock(LocalTaskNode) {
            getDependencySuccessors() >> ImmutableSet.of(intermediate)
        }

        when:
        detector.visitUnfilteredInputLocation(consumer, validationContext, "location")

        then:
        1 * outputHierarchy.getNodesAccessing("location") >> ImmutableSet.of(producer)

        and: "the fast path did not match, so the search was entered"
        1 * consumer.getHardSuccessors() >> [intermediate]

        and: "the dependency is found by the search, so no problem is reported"
        0 * validationContext.visitPropertyError(_)
    }
}
