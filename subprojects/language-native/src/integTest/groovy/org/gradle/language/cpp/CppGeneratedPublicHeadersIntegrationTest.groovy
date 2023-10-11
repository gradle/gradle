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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrary
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

class CppGeneratedPublicHeadersIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def app = new CppAppWithLibrary()

    @ToBeFixedForConfigurationCache
    @Issue("https://github.com/gradle/gradle-native/issues/994")
    def "can depends on library with generated headers"() {
        given:
        settingsFile << """
            include 'app', 'lib'
        """

        writeApp()
        writeLibrary()

        when:
        succeeds ":app:compileDebugCpp"
        then:
        result.assertTasksExecuted(":lib:generatePublicHeaders", ":app:compileDebugCpp")
    }

    private writeApp() {
        app.main.writeToProject(file("app"))
        file("app/build.gradle") << """
            apply plugin: 'cpp-application'
            group = 'org.gradle.cpp'
            version = '1.0'

            dependencies {
                implementation project(':lib')
            }
        """
    }

    private writeLibrary(TestFile dir = testDirectory) {
        def libraryPath = dir.file("lib")
        app.greeter.publicHeaders.writeToSourceDir(testDirectory.file("staging-includes"))
        app.greeter.privateHeaders.writeToSourceDir(libraryPath.file("src/main/headers"))
        app.greeter.sources.writeToSourceDir(libraryPath.file("src/main/cpp"))
        libraryPath.file("build.gradle") << """
            apply plugin: 'cpp-library'
            group = 'org.gradle.cpp'
            version = '1.0'

            library {
                def generatorTask = tasks.register('generatePublicHeaders', Sync) {
                    from(rootProject.file('staging-includes'))
                    into({ temporaryDir })
                }

                publicHeaders.from(generatorTask)
            }
        """
    }
}
