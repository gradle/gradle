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

package org.gradle.language

import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.SourceElement
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

abstract class AbstractNativeLanguageComponentIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def "binaries have the right platform type"() {
        given:
        makeSingleProject()
        buildFile << """
            task verifyBinariesPlatformType {
                doLast {
                    ${componentUnderTestDsl}.binaries.get().each {
                        assert it.targetMachine.operatingSystemFamily.name == "${DefaultNativePlatform.currentOperatingSystem.toFamilyName()}"
                        assert it.targetMachine.architecture.name == "${defaultArchitecture}"
                    }
                }
            }
        """

        expect:
        succeeds "verifyBinariesPlatformType"
    }

    def "binaries have the right tool chain type"() {
        given:
        makeSingleProject()
        buildFile << """
            task verifyBinariesToolChainType {
                doLast {
                    ${componentUnderTestDsl}.binaries.get().each {
                        assert it.toolChain instanceof ${AbstractInstalledToolChainIntegrationSpec.toolChain.implementationClass}
                    }
                }
            }
        """

        expect:
        succeeds "verifyBinariesToolChainType"
    }

    def "fails configuration when architecture is not supported by any tool chain"() {
        given:
        makeSingleProject()
        componentUnderTest.writeToProject(testDirectory)

        and:
        buildFile << configureTargetMachines("machines.${currentHostOperatingSystemFamilyDsl}", "machines.${currentHostOperatingSystemFamilyDsl}.architecture('foo')")

        expect:
        fails taskNameToAssembleDevelopmentBinary
        result.assertTasksExecuted()
        failure.assertHasCause("No tool chain has support to build")
    }

    protected String getDefaultArchitecture() {
        DefaultNativePlatform.currentArchitecture.name
    }

    protected abstract void makeSingleProject()

    protected abstract String getComponentUnderTestDsl()

    protected abstract SourceElement getComponentUnderTest()

    protected abstract String getTaskNameToAssembleDevelopmentBinary()

    protected String getCurrentHostOperatingSystemFamilyDsl() {
        String osFamily = DefaultNativePlatform.getCurrentOperatingSystem().toFamilyName()
        if (osFamily == OperatingSystemFamily.MACOS) {
            return "macOS"
        } else {
            return osFamily
        }
    }

    protected configureTargetMachines(String... targetMachines) {
        return """
            ${componentUnderTestDsl} {
                targetMachines = [${targetMachines.join(",")}]
            }
        """
    }
}
