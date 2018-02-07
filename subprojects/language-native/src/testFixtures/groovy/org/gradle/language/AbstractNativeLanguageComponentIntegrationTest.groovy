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

import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

abstract class AbstractNativeLanguageComponentIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def "binaries have the right platform type"() {
        given:
        makeSingleProject()
        buildFile << """
            task verifyBinariesPlatformType {
                doLast {
                    ${componentUnderTestDsl}.binaries.get().each {
                        assert it.targetPlatform.operatingSystem.name == "${OperatingSystem.current().name}"
                        assert it.targetPlatform.architecture.name == "${defaultArchitecture}"
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

    protected String getDefaultArchitecture() {
        DefaultNativePlatform.currentArchitecture.name
    }

    protected abstract void makeSingleProject()

    protected abstract String getComponentUnderTestDsl()
}
