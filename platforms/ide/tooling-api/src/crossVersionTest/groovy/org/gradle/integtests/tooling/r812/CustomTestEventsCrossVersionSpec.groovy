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

import org.gradle.integtests.tooling.TestEventsFixture
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.util.GradleVersion

// Proper test display names were implemented in Gradle 8.8
@ToolingApiVersion(">=8.8")
@TargetGradleVersion(">=8.13")
class CustomTestEventsCrossVersionSpec extends ToolingApiSpecification implements TestEventsFixture {
    ProgressEvents events = ProgressEvents.create()

    @Override
    ProgressEvents getEvents() {
        return events
    }

    /**
     * Between 8.13 and 8.14, the display name changed for test events emitted by the TestEventReporter.
     */
    private String if813OrOlderTestDisplayName() {
        if813OrOlder("Test MyTestInternal(MyTestInternal)", "My test!")
    }

    private String if813OrOlder(String value813, String otherwise) {
        if (targetVersion < GradleVersion.version("8.14")) {
            value813
        } else {
            otherwise
        }
    }

    def "reports custom test events (flat)"() {
        given:
        buildFile("""
            import java.time.Instant

            abstract class CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @Inject
                abstract ProjectLayout getLayout()

                @TaskAction
                void runTests() {
                    try (def reporter = testEventReporterFactory.createTestEventReporter(
                        "Custom test root",
                        getLayout().getBuildDirectory().dir("test-results/Custom test root").get(),
                        getLayout().getBuildDirectory().dir("reports/tests/Custom test root").get()
                    )) {
                        reporter.started(Instant.now())
                        try (def myTest = reporter.reportTest("MyTestInternal", "My test!")) {
                            myTest.started(Instant.now())
                            myTest.output(Instant.now(), TestOutputEvent.Destination.StdOut, "This is a test output on stdout")
                            myTest.output(Instant.now(), TestOutputEvent.Destination.StdErr, "This is a test output on stderr")
                            myTest.succeeded(Instant.now())
                        }
                        reporter.succeeded(Instant.now())
                    }
                }
            }

            tasks.register("customTest", CustomTestTask)
        """)

        when:
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .addProgressListener(events, OperationType.TASK, OperationType.TEST)
                    .forTasks('customTest')
                    .run()
        }

        then:
        testEvents {
            task(":customTest") {
                nested("Test suite 'Custom test root'") {
                    test(if813OrOlderTestDisplayName())
                }
            }
        }
    }

    def "reports custom test events (JUnit-like)"() {
        given:
        buildFile("""
            import java.time.Instant

            abstract class CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @Inject
                abstract ProjectLayout getLayout()

                @TaskAction
                void runTests() {
                    try (def reporter = testEventReporterFactory.createTestEventReporter(
                        "Custom test root",
                        getLayout().getBuildDirectory().dir("test-results/Custom test root").get(),
                        getLayout().getBuildDirectory().dir("reports/tests/Custom test root").get()
                    )) {
                        reporter.started(Instant.now())
                        try (def mySuite = reporter.reportTestGroup("My Suite")) {
                            mySuite.started(Instant.now())
                            try (def myTest = mySuite.reportTest("MyTestInternal", "My test!")) {
                                 myTest.started(Instant.now())
                                 myTest.output(Instant.now(), TestOutputEvent.Destination.StdOut, "This is a test output on stdout")
                                 myTest.output(Instant.now(), TestOutputEvent.Destination.StdErr, "This is a test output on stderr")
                                 myTest.succeeded(Instant.now())
                            }
                            mySuite.succeeded(Instant.now())
                        }
                        reporter.succeeded(Instant.now())
                    }
                }
            }

            tasks.register("customTest", CustomTestTask)
        """)

        when:
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .addProgressListener(events, OperationType.TASK, OperationType.TEST)
                    .forTasks('customTest')
                    .run()
        }

        then:
        testEvents {
            task(":customTest") {
                nested("Test suite 'Custom test root'") {
                    nested(if813OrOlder("Test suite 'My Suite'", "Test class My Suite")) {
                        test(if813OrOlderTestDisplayName())
                    }
                }
            }
        }
    }

    def "reports custom test events (parameterized tests)"() {
        given:
        buildFile("""
            import java.time.Instant

            abstract class CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @Inject
                abstract ProjectLayout getLayout()

                @TaskAction
                void runTests() {
                    try (def reporter = testEventReporterFactory.createTestEventReporter(
                        "Custom test root",
                        getLayout().getBuildDirectory().dir("test-results/Custom test root").get(),
                        getLayout().getBuildDirectory().dir("reports/tests/Custom test root").get()
                    )) {
                        reporter.started(Instant.now())
                        try (def mySuite = reporter.reportTestGroup("My Suite")) {
                            mySuite.started(Instant.now())
                            try (def myTestMethod = mySuite.reportTestGroup("myTestMethod")) {
                                 myTestMethod.started(Instant.now())
                                 try (def myTest = myTestMethod.reportTest("myTestMethod[0]", "My test method! (foo=0)")) {
                                     myTest.started(Instant.now())
                                     myTest.output(Instant.now(), TestOutputEvent.Destination.StdOut, "This is a test output on stdout")
                                     myTest.output(Instant.now(), TestOutputEvent.Destination.StdErr, "This is a test output on stderr")
                                     myTest.succeeded(Instant.now())
                                 }
                                 try (def myTest = myTestMethod.reportTest("myTestMethod[1]", "My test method! (foo=1)")) {
                                     myTest.started(Instant.now())
                                     myTest.output(Instant.now(), TestOutputEvent.Destination.StdOut, "This is a test output on stdout")
                                     myTest.output(Instant.now(), TestOutputEvent.Destination.StdErr, "This is a test output on stderr")
                                     myTest.succeeded(Instant.now())
                                 }
                                 myTestMethod.succeeded(Instant.now())
                            }
                            mySuite.succeeded(Instant.now())
                        }
                        reporter.succeeded(Instant.now())
                    }
                }
            }

            tasks.register("customTest", CustomTestTask)
        """)

        when:
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .addProgressListener(events, OperationType.TASK, OperationType.TEST)
                    .forTasks('customTest')
                    .run()
        }

        then:
        testEvents {
            task(":customTest") {
                nested("Test suite 'Custom test root'") {
                    nested(if813OrOlder("Test suite 'My Suite'", "Test class My Suite")) {
                        nested(if813OrOlder("Test suite 'myTestMethod'", "Test class myTestMethod")) {
                            test(if813OrOlder("Test myTestMethod[0](myTestMethod[0])", "My test method! (foo=0)"))
                            test(if813OrOlder("Test myTestMethod[1](myTestMethod[1])", "My test method! (foo=1)"))
                        }
                    }
                }
            }
        }
    }
}
