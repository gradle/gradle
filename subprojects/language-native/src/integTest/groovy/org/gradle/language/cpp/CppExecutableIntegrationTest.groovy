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

package org.gradle.language.cpp

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppCompilerDetectingTestApp
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp

import static org.gradle.util.Matchers.containsText

class CppExecutableIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "build fails when compilation fails"() {
        given:
        buildFile << """
            apply plugin: 'cpp-executable'
         """

        and:
        file("src/main/cpp/broken.cpp") << """
        #include <iostream>

        'broken
"""

        expect:
        fails "assemble"
        failure.assertHasDescription("Execution failed for task ':compileCpp'.");
        failure.assertHasCause("A build operation failed.")
        failure.assertThatCause(containsText("C++ compiler failed while compiling broken.cpp"))
    }

    def "sources are compiled with C++ compiler"() {
        def app = new CppCompilerDetectingTestApp()

        given:
        app.writeSources(file('src/main'))

        and:
        buildFile << """
            apply plugin: 'cpp-executable'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileCpp", ":linkMain", ":assemble")
        executable("build/exe/main").exec().out == app.expectedOutput(AbstractInstalledToolChainIntegrationSpec.toolChain)
    }

    def "can compile and link against a library"() {
        settingsFile << "include 'app', 'lib'"
        def app = new CppHelloWorldApp()

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'cpp-executable'
                dependencies {
                    implementation project(':lib')
                }
            }
            project(':lib') {
                apply plugin: 'cpp-library'
            }
"""
        app.library.headerFiles.each { it.writeToFile(file("lib/src/main/public/$it.name")) }
        app.library.sourceFiles.each { it.writeToFile(file("lib/src/main/cpp/$it.name")) }
        app.executable.sourceFiles.each { it.writeToDir(file('app/src/main')) }

        expect:
        succeeds ":app:assemble"
        result.assertTasksExecuted(":lib:compileCpp", ":lib:linkMain", ":app:compileCpp", ":app:linkMain", ":app:assemble")
        executable("app/build/exe/main").exec().out == app.englishOutput
    }
}
