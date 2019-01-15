/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.nativeplatform.test.cpp

import org.gradle.language.cpp.AbstractCppComponentIntegrationTest

abstract class AbstractCppUnitTestComponentIntegrationTest extends AbstractCppComponentIntegrationTest {
    def "check task warns when current operating system family is excluded"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << configureTargetMachines("machines.os('some-other-family')")

        expect:
        succeeds "check"

        and:
        outputContains("'${componentName}' component in project ':' does not target this operating system.")
    }

    @Override
    protected String getComponentUnderTestDsl() {
        return 'unitTest'
    }

    @Override
    protected String getTaskNameToAssembleDevelopmentBinary() {
        return 'test'
    }

    protected String getTaskNameToAssembleDevelopmentBinaryWithArchitecture(String architecture) {
        return ":runTest${architecture.capitalize()}"
    }

    @Override
    protected String getComponentName() {
        return "test"
    }
}
