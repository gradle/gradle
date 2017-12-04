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

package org.gradle.nativeplatform.test.cpp.plugins

import org.gradle.language.cpp.AbstractCppInstalledToolChainIntegrationTest
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp

class CppUnitTestPluginIntegrationTest extends AbstractCppInstalledToolChainIntegrationTest {
    def "can run test executable"() {
        def app = new CppHelloWorldApp()
        buildFile << """
            apply plugin: 'cpp-library'
            apply plugin: 'cpp-unit-test'
        """

        app.library.writeSources(file("src/main"))
        app.simpleTestExecutable.writeSources(file("src/unitTest"))

        when:
        succeeds("check")

        then:
        result.assertTasksExecuted(":compileDebugCpp",
            ":compileUnitTestCpp", ":linkUnitTest", ":installUnitTest", ":runUnitTest", ":check")
    }

    def "does nothing if cpp-library or cpp-application are not applied"() {
        def app = new CppHelloWorldApp()
        buildFile << """
            apply plugin: 'cpp-unit-test'
        """

        app.library.writeSources(file("src/main"))
        app.simpleTestExecutable.writeSources(file("src/unitTest"))

        when:
        succeeds("check")

        then:
        result.assertTasksExecuted( ":check")
    }
}
