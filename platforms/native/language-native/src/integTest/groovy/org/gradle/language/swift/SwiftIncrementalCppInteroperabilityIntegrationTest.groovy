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
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftModifyCppDepApp
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftModifyCppDepHeadersApp
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftModifyCppDepModuleMapApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

@Requires(UnitTestPreconditions.NotMacOs)
class SwiftIncrementalCppInteroperabilityIntegrationTest extends AbstractSwiftMixedLanguageIntegrationTest {
    @ToBeFixedForConfigurationCache
    def "relinks but does not recompile when c++ sources change"() {
        def app = new IncrementalSwiftModifyCppDepApp()
        createDirs("app", "cppGreeter")
        settingsFile << "include ':app', ':cppGreeter'"

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':cppGreeter')
                }
            }
            project(':cppGreeter') {
                apply plugin: 'cpp-library'
            }
        """
        app.library.writeToProject(file("cppGreeter"))
        app.application.writeToProject(file("app"))

        when:
        succeeds ":app:assemble"

        then:
        result.assertTasksExecuted(":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        result.assertTasksNotSkipped(":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        installation("app/build/install/main/debug").exec().out == app.expectedOutput

        when:
        app.library.applyChangesToProject(file('cppGreeter'))
        succeeds ":app:assemble"

        then:
        result.assertTasksExecuted(":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        result.assertTasksNotSkipped( ":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ":app:linkDebug", ":app:installDebug", ":app:assemble")
        installation("app/build/install/main/debug").exec().out == app.alternateLibraryOutput

        when:
        succeeds ":app:assemble"

        then:
        result.assertTasksExecuted(":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        result.assertTasksSkipped(":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
    }

    @ToBeFixedForConfigurationCache
    def "recompiles when c++ headers change"() {
        def app = new IncrementalSwiftModifyCppDepHeadersApp()
        createDirs("app", "cppGreeter")
        settingsFile << "include ':app', ':cppGreeter'"

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':cppGreeter')
                }
            }
            project(':cppGreeter') {
                apply plugin: 'cpp-library'
            }
        """
        app.library.writeToProject(file("cppGreeter"))
        app.application.writeToProject(file("app"))

        when:
        succeeds ":app:assemble"

        then:
        result.assertTasksExecuted(":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        result.assertTasksNotSkipped(":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        installation("app/build/install/main/debug").exec().out == app.expectedOutput

        when:
        app.library.applyChangesToProject(file('cppGreeter'))
        succeeds ":app:assemble"

        then:
        result.assertTasksExecuted(":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        result.assertTasksNotSkipped(":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        installation("app/build/install/main/debug").exec().out == app.alternateLibraryOutput

        when:
        succeeds ":app:assemble"

        then:
        result.assertTasksExecuted(":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        result.assertTasksSkipped(":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
    }

    @ToBeFixedForConfigurationCache
    def "regenerates module map and recompiles swift app when headers change"() {
        def app = new IncrementalSwiftModifyCppDepModuleMapApp()
        createDirs("app", "cppGreeter")
        settingsFile << "include ':app', ':cppGreeter'"

        given:
        buildFile << """
            project(':app') {
                apply plugin: 'swift-application'
                dependencies {
                    implementation project(':cppGreeter')
                }
            }
            project(':cppGreeter') {
                apply plugin: 'cpp-library'
            }
        """
        app.library.writeToProject(file("cppGreeter"))
        app.application.writeToProject(file("app"))

        when:
        succeeds ":app:assemble"

        then:
        result.assertTasksExecuted(":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        result.assertTasksNotSkipped(":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        installation("app/build/install/main/debug").exec().out == app.expectedOutput

        when:
        app.library.applyChangesToProject(file('cppGreeter'))
        buildFile << """
            project(':cppGreeter') {
                library.publicHeaders.from = ['src/main/${app.library.alternateGreeter.header.sourceFile.path}']
            }
        """
        succeeds ":app:assemble"

        then:
        result.assertTasksExecuted(":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug",
            ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        result.assertTasksNotSkipped(":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        installation("app/build/install/main/debug").exec().out == app.alternateLibraryOutput

        when:
        succeeds ":app:assemble"

        then:
        result.assertTasksExecuted(":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
        result.assertTasksSkipped(":cppGreeter:compileDebugCpp", ":cppGreeter:linkDebug", ":app:compileDebugSwift", ":app:linkDebug", ":app:installDebug", ":app:assemble")
    }
}
