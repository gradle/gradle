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

import org.gradle.api.Action
import org.gradle.api.internal.tasks.testing.operations.ExecuteTestBuildOperationType
import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.util.internal.ClosureBackedAction
import org.junit.Rule

import static org.gradle.testing.TestExecutionBuildOperationTestUtils.assertJunit

class TestExecutionBuildOperationsContinuousIntegrationTest extends AbstractContinuousIntegrationTest {

    final List<Action<GradleExecuter>> afterExecute = []

    @Override
    def setupBuildOperationFixture() {
        //disable because of a test that is incompatible with the build operation fixture
    }

    final GradleExecuter delegatingExecuter = new GradleExecuter() {
        @Delegate
        GradleExecuter delegate = executer

        void afterExecute(Closure action) {
            afterExecute << new ClosureBackedAction<GradleExecuter>(action)
        }
    }

    @Rule
    final TestResources resources = new TestResources(testDirectoryProvider)

    def operations = new BuildOperationsFixture(delegatingExecuter, temporaryFolder)

    void afterBuild() {
        afterExecute*.execute(executer)
    }

    def setup() {
        executer.withRepositoryMirrors()
    }

    def "emits test operations for continuous builds"() {
        given:
        resources.maybeCopy('TestExecutionBuildOperationsIntegrationTest/emitsBuildOperationsForJUnitTests')
        succeeds("test")

        when:
        update(file("src/test/java/org/gradle/OtherTest.java"), file("src/test/java/org/gradle/OtherTest.java").text.replace("Assert.fail();", ""))
        waitForBuild()
        stopGradle()
        afterBuild()

        then: "test build operations are emitted in expected hierarchy"
        def testRootOperations = operations.all(ExecuteTestBuildOperationType) {
            it.details.testDescriptor.name.startsWith("Gradle Test Run")
        }
        testRootOperations.size() == 2
        assertJunit(testRootOperations[0], operations)
        assertJunit(testRootOperations[1], operations)
    }
}
