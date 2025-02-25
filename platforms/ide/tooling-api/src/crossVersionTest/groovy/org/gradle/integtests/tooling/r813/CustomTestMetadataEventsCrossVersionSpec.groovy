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

package org.gradle.integtests.tooling.r813

import org.gradle.integtests.tooling.TestEventsFixture
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType

@ToolingApiVersion(">=8.13")
@TargetGradleVersion(">=8.13")
class CustomTestMetadataEventsCrossVersionSpec extends ToolingApiSpecification implements TestEventsFixture {
    ProgressEvents events = ProgressEvents.create()

    @Override
    ProgressEvents getEvents() {
        return events
    }

    def "reports custom test events with metadata"() {
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
                            myTest.metadata(Instant.now(), "mykey", "my value")
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
                root("Custom test root") {
                    test("MyTestInternal") {
                        displayName "My test!"
                        metadata("mykey", "my value")
                    }
                }
            }
        }
    }

    def "reports custom test events with metadata reusing test start timestamp"() {
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
                root("Custom test root") {
                    test("MyTestInternal") {
                        displayName "My test!"
                        metadata("mykey", "myvalue")
                    }
                }
            }
        }
    }

    def "reports custom test events with multiple metadata events and output"() {
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
                root("Custom test root") {
                    test("MyTestInternal") {
                        displayName "My test!"
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

    def "reports custom test events at root, group and nested group levels"() {
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
                    try (def root = testEventReporterFactory.createTestEventReporter(
                        "Custom test root",
                        getLayout().getBuildDirectory().dir("test-results/Custom test root").get(),
                        getLayout().getBuildDirectory().dir("reports/tests/Custom test root").get()
                    )) {
                        root.started(Instant.now())
                        root.metadata(Instant.now(), "myroot", "my root value")
                        try (def myGroup = root.reportTestGroup("My Group")) {
                            myGroup.started(Instant.now())
                            myGroup.metadata(Instant.now(), "mygroup", "my group value")
                            try (def myInnerGroup = myGroup.reportTestGroup("My Inner Group")) {
                                myInnerGroup.started(Instant.now())
                                myInnerGroup.metadata(Instant.now(), "myinnergroup", "my inner group value")
                                try (def myTest = myInnerGroup.reportTest("MyTestInternal", "My test!")) {
                                    myTest.started(Instant.now())
                                    myTest.metadata(Instant.now(), "mytest", "my test value")
                                    myTest.succeeded(Instant.now())
                                }
                                myInnerGroup.succeeded(Instant.now())
                            }
                            myGroup.succeeded(Instant.now())
                        }
                        root.succeeded(Instant.now())
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
                root("Custom test root") {
                    metadata("myroot", "my root value")
                    composite("My Group") {
                        metadata("mygroup", "my group value")
                        composite("My Inner Group") {
                            metadata("myinnergroup", "my inner group value")
                            test("MyTestInternal") {
                                displayName "My test!"
                                metadata("mytest", "my test value")
                            }
                        }
                    }
                }
            }
        }
    }

    def "reporting custom test events with metadata doesn't break older TAPI"() {
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
                            myTest.metadata(Instant.now(), "mykey", "my value")
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
                root("Custom test root") {
                    test("MyTestInternal") {
                        displayName "My test!"
                    }
                }
            }
        }
    }
}
