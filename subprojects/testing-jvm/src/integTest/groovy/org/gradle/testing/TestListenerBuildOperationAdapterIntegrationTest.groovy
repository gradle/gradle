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
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestResources
import org.gradle.testing.fixture.JUnitMultiVersionIntegrationSpec
import org.junit.Rule

import static org.gradle.testing.fixture.JUnitCoverage.*

@TargetCoverage({ JUNIT_4_LATEST + JUNIT_VINTAGE })
class TestListenerBuildOperationAdapterIntegrationTest extends JUnitMultiVersionIntegrationSpec {

    @Rule
    final TestResources resources = new TestResources(testDirectoryProvider)

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emits build operations for junit tests"() {
        given:
        resources.maybeCopy('/org/gradle/testing/junit/JUnitIntegrationTest/suitesOutputIsVisible')

        when:
        succeeds "test"

        then:
        def ops = operations.all(ExecuteTestBuildOperationType) { true }
        def iterator = ops.iterator()

        with(iterator.next()) {
            details.testDescriptor.name == "Gradle Test Run :test"
            details.testDescriptor.className == null
            details.testDescriptor.composite == true
        }
        with(iterator.next()) {
            details.testDescriptor.name ==~ "Gradle Test Executor \\d+"
            details.testDescriptor.className == null
            details.testDescriptor.composite == true
        }
        with(iterator.next()) {
            details.testDescriptor.name == "org.gradle.ASuite"
            details.testDescriptor.className == "org.gradle.ASuite"
            details.testDescriptor.composite == true
        }
        if (isVintage()) {
            with(iterator.next()) {
                details.testDescriptor.name == "org.gradle.OkTest"
                details.testDescriptor.className == "org.gradle.OkTest"
                details.testDescriptor.composite == true
            }
        }
        with(iterator.next()) {
            details.testDescriptor.name == "anotherOk"
            details.testDescriptor.className == "org.gradle.OkTest"
            details.testDescriptor.composite == false
        }
        with(iterator.next()) {
            details.testDescriptor.name == "ok"
            details.testDescriptor.className == "org.gradle.OkTest"
            details.testDescriptor.composite == false
        }
        if (isVintage()) {
            with(iterator.next()) {
                details.testDescriptor.name == "org.gradle.OtherTest"
                details.testDescriptor.className == "org.gradle.OtherTest"
                details.testDescriptor.composite == true
            }
        }
        with(iterator.next()) {
            details.testDescriptor.name == "ok"
            details.testDescriptor.className == "org.gradle.OtherTest"
            details.testDescriptor.composite == false
        }
        !iterator.hasNext()
    }

}
