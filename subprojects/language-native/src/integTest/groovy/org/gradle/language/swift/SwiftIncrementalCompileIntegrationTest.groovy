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

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftApp
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftAppWithLib
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.SWIFT_SUPPORT)
class SwiftIncrementalCompileIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "rebuilds application when a single source file changes"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new IncrementalSwiftApp()

        given:
        app.app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'swift-executable'
         """

        when:
        succeeds "assemble"

        then:
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":installMain", ":assemble")
        result.assertTasksNotSkipped(":compileSwift", ":linkMain", ":installMain", ":assemble")
        executable("build/exe/App").exec().out == app.app.expectedOutput

        when:
        app.alternateApp.files.first().writeToDir(file('src/main'))
        succeeds "assemble"

        then:
        result.assertTasksExecuted(":compileSwift", ":linkMain", ":installMain", ":assemble")
        result.assertTasksNotSkipped(":compileSwift", ":linkMain", ":installMain", ":assemble")
        executable("build/exe/App").exec().out == app.alternateApp.expectedOutput
    }

    def "rebuilds application when a single source file in library changes"() {
        settingsFile << "include 'app', 'greeter'"
        def app = new IncrementalSwiftAppWithLib()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-executable'
                dependencies {
                    implementation project(':greeter')
                }
            }
            project(':greeter') {
                apply plugin: 'swift-library'
            }
"""
        app.library.writeToProject(file("greeter"))
        app.executable.writeToProject(file("app"))

        when:
        succeeds ":app:assemble"

        then:
        result.assertTasksExecuted(":greeter:compileSwift", ":greeter:linkMain", ":app:compileSwift", ":app:linkMain", ":app:installMain", ":app:assemble")
        result.assertTasksNotSkipped(":greeter:compileSwift", ":greeter:linkMain", ":app:compileSwift", ":app:linkMain", ":app:installMain", ":app:assemble")
        installation("app/build/install/App").exec().out == app.expectedOutput

        when:
        app.alternateLibrary.files.first().writeToDir(file('greeter/src/main/'))
        succeeds ":app:assemble"

        then:
        result.assertTasksExecuted(":greeter:compileSwift", ":greeter:linkMain", ":app:compileSwift", ":app:linkMain", ":app:installMain", ":app:assemble")
        result.assertTasksNotSkipped(":greeter:compileSwift", ":greeter:linkMain", ":app:linkMain", ":app:installMain", ":app:assemble")
        installation("app/build/install/App").exec().out == app.alternateLibraryOutput
    }
}
