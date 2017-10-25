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

import org.gradle.nativeplatform.fixtures.app.CppAppWithLibraries
import org.gradle.vcs.internal.DirectoryRepositorySpec

class CppDependenciesIntegrationTest extends AbstractCppInstalledToolChainIntegrationTest {
    def app = new CppAppWithLibraries()

    def "can combine C++ builds in a composite"() {
        given:
        settingsFile << """
            include 'app'
            includeBuild 'hello'
            includeBuild 'log'
        """

        writeApp()
        writeHelloLibrary()
        writeLogLibrary()

        when:
        succeeds ":app:installDebugShared"
        then:
        assertTasksExecutedFor("Debug")
        assertAppHasOutputFor("debug")

        when:
        succeeds ":app:installReleaseShared"
        then:
        assertTasksExecutedFor("Release")
        assertAppHasOutputFor("release")
    }

    // NOTE: This method is named in a short way because of the maximum path length
    // on Windows.
    def "from VCS"() {
        given:
        settingsFile << """
            import ${DirectoryRepositorySpec.canonicalName}

            include 'app'

            sourceControl {
                vcsMappings {
                    addRule("org.gradle.cpp VCS rule") { details ->
                        if (details.requested.group == "org.gradle.cpp") {
                            from vcs(DirectoryRepositorySpec) {
                                sourceDir = file(details.requested.module)
                            }
                        }
                    }
                }
            }
        """

        writeApp()
        writeHelloLibrary()
        writeLogLibrary()

        when:
        succeeds ":app:installDebugShared"
        then:
        assertTasksExecutedFor("Debug")
        assertAppHasOutputFor("debug")

        when:
        succeeds ":app:installReleaseShared"
        then:
        assertTasksExecutedFor("Release")
        assertAppHasOutputFor("release")
    }

    private void assertTasksExecutedFor(String buildType, String linkage = 'Shared') {
        assert result.assertTasksExecuted(":app:depend${buildType}${linkage}Cpp", ":hello:depend${buildType}${linkage}Cpp", ":log:depend${buildType}${linkage}Cpp", ":hello:compile${buildType}${linkage}Cpp", ":hello:link${buildType}${linkage}", ":log:compile${buildType}${linkage}Cpp", ":log:link${buildType}${linkage}", ":app:compile${buildType}${linkage}Cpp", ":app:link${buildType}${linkage}", ":app:install${buildType}${linkage}")
    }

    private void assertAppHasOutputFor(String buildType, String linkage = 'shared') {
        assert installation("app/build/install/main/${buildType}/${linkage}").exec().out == app.expectedOutput
    }

    private writeApp() {
        app.main.writeToProject(file("app"))
        file("app/build.gradle") << """
            apply plugin: 'cpp-executable'
            group = 'org.gradle.cpp'
            version = '1.0'

            dependencies {
                implementation 'org.gradle.cpp:hello:latest.integration'
            }
        """
    }

    private writeHelloLibrary() {
        def libraryPath = file("hello")
        app.greeterLib.writeToProject(libraryPath)
        libraryPath.file("build.gradle") << """
            apply plugin: 'cpp-library'
            group = 'org.gradle.cpp'
            version = '1.0'
        
            dependencies {
                api 'org.gradle.cpp:log:latest.integration'
            }
        """
        libraryPath.file("settings.gradle").touch()
    }

    private writeLogLibrary() {
        def logPath = file("log")
        app.loggerLib.writeToProject(logPath)
        logPath.file("build.gradle") << """
            apply plugin: 'cpp-library'
            group = 'org.gradle.cpp'
            version = '1.0'
        """
        logPath.file("settings.gradle").touch()
    }
}
