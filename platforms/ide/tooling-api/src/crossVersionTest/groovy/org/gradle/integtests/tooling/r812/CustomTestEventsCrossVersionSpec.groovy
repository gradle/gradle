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

package org.gradle.integtests.tooling.r812

import groovy.transform.CompileStatic
import org.gradle.integtests.tooling.TestLauncherSpec
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.TestLauncher
import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.test.TestOperationDescriptor

@ToolingApiVersion('>=8.8')
@TargetGradleVersion(">=8.12")
class CustomTestEventsCrossVersionSpec extends TestLauncherSpec {
    @Override
    void addDefaultTests() {
    }

    @Override
    String simpleJavaProject() {
        ""
    }

    def "reports custom test events"() {
        given:
        if (!buildFile.delete()) {
            throw new RuntimeException("Could not delete build file")
        }
        buildFile("""
            import java.time.Instant

            abstract class CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventService getTestEventService()

                @TaskAction
                void runTests() {
                   try (def generator = getTestEventService().generateTestEvents("Custom test root")) {
                       generator.started(Instant.now())
                       try (def mySuite = generator.createCompositeNode("My Suite")) {
                            mySuite.started(Instant.now())
                            try (def myTest = mySuite.createAtomicNode("MyTestInternal", "My test!")) {
                                 myTest.started(Instant.now())
                                 myTest.output(TestOutputEvent.Destination.StdOut, "This is a test output on stdout")
                                 myTest.output(TestOutputEvent.Destination.StdErr, "This is a test output on stderr")
                                 myTest.completed(Instant.now(), TestResult.ResultType.SUCCESS)
                            }
                            mySuite.completed(Instant.now(), TestResult.ResultType.SUCCESS)
                       }
                       generator.completed(Instant.now(), TestResult.ResultType.SUCCESS)
                   }
                }
            }

            tasks.register("customTest", CustomTestTask)
        """)

        when:
        launchTests { TestLauncher launcher ->
            launcher.withTaskAndTestClasses(':customTest', ['no idea what to do with this yet'])
        }

        then:
        testEvents {
            task(":customTest") {
                composite("Custom test root") {
                    composite("My Suite") {
                        test("MyTestInternal") {
                            testDisplayName "My test!"
                        }
                    }
                }
            }
        }
    }

    void testEvents(@DelegatesTo(value = TestEventsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> assertionSpec) {
        DefaultTestEventsSpec spec = new DefaultTestEventsSpec()
        assertionSpec.delegate = spec
        assertionSpec.resolveStrategy = Closure.DELEGATE_FIRST
        assertionSpec()
        def remainingEvents = spec.testEvents - spec.verifiedEvents
        if (remainingEvents) {
            ErrorMessageBuilder err = new ErrorMessageBuilder()
            err.title("The following test events were received but not verified")
            remainingEvents.each { err.candidate("${it} : Kind=${it.jvmTestKind} suiteName=${it.suiteName} className=${it.className} methodName=${it.methodName} displayName=${it.displayName}") }
            throw err.build()
        }
    }

    static interface TestEventsSpec {
        void task(String path, @DelegatesTo(value = CompositeTestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> rootSpec)
    }

    static interface TestEventSpec {
        void testDisplayName(String displayName)
    }

    static interface CompositeTestEventSpec extends TestEventSpec {
        void composite(String name, @DelegatesTo(value = CompositeTestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec)

        void test(String name, @DelegatesTo(value = TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec)
    }

    class DefaultTestEventsSpec implements TestEventsSpec {
        final List<TestOperationDescriptor> testEvents = events.tests.collect {(TestOperationDescriptor) it.descriptor }
        final Set<OperationDescriptor> verifiedEvents = []

        @Override
        void task(String path, @DelegatesTo(value = TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> rootSpec) {
            println(events.tests)
            def task = testEvents.find {
                ((TaskOperationDescriptor) it.parent)?.taskPath == path
            }
            if (task == null) {
                throw new AssertionError("Expected to find a test task $path but none was found")
            }
            DefaultTestEventSpec.assertSpec(task.parent, testEvents, verifiedEvents, "Task $path", rootSpec)
        }
    }

    @CompileStatic
    static class DefaultTestEventSpec implements CompositeTestEventSpec {
        private final List<TestOperationDescriptor> testEvents
        private final Set<OperationDescriptor> verifiedEvents
        private final OperationDescriptor parent
        private String testDisplayName

        static void assertSpec(OperationDescriptor descriptor, List<TestOperationDescriptor> testEvents, Set<OperationDescriptor> verifiedEvents, String expectedOperationDisplayName, @DelegatesTo(value = TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
            verifiedEvents.add(descriptor)
            DefaultTestEventSpec childSpec = new DefaultTestEventSpec(descriptor, testEvents, verifiedEvents)
            spec.delegate = childSpec
            spec.resolveStrategy = Closure.DELEGATE_FIRST
            spec()
            childSpec.validate(expectedOperationDisplayName)
        }

        DefaultTestEventSpec(OperationDescriptor parent, List<TestOperationDescriptor> testEvents, Set<OperationDescriptor> verifiedEvents) {
            this.parent = parent
            this.testEvents = testEvents
            this.verifiedEvents = verifiedEvents
        }

        @Override
        void testDisplayName(String displayName) {
            this.testDisplayName = displayName
        }

        private static String normalizeExecutor(String name) {
            if (name.startsWith("Gradle Test Executor")) {
                return "Gradle Test Executor"
            }
            return name
        }

        @Override
        void composite(String name, @DelegatesTo(value = CompositeTestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
            def child = testEvents.find {
                it.parent == parent && it.name == name
            }
            if (child == null) {
                failWith("composite test node", name)
            }
            assertSpec(child, testEvents, verifiedEvents, "Test suite '$name'", spec)
        }

        @Override
        void test(String name, @DelegatesTo(value = TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
            def child = testEvents.find {
                it.parent == parent && it.name == name
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
                err.title("Expected to find a '$what' named '$name' under '${parent.displayName}' and none was found. Possible events are:")
                remaining.each {
                    err.candidate("${it}: displayName=${it.displayName}")
                }
            } else {
                err.title("Expected to find a '$what' named '$name' under '${parent.displayName}' and none was found. There are no more events available for this parent.")
            }
            throw err.build()
        }

        void validate(String expectedOperationDisplayName) {
            if (testDisplayName != null && parent.respondsTo("getTestDisplayName")) {
                assert testDisplayName == ((TestOperationDescriptor) parent).testDisplayName
                return
            }
            assert expectedOperationDisplayName == normalizeExecutor(parent.displayName)
        }
    }
}
