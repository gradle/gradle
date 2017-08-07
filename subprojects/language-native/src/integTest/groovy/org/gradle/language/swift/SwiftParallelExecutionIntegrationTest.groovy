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
import org.gradle.nativeplatform.fixtures.app.SwiftApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.SWIFT_SUPPORT)
class SwiftParallelExecutionIntegrationTest extends AbstractNativeParallelIntegrationTest {
    def app = new SwiftApp()

    def "link task is executed in parallel"() {
        settingsFile << "rootProject.name = 'app'"

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-executable'
        """
        withTaskThatRunsParallelWith("linkMain")

        when:
        succeeds "assemble", "parallelTask"

        then:
        assertTaskIsParallel("linkMain")
    }

    def "compile task is executed in parallel"() {
        settingsFile << "rootProject.name = 'app'"

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-executable'
         """
        withTaskThatRunsParallelWith("compileSwift")

        when:
        succeeds "assemble", "parallelTask"

        then:
        assertTaskIsParallel("compileSwift")
    }
}
