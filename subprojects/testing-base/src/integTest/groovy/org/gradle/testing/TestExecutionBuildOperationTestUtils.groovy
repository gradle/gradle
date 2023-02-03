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

import org.gradle.api.internal.tasks.testing.operations.ExecuteTestBuildOperationType
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.operations.trace.BuildOperationRecord

import static org.gradle.util.internal.TextUtil.normaliseLineSeparators

abstract class TestExecutionBuildOperationTestUtils {


    static void assertTestNg(BuildOperationRecord rootTestOp, BuildOperationsFixture operations) {
        def executorTestOps = operations.children(rootTestOp, ExecuteTestBuildOperationType)
        assert executorTestOps.size() == 1
        assert executorTestOps[0].details.testDescriptor.name.startsWith("Gradle Test Executor ")
        assert executorTestOps[0].details.testDescriptor.className == null
        assert executorTestOps[0].details.testDescriptor.composite == true

        def firstLevelTestOps = operations.children(executorTestOps[0], ExecuteTestBuildOperationType)
        assert firstLevelTestOps.size() == 1
        assert firstLevelTestOps.details.testDescriptor.name == ["SimpleSuite"]
        assert firstLevelTestOps.details.testDescriptor.className == [null]
        assert firstLevelTestOps.details.testDescriptor.composite == [true]

        def suiteTestOps = operations.children(firstLevelTestOps[0], ExecuteTestBuildOperationType)
        assert suiteTestOps.size() == 1
        assert suiteTestOps*.details.testDescriptor.name == ["SimpleTest"]
        assert suiteTestOps*.details.testDescriptor.className == [null]
        assert suiteTestOps*.details.testDescriptor.composite == [true]

        def suiteTestTestOps = operations.children(suiteTestOps[0], ExecuteTestBuildOperationType)
        assert suiteTestTestOps.size() == 3
        assert suiteTestTestOps*.details.testDescriptor.name == ["fail", "ok", "anotherOk"]
        assert suiteTestTestOps*.details.testDescriptor.className == ["org.gradle.FooTest", "org.gradle.FooTest", "org.gradle.BarTest"]
        assert suiteTestTestOps*.details.testDescriptor.composite == [false, false, false]

        def testOutput = suiteTestTestOps[1].progress
        assert testOutput.size() == 2
        assert testOutput*.details.output.message.collect { normaliseLineSeparators(it) } == ["sys out ok\n", "sys err ok\n"]
        assert testOutput*.details.output.destination == ["StdOut", "StdErr"]
    }

    static void assertJunit(BuildOperationRecord rootTestOp, BuildOperationsFixture operations) {
        def executorTestOps = operations.children(rootTestOp, ExecuteTestBuildOperationType)
        assert executorTestOps.size() == 1
        assert executorTestOps[0].details.testDescriptor.name.startsWith("Gradle Test Executor ")
        assert executorTestOps[0].details.testDescriptor.className == null
        assert executorTestOps[0].details.testDescriptor.composite == true

        def firstLevelTestOps = operations.children(executorTestOps[0], ExecuteTestBuildOperationType).sort { it.details.testDescriptor.name }
        assert firstLevelTestOps.size() == 2
        assert firstLevelTestOps*.details.testDescriptor.name == ["org.gradle.Test", "org.gradle.TestSuite"]
        assert firstLevelTestOps*.details.testDescriptor.className == ["org.gradle.Test", "org.gradle.TestSuite"]
        assert firstLevelTestOps*.details.testDescriptor.composite == [true, true]

        def suiteTestOps = operations.children(firstLevelTestOps[1], ExecuteTestBuildOperationType)
        assert suiteTestOps.size() == 4
        assert suiteTestOps*.details.testDescriptor.name as Set == ["ok", "fail", "otherFail", "otherOk"] as Set
        assert suiteTestOps*.details.testDescriptor.className as Set == ["org.gradle.Test", "org.gradle.Test", "org.gradle.OtherTest", "org.gradle.OtherTest"] as Set
        assert suiteTestOps*.details.testDescriptor.composite == [false, false, false, false]

        def testTestOps = operations.children(firstLevelTestOps[0], ExecuteTestBuildOperationType)
        assert testTestOps.size() == 2
        assert testTestOps*.details.testDescriptor.name as Set == ["ok", "fail"] as Set
        assert testTestOps*.details.testDescriptor.className as Set == ["org.gradle.Test", "org.gradle.Test"] as Set
        assert testTestOps*.details.testDescriptor.composite == [false, false]

        // outputs are emitted in test build operation hierarchy
        def testSuiteOutput = firstLevelTestOps[1].progress
        assert testSuiteOutput.size() == 4
        assert testSuiteOutput*.details.output.message.collect {
            normaliseLineSeparators(it)
        } == ["before suite class out\n", "before suite class err\n", "after suite class out\n", "after suite class err\n"]
        assert testSuiteOutput*.details.output.destination == ["StdOut", "StdErr", "StdOut", "StdErr"]

        def testOutput = testTestOps[0].progress
        testOutput.size() == 2

        testOutput*.details.output.message.collect { normaliseLineSeparators(it) } == ["sys out ok\n", "sys err ok\n"]
        testOutput*.details.output.destination == ["StdOut", "StdErr"]
    }

}
