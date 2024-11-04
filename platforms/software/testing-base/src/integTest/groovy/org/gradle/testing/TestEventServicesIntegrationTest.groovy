/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.testing

import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.operations.ExecuteTestBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

class TestEventServicesIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    final TestResources resources = new TestResources(testDirectoryProvider)

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emits build operations for custom test"() {
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
                       try (def mySuite = generator.createCompositeNested("My Suite")) {
                            mySuite.started(Instant.now())
                            try (def myTest = mySuite.createSolitaryNested("MyTestInternal", "My test!")) {
                                 myTest.started(Instant.now())
                                 myTest.output(TestOutputEvent.Destination.StdOut, "This is a test output on stdout")
                                 myTest.output(TestOutputEvent.Destination.StdErr, "This is a test output on stderr")
                                 myTest.completed(Instant.now(), TestResult.ResultType.SUCCESS)
                            }
                            try (def myTest = mySuite.createSolitaryNested("MyTestInternal2", "My failing test :(")) {
                                 myTest.started(Instant.now())
                                 myTest.output(TestOutputEvent.Destination.StdErr, "Some text on stderr")
                                 myTest.failure(TestFailure.fromTestFrameworkFailure(new RuntimeException("Test framework failure")))
                                 myTest.completed(Instant.now(), TestResult.ResultType.FAILURE)
                            }
                            mySuite.completed(Instant.now(), TestResult.ResultType.FAILURE)
                       }
                       generator.completed(Instant.now(), TestResult.ResultType.FAILURE)
                   }
                }
            }

            tasks.register("customTest", CustomTestTask)
        """)

        when:
        succeeds "customTest"

        then: "test build operations are emitted in expected hierarchy"
        def rootTestOp = operations.first(ExecuteTestBuildOperationType)
        def rootTestOpDetails = rootTestOp.details as Map<String, Map<String, ?>>
        assert (rootTestOpDetails.testDescriptor.name as String).startsWith("Custom test root")
        assert rootTestOpDetails.testDescriptor.className == null
        assert rootTestOpDetails.testDescriptor.composite

        def suiteTestOps = operations.children(rootTestOp, ExecuteTestBuildOperationType)
        assert suiteTestOps.size() == 1
        def suiteTestOpDetails = suiteTestOps[0].details as Map<String, Map<String, ?>>
        assert (suiteTestOpDetails.testDescriptor.name as String).startsWith("My Suite")
        assert suiteTestOpDetails.testDescriptor.className == null
        assert suiteTestOpDetails.testDescriptor.composite

        def firstLevelTestOps = operations.children(suiteTestOps[0], ExecuteTestBuildOperationType).sort {
            (it.details as Map<String, TestDescriptorInternal>).testDescriptor.name
        }
        assert firstLevelTestOps.size() == 2
        def firstLevelTestOpDetails = firstLevelTestOps*.details as List<Map<String, Map<String, ?>>>
        assert firstLevelTestOpDetails*.testDescriptor.name == ["MyTestInternal", "MyTestInternal2"]
        assert firstLevelTestOpDetails*.testDescriptor.displayName == ["My test!", "My failing test :("]
        assert firstLevelTestOpDetails*.testDescriptor.className == [null, null]
        assert firstLevelTestOpDetails*.testDescriptor.composite == [false, false]

        def firstTestOutputProgress = firstLevelTestOps[0].progress
        assert firstTestOutputProgress.size() == 2
        def firstTestOutputs = firstTestOutputProgress*.details.output as List<Map<String, ?>>
        assert firstTestOutputs[0].destination == "StdOut"
        assert firstTestOutputs[0].message == "This is a test output on stdout"
        assert firstTestOutputs[1].destination == "StdErr"
        assert firstTestOutputs[1].message == "This is a test output on stderr"

        def secondTestOutputProgress = firstLevelTestOps[1].progress
        assert secondTestOutputProgress.size() == 1
        def secondTestOutputs = secondTestOutputProgress[0].details.output as Map<String, ?>
        assert secondTestOutputs.destination == "StdErr"
        assert secondTestOutputs.message == "Some text on stderr"
    }
}
