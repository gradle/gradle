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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest

abstract class AbstractTestListenerBuildOperationAdapterIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {
    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    abstract boolean isEmitsTestClassOperations()
    abstract void writeTestSources()

    @ToBeFixedForConfigurationCache(because = "load-after-store")
    def "emits build operations for junit tests"() {
        given:
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies {
                ${testFrameworkDependencies}
            }
            test {
                ${configureTestFramework}
                include '**/ASuite.class'
                exclude '**/*Test.class'
            }
        """
        writeTestSources()

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
        checkForSuiteOperations(iterator, "org.gradle.ASuite")
        if (emitsTestClassOperations) {
            checkForTestClassOperations(iterator, "org.gradle.OkTest")
        }
        checkForTestOperations(iterator, "org.gradle.OkTest", "anotherOk")
        checkForTestOperations(iterator, "org.gradle.OkTest", "ok")
        if (emitsTestClassOperations) {
            checkForTestClassOperations(iterator, "org.gradle.OtherTest")
        }
        checkForTestOperations(iterator, "org.gradle.OtherTest", "ok")

        !iterator.hasNext()
    }

    void checkForSuiteOperations(Iterator<BuildOperationRecord> iterator, String suiteName) {
        with(iterator.next()) {
            details.testDescriptor.name == suiteName
            details.testDescriptor.className == suiteName
            details.testDescriptor.composite == true
        }
    }

    void checkForTestClassOperations(Iterator<BuildOperationRecord> iterator, String className) {
        with(iterator.next()) {
            details.testDescriptor.name == className
            details.testDescriptor.className == className
            details.testDescriptor.composite == true
        }
    }

    void checkForTestOperations(Iterator<BuildOperationRecord> iterator, String className, String methodName) {
        with(iterator.next()) {
            details.testDescriptor.name == maybeParentheses(methodName)
            details.testDescriptor.className == className
            details.testDescriptor.composite == false
        }
    }
}
