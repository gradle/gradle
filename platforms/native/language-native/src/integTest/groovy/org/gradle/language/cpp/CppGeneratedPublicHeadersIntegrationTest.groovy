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
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibraries
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

class CppGeneratedPublicHeadersIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def app = new CppAppWithLibraries()

    def setup() {
        settingsFile << """
            include 'app', 'hello', 'log'
        """

        writeApp()
    }

    @ToBeFixedForConfigurationCache
    @Issue("https://github.com/gradle/gradle-native/issues/994")
    def "can depends on library with generated headers"() {
        given:
        writeHelloLibrary { TestFile libraryPath ->
            app.greeterLib.publicHeaders.writeToSourceDir(testDirectory.file("staging-includes"))
            app.greeterLib.privateHeaders.writeToSourceDir(libraryPath.file("src/main/headers"))
            app.greeterLib.sources.writeToSourceDir(libraryPath.file("src/main/cpp"))
            libraryPath.file('build.gradle') << '''
                library {
                    def generatorTask = tasks.register('generatePublicHeaders', Sync) {
                        from(rootProject.file('staging-includes'))
                        into({ temporaryDir })
                    }

                    publicHeaders.from(generatorTask)
                }
            '''
        }
        writeLogLibrary()

        when:
        succeeds ":app:compileDebugCpp"
        then:
        result.assertTasksExecuted(":hello:generatePublicHeaders", ":app:compileDebugCpp")
    }

    @ToBeFixedForConfigurationCache
    @Issue("https://github.com/gradle/gradle-native/issues/994")
    def "can transitively depends on library with generated headers"() {
        given:
        writeHelloLibrary()
        writeLogLibrary { TestFile logPath ->
            app.loggerLib.publicHeaders.writeToSourceDir(testDirectory.file("staging-includes"))
            app.loggerLib.privateHeaders.writeToSourceDir(logPath.file("src/main/headers"))
            app.loggerLib.sources.writeToSourceDir(logPath.file("src/main/cpp"))
            logPath.file('build.gradle') << '''
                library {
                    def generatorTask = tasks.register('generatePublicHeaders', Sync) {
                        from(rootProject.file('staging-includes'))
                        into({ temporaryDir })
                    }

                    publicHeaders.from(generatorTask)
                }
            '''
        }

        when:
        succeeds ":hello:compileDebugCpp"
        then:
        result.assertTasksExecuted(":log:generatePublicHeaders", ":hello:compileDebugCpp")

        when:
        succeeds ":app:compileDebugCpp"
        then:
        result.assertTasksExecuted(":log:generatePublicHeaders", ":app:compileDebugCpp")
    }

    private writeApp() {
        app.main.writeToProject(file("app"))
        file("app/build.gradle") << """
            apply plugin: 'cpp-application'
            group = 'org.gradle.cpp'
            version = '1.0'

            dependencies {
                implementation project(':hello')
            }
        """
    }

    private writeHelloLibrary(Closure writeFixtureTo = { TestFile libraryPath -> app.greeterLib.writeToProject(libraryPath) }) {
        def libraryPath = testDirectory.file("hello")
        libraryPath.file("build.gradle") << """
            apply plugin: 'cpp-library'

            dependencies {
                api project(':log')
            }
        """
        writeFixtureTo(libraryPath)
    }

    private writeLogLibrary(Closure writeFixtureTo = { TestFile logPath -> app.loggerLib.writeToProject(logPath) }) {
        def logPath = testDirectory.file("log")
        logPath.file("build.gradle") << """
            apply plugin: 'cpp-library'
        """
        writeFixtureTo(logPath)
    }
}
