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

package org.gradle.ide.xcode

import org.gradle.ide.xcode.fixtures.AbstractXcodeIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.gradle.util.Matchers.containsText

class XcodeErrorIntegrationTest extends AbstractXcodeIntegrationSpec {
    @Requires(UnitTestPreconditions.HasXCode)
    @ToBeFixedForConfigurationCache
    def "fails to build when project code is broken"() {
        useXcodebuildTool()

        given:
        buildFile << """
            apply plugin: 'swift-application'
         """

        and:
        file("src/main/swift/broken.swift") << "broken!"

        and:
        succeeds("xcode")

        expect:
        def failure = xcodebuild
            .withProject(rootXcodeProject)
            .withScheme("App")
            .fails()
        failure.assertHasDescription("Execution failed for task ':compileDebugSwift'.")
        failure.assertHasCause("A build operation failed.")
        failure.assertThatCause(containsText("Swift compiler failed while compiling swift file(s)"))
    }
}
