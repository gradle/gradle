/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.tooling


import groovy.transform.CompileStatic
import groovy.transform.SelfType
import org.gradle.integtests.tooling.TestEventsFixture.TestEventSpec
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.test.TestMetadataEvent
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.events.test.TestOutputEvent
import org.gradle.util.GradleVersion

@SelfType(ToolingApiSpecification)
trait TestEventsFixture {
    abstract ProgressEvents getEvents()

    void testEvents(@DelegatesTo(value = TestEventsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> assertionSpec) {
        DefaultTestEventsSpec spec = new DefaultTestEventsSpec(events, targetDist.version.version)
        assertionSpec.delegate = spec
        assertionSpec.resolveStrategy = Closure.DELEGATE_FIRST
        assertionSpec()
        def remainingEvents = spec.testEvents - spec.verifiedEvents
        if (remainingEvents) {
            TestLauncherSpec.ErrorMessageBuilder err = new TestLauncherSpec.ErrorMessageBuilder()
            err.title("The following test events were received but not verified")
            remainingEvents.each { err.candidate("${it}: displayName=${it.displayName}") }
            throw err.build()
        }
    }

    /**
     * Spec builder for asserting that a test task emitted the appropriate events.
     */
    static interface TestEventsSpec {
        void task(String path, @DelegatesTo(value = RootTestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> rootSpec)
    }

    /**
     * Spec builder for asserting that a test task has a root test event with or without a tree of other test events.
     */
    static interface RootTestEventSpec extends TestEventSpec {
        /**
         * The name of the root test event.
         */
        void root(String name, @DelegatesTo(value = GroupTestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec)
    }

    /**
     * Spec builder for asserting that a test task has events from a group of tests or individual tests within the current group.
     */
    static interface GroupTestEventSpec extends TestEventSpec {
        /**
         * The name of the group test event.
         */
        void composite(String name, @DelegatesTo(value = GroupTestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec)

        /**
         * The name of the test in the test event.
         */
        void test(String name, @DelegatesTo(value = TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec)
    }

    /**
     * Expectations for a single test or group.
     */
    static interface TestEventSpec {
        /**
         * The display name to expect
         */
        void displayName(String displayName)

        /**
         * Some output to expect
         */
        void output(String msg)

        /**
         * Some metadata to expect
         */
        void metadata(String key, Object value)
    }

}

@CompileStatic
class DefaultTestEventsSpec implements TestEventsFixture.TestEventsSpec {
    final List<TestOperationDescriptor> testEvents
    final Set<OperationDescriptor> verifiedEvents = []
    final Map<TestOperationDescriptor, List<String>> outputByDescriptor
    final Map<TestOperationDescriptor, Map<String, Object>> metadataByDescriptor = [:]
    final String targetGradleVersion

    DefaultTestEventsSpec(ProgressEvents events, String targetGradleVersion) {
        this.targetGradleVersion = targetGradleVersion

        testEvents = events.tests.collect {(TestOperationDescriptor) it.descriptor }

        outputByDescriptor = events.getAll()
            .findAll { it instanceof TestOutputEvent }
            .collect { (TestOutputEvent) it }
            .groupBy {it.descriptor.parent }
            .collectEntries { entry ->
                [(entry.key): entry.value*.descriptor.message]
            }
        events.getAll()
            .findAll { it instanceof TestMetadataEvent }
            .collect { (TestMetadataEvent) it }.each {
                metadataByDescriptor.computeIfAbsent((TestOperationDescriptor)it.descriptor.parent) { [:] }.putAll(it.values)
            }
    }

    @Override
    void task(String path, @DelegatesTo(value = TestEventsFixture.RootTestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> rootSpec) {
        def task = testEvents.find {
            ((TaskOperationDescriptor) it.parent)?.taskPath == path
        }
        if (task == null) {
            throw new AssertionError((Object)"Expected to find a test task $path but none was found")
        }
        DefaultTestEventSpec.assertSpec(task.parent, testEvents, verifiedEvents, "Task $path", outputByDescriptor, metadataByDescriptor, targetGradleVersion, rootSpec)
    }
}

@CompileStatic
class DefaultTestEventSpec implements TestEventsFixture.GroupTestEventSpec, TestEventsFixture.RootTestEventSpec {
    private final List<TestOperationDescriptor> testEvents
    private final Set<OperationDescriptor> verifiedEvents
    private final OperationDescriptor parent
    private String testDisplayName
    private final Map<TestOperationDescriptor, List<String>> recordedOutputs = [:]
    private final Map<TestOperationDescriptor, List<String>> outputsToVerify = [:]
    private final Map<TestOperationDescriptor, Map<String, Object>> recordedMetadata = [:]
    private final Map<TestOperationDescriptor, Map<String, Object>> metadataToVerify = [:]
    private final String targetGradleVersion

    static void assertSpec(
        OperationDescriptor descriptor,
        List<TestOperationDescriptor> testEvents,
        Set<OperationDescriptor> verifiedEvents,
        String expectedOperationDisplayName,
        Map<TestOperationDescriptor, List<String>> recordedOutputs,
        Map<TestOperationDescriptor, Map<String, Object>> recordedMetadata,
        String targetGradleVersion,
        @DelegatesTo(value = TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec
    ) {
        verifiedEvents.add(descriptor)
        DefaultTestEventSpec childSpec = new DefaultTestEventSpec(descriptor, testEvents, verifiedEvents, recordedOutputs, recordedMetadata, targetGradleVersion)
        spec.delegate = childSpec
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
        childSpec.validate(expectedOperationDisplayName)
    }

    DefaultTestEventSpec(OperationDescriptor parent, List<TestOperationDescriptor> testEvents, Set<OperationDescriptor> verifiedEvents, Map<TestOperationDescriptor, List<String>> recordedOutputs, Map<TestOperationDescriptor, Map<String, Object>> recordedMetadata, String targetGradleVersion) {
        this.parent = parent
        this.testEvents = testEvents
        this.verifiedEvents = verifiedEvents
        this.recordedOutputs.putAll(recordedOutputs)
        this.recordedMetadata.putAll(recordedMetadata)
        this.targetGradleVersion = targetGradleVersion
    }

    @Override
    void displayName(String displayName) {
        this.testDisplayName = displayName
    }

    @Override
    void output(String msg) {
        def outputForDescriptor = this.outputsToVerify.computeIfAbsent((TestOperationDescriptor) this.parent) { [] }
        outputForDescriptor << msg
    }

    @Override
    void metadata(String key, Object value) {
        def metadataForDescriptor = this.metadataToVerify.computeIfAbsent((TestOperationDescriptor) this.parent) { [:] }
        metadataForDescriptor[key] = value
    }

    private static String normalizeExecutor(String name) {
        if (name.startsWith("Gradle Test Executor")) {
            return "Gradle Test Executor"
        }
        return name
    }

    @Override
    void root(String name, @DelegatesTo(value = TestEventsFixture.GroupTestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        def child = findChild(name)
        if (child == null) {
            failWith("composite test node", name)
        }
        assertSpec(child, testEvents, verifiedEvents, "Test suite '$name'", recordedOutputs, recordedMetadata, targetGradleVersion, spec)
    }

    @Override
    void composite(String name, @DelegatesTo(value = TestEventsFixture.GroupTestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        def child = findChild(name)
        if (child == null) {
            failWith("composite test node", name)
        }
        if (isGradleVersion813OrOlder()) {
            assertSpec(child, testEvents, verifiedEvents, "Test suite '$name'", recordedOutputs, recordedMetadata, targetGradleVersion, spec)
        } else {
            assertSpec(child, testEvents, verifiedEvents, "Test class $name", recordedOutputs, recordedMetadata, targetGradleVersion, spec)
        }
    }

    private boolean isGradleVersion813OrOlder() {
        GradleVersion.version(targetGradleVersion) <= GradleVersion.version("8.13")
    }

    private TestOperationDescriptor findChild(String name) {
        return testEvents.find {
            it.parent == parent && it.name == name
        }
    }

    @Override
    void test(String name, @DelegatesTo(value = TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        def child = findChild(name)
        if (child == null) {
            failWith("solitary test node", name)
        }
        assertSpec(child, testEvents, verifiedEvents, name, recordedOutputs, recordedMetadata, targetGradleVersion, spec)
    }

    private void failWith(String what, String name) {
        TestLauncherSpec.ErrorMessageBuilder err = new TestLauncherSpec.ErrorMessageBuilder()
        def remaining = testEvents.findAll { it.parent == parent && !verifiedEvents.contains(it) }
        if (remaining) {
            err.title("Expected to find a '$what' named '$name' under '${parent.displayName}' and none was found. Possible events are:")
            remaining.each {
                err.candidate("${it}: displayName=${it.displayName}")
            }
        } else {
            err.title("Expected to find a '$what' named '$name' under '${parent.displayName}' and none was found. There are no more events available for this parent.")
        }
        throw err.build()
    }

    void validate(String expectedDisplayName) {
        assert recordedMetadata[this.parent] == metadataToVerify[this.parent]
        assert recordedOutputs[this.parent] == outputsToVerify[this.parent]

        if (testDisplayName != null && parent.respondsTo("getTestDisplayName")) {
            assert testDisplayName == ((TestOperationDescriptor) parent).testDisplayName
            return
        }


        def actualDisplayName = normalizeExecutor(parent.displayName)
        assert expectedDisplayName == actualDisplayName
    }
}
