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

package org.gradle.api.internal.tasks.testing.detection.distribution

import org.gradle.api.internal.tasks.testing.TestDefinitionProcessor
import org.gradle.api.internal.tasks.testing.TestIdentifierTestDefinition
import org.junit.platform.engine.ConfigurationParameters
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.reporting.OutputDirectoryProvider
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path

/**
 * Unit tests for {@link ByTopLevelContainerTestDistributor}.
 */
class ByTopLevelContainerTestDistributorTest extends Specification {
    def processor = Mock(TestDefinitionProcessor)

    @Subject
    def distributor = new ByTopLevelContainerTestDistributor()

    def "distributes each top-level class as a separate unit"() {
        given:
        def testPlan = buildTestPlan {
            engine("junit-jupiter") {
                testClass("org.example.ClassA") {
                    testMethod("test1", "org.example.ClassA")
                    testMethod("test2", "org.example.ClassA")
                }
                testClass("org.example.ClassB") {
                    testMethod("test1", "org.example.ClassB")
                }
            }
        }

        when:
        def result = distributor.distribute(testPlan, processor)

        then:
        2 * processor.processTestDefinition({ it instanceof TestIdentifierTestDefinition })

        and:
        result.size() == 2
        result.every { it[0] instanceof TestIdentifierTestDefinition }
    }

    def "nested class is not distributed separately from outer class"() {
        given:
        def testPlan = buildTestPlan {
            engine("junit-jupiter") {
                testClass("org.example.Outer") {
                    testMethod("outerTest", "org.example.Outer")
                    testClass("org.example.Outer\$Inner") {
                        testMethod("innerTest", "org.example.Outer\$Inner")
                    }
                }
            }
        }

        when:
        def result = distributor.distribute(testPlan, processor)

        then: 'only the outer class is distributed'
        1 * processor.processTestDefinition({ it instanceof TestIdentifierTestDefinition })

        and:
        result.size() == 1
        (result[0][0] as TestIdentifierTestDefinition).className == "org.example.Outer"
    }

    def "class with only parameterized methods is distributed as a single unit"() {
        given:
        def testPlan = buildTestPlan {
            engine("junit-jupiter") {
                testClass("org.example.ParamTest") {
                    container("paramMethod")  // CONTAINER with no children at discovery
                }
            }
        }

        when:
        def result = distributor.distribute(testPlan, processor)

        then:
        1 * processor.processTestDefinition({ it instanceof TestIdentifierTestDefinition })

        and:
        result.size() == 1
    }

    def "empty test plan produces no distributions"() {
        given:
        def testPlan = buildTestPlan {}

        when:
        def result = distributor.distribute(testPlan, processor)

        then:
        0 * processor.processTestDefinition(_)

        and:
        result.isEmpty()
    }

    def "methods from different classes are never grouped together"() {
        given:
        def testPlan = buildTestPlan {
            engine("junit-jupiter") {
                testClass("org.example.A") {
                    testMethod("test", "org.example.A")
                }
                testClass("org.example.B") {
                    testMethod("test", "org.example.B")
                }
                testClass("org.example.C") {
                    testMethod("test", "org.example.C")
                }
            }
        }

        when:
        def result = distributor.distribute(testPlan, processor)

        then: 'each class is its own distribution group'
        result.size() == 3
        result.every { it.size() == 1 }

        and: 'each group contains a different class'
        def classNames = result.collect { (it[0] as TestIdentifierTestDefinition).className } as Set
        classNames == ["org.example.A", "org.example.B", "org.example.C"] as Set
    }

    // region isTopLevelContainer
    def "identifier with ClassSource and non-class parent is a top-level class container"() {
        given:
        def testPlan = buildTestPlan {
            engine("junit-jupiter") {
                testClass("org.example.MyTest") {
                    testMethod("test1", "org.example.MyTest")
                }
            }
        }
        def classId = findByClassName(testPlan, "org.example.MyTest")

        expect:
        ByTopLevelContainerTestDistributor.isTopLevelContainer(testPlan, classId)
    }

    def "identifier with MethodSource is not a top-level class container"() {
        given:
        def testPlan = buildTestPlan {
            engine("junit-jupiter") {
                testClass("org.example.MyTest") {
                    testMethod("test1", "org.example.MyTest")
                }
            }
        }
        def methodId = findByDisplayName(testPlan, "test1()")

        expect:
        !ByTopLevelContainerTestDistributor.isTopLevelContainer(testPlan, methodId)
    }

    def "nested class with ClassSource parent is not a top-level class container"() {
        given:
        def testPlan = buildTestPlan {
            engine("junit-jupiter") {
                testClass("org.example.Outer") {
                    testClass("org.example.Outer\$Inner") {
                        testMethod("innerTest", "org.example.Outer\$Inner")
                    }
                }
            }
        }
        def nestedId = findByClassName(testPlan, "org.example.Outer\$Inner")

        expect:
        !ByTopLevelContainerTestDistributor.isTopLevelContainer(testPlan, nestedId)
    }

    def "engine root is not a top-level class container"() {
        given:
        def testPlan = buildTestPlan {
            engine("junit-jupiter") {}
        }
        def engineId = testPlan.roots.first()

        expect:
        !ByTopLevelContainerTestDistributor.isTopLevelContainer(testPlan, engineId)
    }

    def "container without source is not a top-level class container"() {
        given:
        def testPlan = buildTestPlan {
            engine("junit-jupiter") {
                container("mystery")
            }
        }
        def containerId = findByDisplayName(testPlan, "mystery")

        expect:
        !ByTopLevelContainerTestDistributor.isTopLevelContainer(testPlan, containerId)
    }
    // endregion isTopLevelContainer

    // region Test plan builder helpers
    private static TestPlan buildTestPlan(@DelegatesTo(EngineBuilder) Closure config) {
        def builder = new EngineBuilder()
        config.delegate = builder
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()
        return TestPlan.from(builder.engines, emptyConfigParams(), emptyOutputDirProvider())
    }

    private static TestIdentifier findByClassName(TestPlan plan, String className) {
        for (def root : plan.roots) {
            def found = findInDescendants(plan, root, { id ->
                id.source.orElse(null) instanceof ClassSource &&
                    (id.source.get() as ClassSource).className == className
            })
            if (found != null) {
                return found
            }
        }
        throw new IllegalArgumentException("No TestIdentifier found for class: ${className}")
    }

    private static TestIdentifier findByDisplayName(TestPlan plan, String displayName) {
        for (def root : plan.roots) {
            def found = findInDescendants(plan, root, { it.displayName == displayName })
            if (found != null) {
                return found
            }
        }
        throw new IllegalArgumentException("No TestIdentifier found with displayName: ${displayName}")
    }

    private static TestIdentifier findInDescendants(TestPlan plan, TestIdentifier node, Closure<Boolean> predicate) {
        if (predicate(node)) {
            return node
        }
        for (def child : plan.getChildren(node)) {
            def found = findInDescendants(plan, child, predicate)
            if (found != null) {
                return found
            }
        }
        return null
    }

    private static ConfigurationParameters emptyConfigParams() {
        return new ConfigurationParameters() {
            Optional<String> get(String key) { Optional.empty() }
            Optional<Boolean> getBoolean(String key) { Optional.empty() }
            @SuppressWarnings("deprecation")
            int size() { 0 }
            Set<String> keySet() { Collections.emptySet() }
        }
    }

    private static OutputDirectoryProvider emptyOutputDirProvider() {
        return new OutputDirectoryProvider() {
            Path getRootDirectory() { Path.of("/tmp/test-output") }
            Path createOutputDirectory(TestDescriptor descriptor) { Path.of("/tmp/test-output") }
        }
    }

    private static class EngineBuilder {
        List<TestDescriptor> engines = []

        void engine(String name, @DelegatesTo(DescriptorBuilder) Closure config) {
            def engineId = UniqueId.root("engine", name)
            def engineDescriptor = new SimpleDescriptor(engineId, name, null, TestDescriptor.Type.CONTAINER)
            def builder = new DescriptorBuilder(engineDescriptor)
            config.delegate = builder
            config.resolveStrategy = Closure.DELEGATE_FIRST
            config.call()
            engines << engineDescriptor
        }
    }

    private static class DescriptorBuilder {
        final TestDescriptor parent

        DescriptorBuilder(TestDescriptor parent) {
            this.parent = parent
        }

        void testClass(String className, @DelegatesTo(DescriptorBuilder) Closure config = {}) {
            def id = parent.uniqueId.append("class", className)
            def descriptor = new SimpleDescriptor(id, className, ClassSource.from(className), TestDescriptor.Type.CONTAINER)
            parent.addChild(descriptor)
            def builder = new DescriptorBuilder(descriptor)
            config.delegate = builder
            config.resolveStrategy = Closure.DELEGATE_FIRST
            config.call()
        }

        void testMethod(String methodName, String className) {
            def id = parent.uniqueId.append("method", "${methodName}()")
            def descriptor = new SimpleDescriptor(id, "${methodName}()", MethodSource.from(className, methodName), TestDescriptor.Type.TEST)
            parent.addChild(descriptor)
        }

        void container(String name) {
            def id = parent.uniqueId.append("container", name)
            def descriptor = new SimpleDescriptor(id, name, null, TestDescriptor.Type.CONTAINER)
            parent.addChild(descriptor)
        }
    }

    private static class SimpleDescriptor extends AbstractTestDescriptor {
        private final Type type

        SimpleDescriptor(UniqueId uniqueId, String displayName, TestSource source, Type type) {
            super(uniqueId, displayName, source)
            this.type = type
        }

        @Override
        Type getType() {
            return type
        }
    }
    // endregion Test plan builder helpers
}
