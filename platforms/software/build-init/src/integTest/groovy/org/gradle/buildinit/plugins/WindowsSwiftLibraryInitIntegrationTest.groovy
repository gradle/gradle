/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.buildinit.plugins


import org.gradle.buildinit.plugins.fixtures.ScriptDslFixture
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.gradle.buildinit.plugins.SwiftApplicationInitIntegrationTest.LINUX_MAIN_DOT_SWIFT
import static org.gradle.buildinit.plugins.SwiftLibraryInitIntegrationTest.SAMPLE_LIBRARY_CLASS
import static org.gradle.buildinit.plugins.SwiftLibraryInitIntegrationTest.SAMPLE_LIBRARY_TEST_CLASS

@Requires(UnitTestPreconditions.Windows)
class WindowsSwiftLibraryInitIntegrationTest extends AbstractInitIntegrationSpec {

    @Override
    String subprojectName() { 'lib' }

    def "creates sample library source if no source present with #scriptDsl build scripts"() {
        when:
        run('init', '--type', 'swift-library', '--dsl', scriptDsl.id)

        then:
        subprojectDir.file("src/main/swift").assertHasDescendants(SAMPLE_LIBRARY_CLASS)
        subprojectDir.file("src/test/swift").assertHasDescendants(SAMPLE_LIBRARY_TEST_CLASS, LINUX_MAIN_DOT_SWIFT)

        and:
        subprojectDir.file("src/test/swift/${SAMPLE_LIBRARY_TEST_CLASS}").text.contains("@testable import Lib")

        and:
        dslFixtureFor(scriptDsl).getBuildFile().text.contains("Swift tool chain does not support Windows. The following targets macOS and Linux:")
        dslFixtureFor(scriptDsl).getBuildFile().text.contains("targetMachines.add(machines.macOS.x86_64")
        dslFixtureFor(scriptDsl).getBuildFile().text.contains("targetMachines.add(machines.linux.x86_64")

        and:
        commonFilesGenerated(scriptDsl)

        and:
        succeeds("build")

        and:
        outputContains("'main' component in project ':lib' does not target this operating system.")
        outputContains("'test' component in project ':lib' does not target this operating system.")

        where:
        scriptDsl << ScriptDslFixture.SCRIPT_DSLS
    }
}
