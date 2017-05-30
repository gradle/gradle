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
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.HelloWorldApp

import static org.gradle.util.Matchers.containsText

class CppLibraryIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    HelloWorldApp helloWorldApp = new CppHelloWorldApp()

    def "build fails when compilation fails"() {
        given:
        buildFile << """
            apply plugin: 'cpp-library'
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
        given:
        def app = new CppHelloWorldApp()
        app.library.writeSources(file('src/main'))

        and:
        buildFile << """
            apply plugin: 'cpp-library'
         """

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileCpp", ":linkMain", ":assemble")
        sharedLibrary("build/lib/main").assertExists()
    }
}
