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
import org.gradle.vcs.fixtures.GitFileRepository
import org.gradle.vcs.git.GitVersionControlSpec
import org.junit.Rule

class CppDependenciesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def app = new CppAppWithLibraries()
    @Rule
    GitFileRepository repo = new GitFileRepository(testDirectory)

    @ToBeFixedForConfigurationCache
    def "can combine C++ builds in a composite"() {
        given:
        createDirs("app", "hello", "log")
        settingsFile << """
            include 'app'
            includeBuild 'hello'
            includeBuild 'log'
        """

        writeApp()
        writeHelloLibrary()
        writeLogLibrary()

        when:
        succeeds ":app:installDebug"
        then:
        assertTasksExecutedFor("Debug")
        assertAppHasOutputFor("debug")

        when:
        succeeds ":app:installRelease"
        then:
        assertTasksExecutedFor("Release")
        assertAppHasOutputFor("release")
    }

    // NOTE: This method is named in a short way because of the maximum path length
    // on Windows.
    @ToBeFixedForConfigurationCache
    def "from VCS"() {
        given:
        createDirs("app")
        settingsFile << """
            include 'app'

            sourceControl {
                vcsMappings {
                    all { details ->
                        if (details.requested.group == "org.gradle.cpp") {
                            from(${GitVersionControlSpec.name}) {
                                url = uri("${repo.url}")
                                rootDir = details.requested.module
                            }
                        }
                    }
                }
            }
        """

        writeApp()
        writeHelloLibrary(repo.workTree)
        writeLogLibrary(repo.workTree)
        repo.commit('first')

        when:
        succeeds ":app:installDebug"
        then:
        assertTasksExecutedFor("Debug")
        assertAppHasOutputFor("debug")

        when:
        succeeds ":app:installRelease"
        then:
        assertTasksExecutedFor("Release")
        assertAppHasOutputFor("release")
    }

    private void assertTasksExecutedFor(String buildType) {
        def tasks = [":hello:compile${buildType}Cpp", ":hello:link${buildType}", ":log:compile${buildType}Cpp", ":log:link${buildType}", ":app:compile${buildType}Cpp", ":app:link${buildType}", ":app:install${buildType}"]
        if (buildType == "Release" && !toolChain.visualCpp) {
            tasks << [ ":log:stripSymbols${buildType}", ":hello:stripSymbols${buildType}", ":app:stripSymbols${buildType}"]
        }
        assert result.assertTasksExecuted(tasks)
    }

    private void assertAppHasOutputFor(String buildType) {
        assert installation("app/build/install/main/${buildType}").exec().out == app.expectedOutput
    }

    private writeApp() {
        app.main.writeToProject(file("app"))
        file("app/build.gradle") << """
            apply plugin: 'cpp-application'
            group = 'org.gradle.cpp'
            version = '1.0'

            dependencies {
                implementation 'org.gradle.cpp:hello:latest.integration'
            }
        """
    }

    private writeHelloLibrary(TestFile dir = testDirectory) {
        def libraryPath = dir.file("hello")
        app.greeterLib.writeToProject(libraryPath)
        libraryPath.file("build.gradle") << """
            apply plugin: 'cpp-library'
            group = 'org.gradle.cpp'
            version = '1.0'

            dependencies {
                api 'org.gradle.cpp:log:latest.integration'
            }
        """
    }

    private writeLogLibrary(TestFile dir = testDirectory) {
        def logPath = dir.file("log")
        app.loggerLib.writeToProject(logPath)
        logPath.file("build.gradle") << """
            apply plugin: 'cpp-library'
            group = 'org.gradle.cpp'
            version = '1.0'
        """
    }
}
