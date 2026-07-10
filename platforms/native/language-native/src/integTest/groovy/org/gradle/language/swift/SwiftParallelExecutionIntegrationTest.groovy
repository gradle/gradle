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


import org.gradle.language.AbstractNativeParallelIntegrationTest
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.SwiftApp
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths

@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC)
@DoesNotSupportNonAsciiPaths(reason = "swiftc does not support these paths")
class SwiftParallelExecutionIntegrationTest extends AbstractNativeParallelIntegrationTest {
    def app = new SwiftApp()

    def setup() {
        settingsFile << "rootProject.name = '${app.projectName}'"
        app.writeToProject(testDirectory)
        buildFile << """
            apply plugin: 'swift-application'
         """
    }

    def "link task is executed in parallel"() {
        createTaskThatRunsInParallelUsingCustomToolchainWith("linkDebug")

        when:
        succeeds "assemble", "parallelTask"

        then:
        assertTaskIsParallel("linkDebug")
    }

    def "compile task is executed in parallel"() {
        createTaskThatRunsInParallelUsingCustomToolchainWith("compileDebugSwift")

        when:
        succeeds "assemble", "parallelTask"

        then:
        assertTaskIsParallel("compileDebugSwift")
    }
}
