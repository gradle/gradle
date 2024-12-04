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
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.test.TestMetadataEvent
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.events.test.TestOutputEvent

@CompileStatic
trait TestEventsFixture {
    abstract ProgressEvents getEvents()

    void testEvents(@DelegatesTo(value = TestEventsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> assertionSpec) {
        DefaultTestEventsSpec spec = new DefaultTestEventsSpec(events)
        assertionSpec.delegate = spec
        assertionSpec.resolveStrategy = Closure.DELEGATE_FIRST
        assertionSpec()
        def remainingEvents = spec.testEvents - spec.verifiedEvents
        if (remainingEvents) {
            TestLauncherSpec.ErrorMessageBuilder err = new TestLauncherSpec.ErrorMessageBuilder()
            err.title("The following test events were received but not verified")
            remainingEvents.each { err.candidate("${it}: displayName=${it.descriptor.displayName}") }
            throw err.build()
        }
    }

    static interface TestEventsSpec {
        void task(String path, @DelegatesTo(value = CompositeTestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> rootSpec)
    }

    static interface TestEventSpec {
        void testDisplayName(String displayName)
        void output(String msg)
        void metadata(String key, Object value)
    }

    static interface CompositeTestEventSpec extends TestEventSpec {
        void composite(String name, @DelegatesTo(value = CompositeTestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec)

        void test(String name, @DelegatesTo(value = TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec)
    }
}

@CompileStatic
class DefaultTestEventsSpec implements TestEventsFixture.TestEventsSpec {
    final List<ProgressEvents.Operation> testEvents
    final Set<ProgressEvents.Operation> verifiedEvents = []

    DefaultTestEventsSpec(ProgressEvents events) {
        testEvents = events.tests
    }

    @Override
    void task(String path, @DelegatesTo(value = TestEventsFixture.TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> rootSpec) {
        def task = testEvents.find {
            ((TaskOperationDescriptor) it.descriptor.parent)?.taskPath == path
        }
        if (task == null) {
            throw new AssertionError((Object)"Expected to find a test task $path but none was found")
        }
        DefaultTestEventSpec.assertSpec(task.parent, testEvents, verifiedEvents, "Task $path", rootSpec)
    }
}

@CompileStatic
class DefaultTestEventSpec implements TestEventsFixture.CompositeTestEventSpec {
    private final List<ProgressEvents.Operation> testEvents
    private final Set<ProgressEvents.Operation> verifiedEvents
    private final ProgressEvents.Operation parent
    private String testDisplayName
    private final List<String> expectedOutput = []
    private final Map<String, Object> expectedMetadata = [:]

    static void assertSpec(
        ProgressEvents.Operation parentOperation,
        List<ProgressEvents.Operation> testEvents,
        Set<ProgressEvents.Operation> verifiedEvents,
        String expectedOperationDisplayName,
        @DelegatesTo(value = TestEventsFixture.TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec
    ) {
        verifiedEvents.add(parentOperation)
        DefaultTestEventSpec childSpec = new DefaultTestEventSpec(parentOperation, testEvents, verifiedEvents)
        spec.delegate = childSpec
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
        childSpec.validate(expectedOperationDisplayName)
    }

    DefaultTestEventSpec(ProgressEvents.Operation parent, List<ProgressEvents.Operation> testEvents, Set<ProgressEvents.Operation> verifiedEvents) {
        this.parent = parent
        this.testEvents = testEvents
        this.verifiedEvents = verifiedEvents
    }

    @Override
    void testDisplayName(String displayName) {
        this.testDisplayName = displayName
    }

    @Override
    void output(String msg) {
        expectedOutput << msg
    }

    @Override
    void metadata(String key, Object value) {
        expectedMetadata[key] = value
    }

    private static String normalizeExecutor(String name) {
        if (name.startsWith("Gradle Test Executor")) {
            return "Gradle Test Executor"
        }
        return name
    }

    @Override
    void composite(String name, @DelegatesTo(value = TestEventsFixture.CompositeTestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        def child = testEvents.find {
            it.parent == parent && it.descriptor.name == name
        }
        if (child == null) {
            failWith("composite test node", name)
        }
        assertSpec(child, testEvents, verifiedEvents, "Test suite '$name'", spec)
    }

    @Override
    void test(String name, @DelegatesTo(value = TestEventsFixture.TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        def child = testEvents.find {
            it.parent == parent && it.descriptor.name == name
        }
        if (child == null) {
            failWith("solitary test node", name)
        }
        assertSpec(child, testEvents, verifiedEvents, name, spec)
    }

    private void failWith(String what, String name) {
        TestLauncherSpec.ErrorMessageBuilder err = new TestLauncherSpec.ErrorMessageBuilder()
        def remaining = testEvents.findAll { it.parent == parent && !verifiedEvents.contains(it) }
        if (remaining) {
            err.title("Expected to find a '$what' named '$name' under '${parent.descriptor.displayName}' and none was found. Possible events are:")
            remaining.each {
                err.candidate("${it}: displayName=${it.descriptor.displayName}")
            }
        } else {
            err.title("Expected to find a '$what' named '$name' under '${parent.descriptor.displayName}' and none was found. There are no more events available for this parent.")
        }
        throw err.build()
    }

    void validate(String expectedOperationDisplayName) {
        def actualOutputs = parent.children.findAll { it.startEvent instanceof TestOutputEvent }.collect { ((TestOutputEvent)it.startEvent).descriptor.message }
        assert actualOutputs == expectedOutput

        def actualMetadata = parent.children.findAll { it.startEvent instanceof TestMetadataEvent }
            .collect { ((TestMetadataEvent)it.startEvent) }
            .collectEntries { [it.key, it.value] }
        assert actualMetadata == expectedMetadata

        if (testDisplayName != null && parent.descriptor.respondsTo("getTestDisplayName")) {
            assert testDisplayName == ((TestOperationDescriptor) parent.descriptor).testDisplayName
            return
        }

        assert expectedOperationDisplayName == normalizeExecutor(parent.descriptor.displayName)
    }
}
