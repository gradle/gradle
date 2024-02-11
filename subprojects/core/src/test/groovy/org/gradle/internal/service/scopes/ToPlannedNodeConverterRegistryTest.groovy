/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.service.scopes

import org.gradle.execution.plan.Node
import org.gradle.execution.plan.TaskDependencyResolver
import org.gradle.execution.plan.TaskNode
import org.gradle.execution.plan.ToPlannedNodeConverter
import org.gradle.execution.plan.ToPlannedNodeConverterRegistry
import org.gradle.execution.plan.ToPlannedTaskConverter
import spock.lang.Specification

class ToPlannedNodeConverterRegistryTest extends Specification {

    def "can be empty"() {
        def registry = new ToPlannedNodeConverterRegistry([])
        expect:
        registry.getConverter(Stub(Node)) == null
    }

    def "finds converter matching type"() {
        def testConverter = Stub(ToPlannedNodeConverter) {
            getSupportedNodeType() >> TestNode
        }
        def registry = new ToPlannedNodeConverterRegistry([testConverter])

        when:
        def foundConverter = registry.getConverter(new TestNode())
        then:
        foundConverter.is(testConverter)

        // verify caching works
        when:
        foundConverter = registry.getConverter(new TestNode())
        then:
        foundConverter.is(testConverter)
    }

    def "finds converter for node of sub type"() {
        def testConverter = Stub(ToPlannedNodeConverter) {
            getSupportedNodeType() >> Node
        }
        def registry = new ToPlannedNodeConverterRegistry([testConverter])

        when:
        def foundConverter = registry.getConverter(new TestNode())
        then:
        foundConverter.is(testConverter)
    }

    def "does not find converter for node of parent type"() {
        def testConverter = Stub(ToPlannedNodeConverter) {
            getSupportedNodeType() >> TestNode
        }
        def registry = new ToPlannedNodeConverterRegistry([testConverter])

        when:
        def foundConverter = registry.getConverter(Stub(Node))
        then:
        foundConverter == null

        // Verify caching works
        when:
        foundConverter = registry.getConverter(Stub(Node))
        then:
        foundConverter == null
    }

    def "finds converter matching type among multiple"() {
        def testConverter = Stub(ToPlannedNodeConverter) {
            getSupportedNodeType() >> TestNode
        }
        def taskConverter = new ToPlannedTaskConverter()
        def registry = new ToPlannedNodeConverterRegistry([taskConverter, testConverter])

        when:
        def foundConverter = registry.getConverter(new TestNode())
        then:
        foundConverter.is(testConverter)

        when:
        foundConverter = registry.getConverter(Stub(TaskNode))
        then:
        foundConverter.is(taskConverter)
    }

    def "validates converters support disjoint set of types"() {
        def testConverter1 = Stub(ToPlannedNodeConverter) {
            getSupportedNodeType() >> TestNode
            toString() >> "testConverter1"
        }
        def testConverter2 = Stub(ToPlannedNodeConverter) {
            getSupportedNodeType() >> TestNode
            toString() >> "testConverter2"
        }

        when:
        new ToPlannedNodeConverterRegistry([testConverter1, testConverter2])
        then:
        def e = thrown(IllegalStateException)
        e.message == "Converter testConverter1 overlaps by supported node type with converter testConverter2"
    }

    static class TestNode extends Node {
        @Override
        Throwable getNodeFailure() {
            return null
        }

        @Override
        void resolveDependencies(TaskDependencyResolver dependencyResolver) {}

        @Override
        String toString() {
            "TestNode"
        }
    }
}
