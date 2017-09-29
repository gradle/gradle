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

import org.gradle.api.tasks.testing.ExecuteTestBuildOperationType
import org.gradle.api.tasks.testing.TestOutputBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.TestResources
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.junit.Rule

class TestListenerBuildOperationAdapterIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    final TestResources resources = new TestResources(testDirectoryProvider)

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emitsBuildOperationsForJUnitTests"() {
        when:
        runAndFail "test"

        then:"test build operations are emitted in expected hierarchy"
        def rootTestOp = operations.first(ExecuteTestBuildOperationType)
        rootTestOp.details.testDescriptor.name.startsWith("Gradle Test Executor ")
        rootTestOp.details.testDescriptor.className == null
        rootTestOp.details.testDescriptor.composite == true

        def firstLevelTestOps = directChildren(rootTestOp, ExecuteTestBuildOperationType)
        firstLevelTestOps.size() == 2
        firstLevelTestOps*.details.testDescriptor.name == ["org.gradle.Test", "org.gradle.TestSuite"]
        firstLevelTestOps*.details.testDescriptor.className == ["org.gradle.Test", "org.gradle.TestSuite"]
        firstLevelTestOps*.details.testDescriptor.composite == [true, true]

        def suiteTestOps = directChildren(firstLevelTestOps[1], ExecuteTestBuildOperationType)
        suiteTestOps.size() == 4
        suiteTestOps*.details.testDescriptor.name == ["ok", "fail", "otherFail", "otherOk"]
        suiteTestOps*.details.testDescriptor.className == ["org.gradle.Test", "org.gradle.Test", "org.gradle.OtherTest", "org.gradle.OtherTest"]
        suiteTestOps*.details.testDescriptor.composite == [false, false, false, false]

        def testTestOps = directChildren(firstLevelTestOps[0], ExecuteTestBuildOperationType)
        testTestOps.size() == 2
        testTestOps*.details.testDescriptor.name == ["ok", "fail"]
        testTestOps*.details.testDescriptor.className == ["org.gradle.Test", "org.gradle.Test"]
        testTestOps*.details.testDescriptor.composite == [false, false]

        and:"outputs are emitted in test build operation hierarchy"
        def testSuiteOutput = directChildren(firstLevelTestOps[1], TestOutputBuildOperationType)
        testSuiteOutput.size() == 4
        testSuiteOutput*.result.output.message == ["before suite class out\n" , "before suite class err\n" , "after suite class out\n", "after suite class err\n"]
        testSuiteOutput*.result.output.destination == ["StdOut", "StdErr", "StdOut", "StdErr"]

        def testOutput = directChildren(testTestOps[0], TestOutputBuildOperationType)
        testOutput.size() == 2
        testOutput*.result.output.message == ["sys out ok\n" , "sys err ok\n"]
        testOutput*.result.output.destination == ["StdOut", "StdErr"]
    }

    def "emitsBuildOperationsForTestNgTests"() {
        when:
        runAndFail "test"

        then:"test build operations are emitted in expected hierarchy"
        def rootTestOp = operations.first(ExecuteTestBuildOperationType)
        rootTestOp.details.testDescriptor.name.startsWith("Gradle Test Executor ")
        rootTestOp.details.testDescriptor.className == null
        rootTestOp.details.testDescriptor.composite == true

        def firstLevelTestOps = directChildren(rootTestOp, ExecuteTestBuildOperationType)
        firstLevelTestOps.size() == 1
        firstLevelTestOps.details.testDescriptor.name == ["SimpleSuite"]
        firstLevelTestOps.details.testDescriptor.className == [null]
        firstLevelTestOps.details.testDescriptor.composite == [true]

        def suiteTestOps = directChildren(firstLevelTestOps[0], ExecuteTestBuildOperationType)
        suiteTestOps.size() == 1
        suiteTestOps*.details.testDescriptor.name == ["SimpleTest"]
        suiteTestOps*.details.testDescriptor.className == [null]
        suiteTestOps*.details.testDescriptor.composite == [true]

        def suiteTestTestOps = directChildren(suiteTestOps[0], ExecuteTestBuildOperationType)
        suiteTestTestOps.size() == 3
        suiteTestTestOps*.details.testDescriptor.name == ["fail", "ok", "anotherOk"]
        suiteTestTestOps*.details.testDescriptor.className == ["org.gradle.FooTest", "org.gradle.FooTest", "org.gradle.BarTest"]
        suiteTestTestOps*.details.testDescriptor.composite == [false, false, false]

        and:"outputs are emitted in test build operation hierarchy"
        def testOutput = directChildren(suiteTestTestOps[1], TestOutputBuildOperationType)
        testOutput.size() == 2
        testOutput*.result.output.message == ["sys out ok\n" , "sys err ok\n"]
        testOutput*.result.output.destination == ["StdOut", "StdErr"]
    }


    def directChildren(BuildOperationRecord parent, Class<ExecuteTestBuildOperationType> operationType) {
        return operations.search(parent, operationType) { it.parentId == parent.id }
    }
}
