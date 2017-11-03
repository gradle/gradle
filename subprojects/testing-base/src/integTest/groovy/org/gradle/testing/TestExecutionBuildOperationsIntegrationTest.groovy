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
import org.gradle.api.internal.tasks.testing.operations.TestOutputBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.TestResources
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.junit.Rule

import static org.gradle.util.TextUtil.normaliseLineSeparators

class TestExecutionBuildOperationsIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    final TestResources resources = new TestResources(testDirectoryProvider)

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emitsBuildOperationsForJUnitTests"() {
        when:
        projectDir("junit")
        runAndFail("test")

        then: "test build operations are emitted in expected hierarchy"
        def rootTestOp = operations.first(ExecuteTestBuildOperationType)
        rootTestOp.details.testDescriptor.name.startsWith("Gradle Test Run :test")
        rootTestOp.details.testDescriptor.className == null
        rootTestOp.details.testDescriptor.composite == true

        assertJunit(rootTestOp)
    }

    def "emitsBuildOperationsForTestNgTests"() {
        when:
        projectDir("testng")
        runAndFail "test"

        then: "test build operations are emitted in expected hierarchy"
        def rootTestOp = operations.first(ExecuteTestBuildOperationType)
        rootTestOp.details.testDescriptor.name.startsWith("Gradle Test Run :test")
        rootTestOp.details.testDescriptor.className == null
        rootTestOp.details.testDescriptor.composite == true

        assertTestNg(rootTestOp)
    }

    def supportsCompositeBuilds() {
        given:
        resources.maybeCopy('TestExecutionBuildOperationsIntegrationTest/emitsBuildOperationsForJUnitTests')
        resources.maybeCopy('TestExecutionBuildOperationsIntegrationTest/emitsBuildOperationsForTestNgTests')
        settingsFile.text = """
            rootProject.name = "composite"
            includeBuild "junit"
            includeBuild "testng"
        """
        buildFile.text = """
            task testng {
                dependsOn gradle.includedBuild('testng').task(':test')
            }
            
            task junit {
                dependsOn gradle.includedBuild('junit').task(':test')
            }
        """
        when:
        runAndFail "junit", "testng","--continue"

        then:
        def rootTestOps = operations.all(ExecuteTestBuildOperationType) {
            it.details.testDescriptor.name.startsWith("Gradle Test Run")
        }
        assert rootTestOps.size() == 2
        assertJunit(rootTestOps.find {it.details.testDescriptor.name.startsWith("Gradle Test Run :junit:test")})
        assertTestNg(rootTestOps.find {it.details.testDescriptor.name.startsWith("Gradle Test Run :testng:test")})
    }

    private void assertJunit(BuildOperationRecord rootTestOp) {
        def executorTestOps = directChildren(rootTestOp, ExecuteTestBuildOperationType)
        assert executorTestOps.size() == 1
        assert executorTestOps[0].details.testDescriptor.name.startsWith("Gradle Test Executor ")
        assert executorTestOps[0].details.testDescriptor.className == null
        assert executorTestOps[0].details.testDescriptor.composite == true

        def firstLevelTestOps = directChildren(executorTestOps[0], ExecuteTestBuildOperationType)
        assert firstLevelTestOps.size() == 2
        assert firstLevelTestOps*.details.testDescriptor.name as Set == ["org.gradle.Test", "org.gradle.TestSuite"] as Set
        assert firstLevelTestOps*.details.testDescriptor.className as Set == ["org.gradle.Test", "org.gradle.TestSuite"] as Set
        assert firstLevelTestOps*.details.testDescriptor.composite == [true, true]

        def suiteTestOps = directChildren(firstLevelTestOps[1], ExecuteTestBuildOperationType)
        assert suiteTestOps.size() == 4
        assert suiteTestOps*.details.testDescriptor.name == ["ok", "fail", "otherFail", "otherOk"]
        assert suiteTestOps*.details.testDescriptor.className == ["org.gradle.Test", "org.gradle.Test", "org.gradle.OtherTest", "org.gradle.OtherTest"]
        assert suiteTestOps*.details.testDescriptor.composite == [false, false, false, false]

        def testTestOps = directChildren(firstLevelTestOps[0], ExecuteTestBuildOperationType)
        assert testTestOps.size() == 2
        assert testTestOps*.details.testDescriptor.name == ["ok", "fail"]
        assert testTestOps*.details.testDescriptor.className == ["org.gradle.Test", "org.gradle.Test"]
        assert testTestOps*.details.testDescriptor.composite == [false, false]

        // outputs are emitted in test build operation hierarchy
        def testSuiteOutput = directChildren(firstLevelTestOps[1], TestOutputBuildOperationType)
        assert testSuiteOutput.size() == 4
        assert testSuiteOutput*.result.output.message.collect {
            normaliseLineSeparators(it)
        } == ["before suite class out\n", "before suite class err\n", "after suite class out\n", "after suite class err\n"]
        assert testSuiteOutput*.result.output.destination == ["StdOut", "StdErr", "StdOut", "StdErr"]

        def testOutput = directChildren(testTestOps[0], TestOutputBuildOperationType)
        testOutput.size() == 2

        testOutput*.result.output.message.collect { normaliseLineSeparators(it) } == ["sys out ok\n", "sys err ok\n"]
        testOutput*.result.output.destination == ["StdOut", "StdErr"]
    }

    private void assertTestNg(BuildOperationRecord rootTestOp) {
        def executorTestOps = directChildren(rootTestOp, ExecuteTestBuildOperationType)
        assert executorTestOps.size() == 1
        assert executorTestOps[0].details.testDescriptor.name.startsWith("Gradle Test Executor ")
        assert executorTestOps[0].details.testDescriptor.className == null
        assert executorTestOps[0].details.testDescriptor.composite == true

        def firstLevelTestOps = directChildren(executorTestOps[0], ExecuteTestBuildOperationType)
        assert firstLevelTestOps.size() == 1
        assert firstLevelTestOps.details.testDescriptor.name == ["SimpleSuite"]
        assert firstLevelTestOps.details.testDescriptor.className == [null]
        assert firstLevelTestOps.details.testDescriptor.composite == [true]

        def suiteTestOps = directChildren(firstLevelTestOps[0], ExecuteTestBuildOperationType)
        assert suiteTestOps.size() == 1
        assert suiteTestOps*.details.testDescriptor.name == ["SimpleTest"]
        assert suiteTestOps*.details.testDescriptor.className == [null]
        assert suiteTestOps*.details.testDescriptor.composite == [true]

        def suiteTestTestOps = directChildren(suiteTestOps[0], ExecuteTestBuildOperationType)
        assert suiteTestTestOps.size() == 3
        assert suiteTestTestOps*.details.testDescriptor.name == ["fail", "ok", "anotherOk"]
        assert suiteTestTestOps*.details.testDescriptor.className == ["org.gradle.FooTest", "org.gradle.FooTest", "org.gradle.BarTest"]
        assert suiteTestTestOps*.details.testDescriptor.composite == [false, false, false]

        // outputs are emitted in test build operation hierarchy
        def testOutput = directChildren(suiteTestTestOps[1], TestOutputBuildOperationType)
        assert testOutput.size() == 2
        assert testOutput*.result.output.message.collect { normaliseLineSeparators(it) } == ["sys out ok\n", "sys err ok\n"]
        assert testOutput*.result.output.destination == ["StdOut", "StdErr"]
    }

    def directChildren(BuildOperationRecord parent, Class<ExecuteTestBuildOperationType> operationType) {
        return operations.search(parent, operationType) { it.parentId == parent.id }
    }
}
