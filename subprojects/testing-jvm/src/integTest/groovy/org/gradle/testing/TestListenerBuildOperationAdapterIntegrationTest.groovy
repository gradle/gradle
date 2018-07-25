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
        ops.size() == 6

        ops[0].details.testDescriptor.name == "Gradle Test Run :test"
        ops[0].details.testDescriptor.className == null
        ops[0].details.testDescriptor.composite == true

        ops[1].details.testDescriptor.name ==~ "Gradle Test Executor \\d+"
        ops[1].details.testDescriptor.className == null
        ops[1].details.testDescriptor.composite == true

        ops[2].details.testDescriptor.name == "org.gradle.ASuite"
        ops[2].details.testDescriptor.className == "org.gradle.ASuite"
        ops[2].details.testDescriptor.composite == true

        ops[3].details.testDescriptor.name == "anotherOk"
        ops[3].details.testDescriptor.className == "org.gradle.OkTest"
        ops[3].details.testDescriptor.composite == false

        ops[4].details.testDescriptor.name == "ok"
        ops[4].details.testDescriptor.className == "org.gradle.OkTest"
        ops[4].details.testDescriptor.composite == false

        ops[5].details.testDescriptor.name == "ok"
        ops[5].details.testDescriptor.className == "org.gradle.OtherTest"
        ops[5].details.testDescriptor.composite == false
    }

}
