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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

import static org.gradle.testing.TestExecutionBuildOperationTestUtils.assertJunit
import static org.gradle.testing.TestExecutionBuildOperationTestUtils.assertTestNg

class TestExecutionBuildOperationsIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    final TestResources resources = new TestResources(testDirectoryProvider)

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emitsBuildOperationsForJUnitTests"() {
        given:
        executer.withRepositoryMirrors()

        when:
        run "test"

        then: "test build operations are emitted in expected hierarchy"
        def rootTestOp = operations.first(ExecuteTestBuildOperationType)
        rootTestOp.details.testDescriptor.name.startsWith("Gradle Test Run :test")
        rootTestOp.details.testDescriptor.className == null
        rootTestOp.details.testDescriptor.composite == true

        assertJunit(rootTestOp, operations)
    }

    def "emitsBuildOperationsForTestNgTests"() {
        given:
        executer.withRepositoryMirrors()

        when:
        run "test"

        then: "test build operations are emitted in expected hierarchy"
        def rootTestOp = operations.first(ExecuteTestBuildOperationType)
        rootTestOp.details.testDescriptor.name.startsWith("Gradle Test Run :test")
        rootTestOp.details.testDescriptor.className == null
        rootTestOp.details.testDescriptor.composite == true

        assertTestNg(rootTestOp, operations)
    }


    def "emits test operations as expected for two builds in a row"() {
        given:
        executer.withRepositoryMirrors()
        resources.maybeCopy('TestExecutionBuildOperationsIntegrationTest/emitsBuildOperationsForJUnitTests')

        when:
        run "test"

        then: "test build operations are emitted in expected hierarchy"
        def operations = operations.all(ExecuteTestBuildOperationType)
        operations.size() == 10
        def rootTestOp = this.operations.first(ExecuteTestBuildOperationType)
        assertJunit(rootTestOp, this.operations)

        when:
        run "test", "--rerun-tasks"

        rootTestOp = this.operations.first(ExecuteTestBuildOperationType)
        operations = this.operations.all(ExecuteTestBuildOperationType)

        then:
        operations.size() == 10
        assertJunit(rootTestOp, this.operations)
    }

    def "emits test operations as expected for composite builds"() {
        given:
        resources.maybeCopy('TestExecutionBuildOperationsIntegrationTest')
        settingsFile.text = """
            rootProject.name = "composite"
            includeBuild "emitsBuildOperationsForJUnitTests", { name = 'junit' }
            includeBuild "emitsBuildOperationsForTestNgTests", { name = 'testng' }
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
        run "junit", "testng"

        then:
        def rootTestOps = operations.all(ExecuteTestBuildOperationType) {
            it.details.testDescriptor.name.startsWith("Gradle Test Run")
        }
        assert rootTestOps.size() == 2
        assertJunit(rootTestOps.find { it.details.testDescriptor.name.startsWith("Gradle Test Run :junit:test") }, operations)
        assertTestNg(rootTestOps.find { it.details.testDescriptor.name.startsWith("Gradle Test Run :testng:test") }, operations)
    }
}
