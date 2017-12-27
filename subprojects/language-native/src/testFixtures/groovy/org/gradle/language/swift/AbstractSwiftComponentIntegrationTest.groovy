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

package org.gradle.language.swift

import org.gradle.language.AbstractNativeLanguageComponentIntegrationTest
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.VersionNumber

@Requires(TestPrecondition.SWIFT_SUPPORT)
abstract class AbstractSwiftComponentIntegrationTest extends AbstractNativeLanguageComponentIntegrationTest {
    def "binaries have the right Swift version"() {
        given:
        makeSingleProject()
        buildFile << """
            task verifyBinariesSwiftVersion {
                doLast {
                    ${componentUnderTestDsl}.binaries.get().each {
                        assert it.swiftLanguageVersion == SwiftLanguageVersion.of(${
            VersionNumber.canonicalName}.parse('${AbstractNativeLanguageComponentIntegrationTest.toolChain.version}'))
                    }
                }
            }
        """

        expect:
        succeeds "verifyBinariesSwiftVersion"
    }
}
