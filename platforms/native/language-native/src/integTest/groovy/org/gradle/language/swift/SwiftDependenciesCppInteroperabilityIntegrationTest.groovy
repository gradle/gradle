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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithCppLibrary
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
import org.gradle.vcs.fixtures.GitFileRepository

@DoesNotSupportNonAsciiPaths(reason = "Swift sometimes fails when executed from non-ASCII directory")
class SwiftDependenciesCppInteroperabilityIntegrationTest extends AbstractSwiftMixedLanguageIntegrationTest {
    def app = new SwiftAppWithCppLibrary()

    @ToBeFixedForConfigurationCache
    def "can depend on both swift and cpp libraries from VCS"() {
        given:
        createDirs("app")
        settingsFile << """
            include 'app'

            sourceControl {
                vcsMappings {
                    all { details ->
                        if (details.requested.group == "org.gradle.swift") {
                            from(GitVersionControlSpec) {
                                url = uri(details.requested.module)
                            }
                        }
                    }
                }
            }
        """

        writeApp()
        writeHelloLibrary()
        writeCppLogLibrary()

        when:
        succeeds ":app:installDebug"

        then:
        result.assertTasksExecuted(":hello:compileDebugSwift", ":hello:linkDebug",
            ":log:compileDebugCpp", ":log:linkDebug",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug")
        assertAppHasOutputFor("debug")

        when:
        succeeds ":app:installRelease"

        then:
        result.assertTasksExecuted(":hello:compileReleaseSwift", ":hello:linkRelease", ":hello:stripSymbolsRelease",
            ":log:compileReleaseCpp", ":log:linkRelease", ":log:stripSymbolsRelease",
            ":app:compileReleaseSwift", ":app:linkRelease", ":app:stripSymbolsRelease", ":app:installRelease")
        assertAppHasOutputFor("release")
    }

    private void assertAppHasOutputFor(String buildType) {
        assert installation("app/build/install/main/${buildType}").exec().out == app.expectedOutput
    }

    private writeApp() {
        app.application.writeToProject(file("app"))
        file("app/build.gradle") << """
            apply plugin: 'swift-application'
            group = 'org.gradle.swift'
            version = '1.0'

            dependencies {
                implementation 'org.gradle.swift:hello:latest.integration'
            }
        """
    }

    private writeHelloLibrary() {
        def libraryPath = file("hello")
        def libraryRepo = GitFileRepository.init(libraryPath)
        app.library.writeToProject(libraryPath)
        libraryPath.file("build.gradle") << """
            apply plugin: 'swift-library'
            group = 'org.gradle.swift'
            version = '1.0'

            dependencies {
                api 'org.gradle.swift:log:latest.integration'
            }
        """
        libraryPath.file("settings.gradle").touch()
        libraryRepo.commit("initial commit")
        libraryRepo.close()
    }

    private writeCppLogLibrary() {
        def logPath = file("log")
        def logRepo = GitFileRepository.init(logPath)
        app.logLibrary.writeToProject(logPath)
        logPath.file("build.gradle") << """
            apply plugin: 'cpp-library'
            group = 'org.gradle.swift'
            version = '1.0'
        """
        logPath.file("settings.gradle").touch()
        logRepo.commit("initial commit")
        logRepo.close()
    }
}
