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

// Proper test display names were implemented in Gradle 8.8
@ToolingApiVersion(">=8.8")
@TargetGradleVersion(">=8.12")
class CustomTestEventsCrossVersionSpec extends ToolingApiSpecification implements TestEventsFixture {
    ProgressEvents events = ProgressEvents.create()

    @Override
    ProgressEvents getEvents() {
        return events
    }

    def "reports custom test events (flat)"() {
        given:
        buildFile("""
            import java.time.Instant

            abstract class CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @TaskAction
                void runTests() {
                    try (def reporter = getTestEventReporterFactory().createTestEventReporter("Custom test root")) {
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
                composite("Custom test root") {
                    test("MyTestInternal") {
                        testDisplayName "My test!"
                    }
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

                @TaskAction
                void runTests() {
                    try (def reporter = getTestEventReporterFactory().createTestEventReporter("Custom test root")) {
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

    def "reports custom test events (parameterized tests)"() {
        given:
        buildFile("""
            import java.time.Instant

            abstract class CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @TaskAction
                void runTests() {
                    try (def reporter = getTestEventReporterFactory().createTestEventReporter("Custom test root")) {
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
                composite("Custom test root") {
                    composite("My Suite") {
                        composite("myTestMethod") {
                            test("myTestMethod[0]") {
                                testDisplayName "My test method! (foo=0)"
                            }
                            test("myTestMethod[1]") {
                                testDisplayName "My test method! (foo=1)"
                            }
                        }
                    }
                }
            }
        }
    }

    @ToolingApiVersion(">=8.12")
    def "reports custom test events with metadata #value"() {
        given:
        buildFile("""
            import java.time.Instant

            abstract class CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @TaskAction
                void runTests() {
                    try (def reporter = getTestEventReporterFactory().createTestEventReporter("Custom test root")) {
                        reporter.started(Instant.now())
                        try (def myTest = reporter.reportTest("MyTestInternal", "My test!")) {
                            myTest.started(Instant.now())
                            myTest.metadata(Instant.now(), "mykey", ${ value instanceof String ? "'$value'" : value })
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
                    .addProgressListener(events, OperationType.TASK, OperationType.TEST, OperationType.TEST_OUTPUT, OperationType.TEST_METADATA)
                    .forTasks('customTest')
                    .run()
        }

        then:
        testEvents {
            task(":customTest") {
                composite("Custom test root") {
                    test("MyTestInternal") {
                        testDisplayName "My test!"
                        metadata("mykey", value)
                    }
                }
            }
        }

        where:
        value << ["my value", 5, [1, 2, 3]]
    }

    @ToolingApiVersion(">=8.12")
    def "reports custom test events with metadata reusing test start timestamp"() {
        given:
        buildFile("""
            import java.time.Instant

            abstract class CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @TaskAction
                void runTests() {
                    try (def reporter = getTestEventReporterFactory().createTestEventReporter("Custom test root")) {
                        reporter.started(Instant.now())
                        try (def myTest = reporter.reportTest("MyTestInternal", "My test!")) {
                            def start = Instant.now()
                            myTest.started(start)
                            myTest.metadata(start, "mykey", "myvalue")
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
                    .addProgressListener(events, OperationType.TASK, OperationType.TEST, OperationType.TEST_OUTPUT, OperationType.TEST_METADATA)
                    .forTasks('customTest')
                    .run()
        }

        then:
        testEvents {
            task(":customTest") {
                composite("Custom test root") {
                    test("MyTestInternal") {
                        testDisplayName "My test!"
                        metadata("mykey", "myvalue")
                    }
                }
            }
        }
    }

    @ToolingApiVersion(">=8.12")
    def "reports custom test events with multiple metadata events and output"() {
        given:
        buildFile("""
            import java.time.Instant

            abstract class CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @TaskAction
                void runTests() {
                    try (def reporter = getTestEventReporterFactory().createTestEventReporter("Custom test root")) {
                        reporter.started(Instant.now())
                        try (def myTest = reporter.reportTest("MyTestInternal", "My test!")) {
                            myTest.started(Instant.now())
                            myTest.output(Instant.now(), TestOutputEvent.Destination.StdOut, "This is a test output on stdout")
                            myTest.metadata(Instant.now(), "mykey1", "apple")
                            myTest.metadata(Instant.now(), "mykey2", 10)
                            myTest.output(Instant.now(), TestOutputEvent.Destination.StdOut, "More output on stdout")
                            myTest.metadata(Instant.now(), "mykey3", ["banana", "cherry"])
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
                    .addProgressListener(events, OperationType.TASK, OperationType.TEST, OperationType.TEST_OUTPUT, OperationType.TEST_METADATA)
                    .forTasks('customTest')
                    .run()
        }

        then:
        testEvents {
            task(":customTest") {
                composite("Custom test root") {
                    test("MyTestInternal") {
                        testDisplayName "My test!"
                        output("This is a test output on stdout")
                        output("More output on stdout")
                        metadata("mykey1", "apple")
                        metadata("mykey2", 10)
                        metadata("mykey3", ["banana", "cherry"])
                    }
                }
            }
        }
    }

    @ToolingApiVersion("<8.12")
    def "reporting custom test events with metadata doesn't break older TAPI"() {
        given:
        buildFile("""
            import java.time.Instant

            abstract class CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @TaskAction
                void runTests() {
                    try (def reporter = getTestEventReporterFactory().createTestEventReporter("Custom test root")) {
                        reporter.started(Instant.now())
                        try (def myTest = reporter.reportTest("MyTestInternal", "My test!")) {
                            myTest.started(Instant.now())
                            myTest.metadata(Instant.now(), "mykey", ${ value instanceof String ? "'$value'" : value })
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
                    .addProgressListener(events, OperationType.TASK, OperationType.TEST) // Older TAPI won't have TEST_METADATA available
                    .forTasks('customTest')
                    .run()
        }

        then:
        testEvents {
            task(":customTest") {
                composite("Custom test root") {
                    test("MyTestInternal") {
                        testDisplayName "My test!"
                    }
                }
            }
        }

        where:
        value << ["my value", 5, [1, 2, 3]]
    }
}
