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


import org.gradle.integtests.tooling.CustomTestEventsFixture
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType

@ToolingApiVersion(">=8.8")
@TargetGradleVersion(">=8.12")
class CustomTestEventsCrossVersionSpec extends ToolingApiSpecification implements CustomTestEventsFixture {
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
                abstract TestEventService getTestEventService()

                @TaskAction
                void runTests() {
                    try (def generator = getTestEventService().generateTestEvents("Custom test root")) {
                        generator.started(Instant.now())
                        try (def myTest = generator.createAtomicNode("MyTestInternal", "My test!")) {
                            myTest.started(Instant.now())
                            myTest.output(Instant.now(), TestOutputEvent.Destination.StdOut, "This is a test output on stdout")
                            myTest.output(Instant.now(), TestOutputEvent.Destination.StdErr, "This is a test output on stderr")
                            myTest.completed(Instant.now(), TestResult.ResultType.SUCCESS)
                        }
                        generator.completed(Instant.now(), TestResult.ResultType.SUCCESS)
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
                abstract TestEventService getTestEventService()

                @TaskAction
                void runTests() {
                    try (def generator = getTestEventService().generateTestEvents("Custom test root")) {
                        generator.started(Instant.now())
                        try (def mySuite = generator.createCompositeNode("My Suite")) {
                            mySuite.started(Instant.now())
                            try (def myTest = mySuite.createAtomicNode("MyTestInternal", "My test!")) {
                                 myTest.started(Instant.now())
                                 myTest.output(Instant.now(), TestOutputEvent.Destination.StdOut, "This is a test output on stdout")
                                 myTest.output(Instant.now(), TestOutputEvent.Destination.StdErr, "This is a test output on stderr")
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
}
