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
import org.gradle.nativeplatform.fixtures.app.CppApp
import org.gradle.nativeplatform.fixtures.app.CppLib

class CppIncrementalCompileIntegrationTest extends AbstractInstalledToolChainIntegrationSpec implements CppTaskNames {

    @ToBeFixedForConfigurationCache
    def "skips compile and link tasks for executable when source doesn't change"() {
        def app = new CppApp()
        settingsFile << "rootProject.name = 'app'"

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-application'
         """

        and:
        succeeds "assemble", "assembleRelease"

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(tasks.debug.allToInstall, ":assemble")
        result.assertTasksSkipped(tasks.debug.allToInstall, ":assemble")

        executable("build/exe/main/debug/app").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput

        and:
        succeeds "assembleRelease"
        result.assertTasksExecuted(tasks.release.allToInstall, tasks.release.extract, ":assembleRelease")
        result.assertTasksSkipped( tasks.release.allToInstall, tasks.release.extract, ":assembleRelease")

        executable("build/exe/main/release/app").assertExists()
        installation("build/install/main/release").exec().out == app.expectedOutput
    }

    @ToBeFixedForConfigurationCache
    def "skips compile and link tasks for library when source doesn't change"() {
        def lib = new CppLib()
        settingsFile << "rootProject.name = 'hello'"

        given:
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-library'
         """

        and:
        succeeds "assemble", "assembleRelease"

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(tasks.debug.allToLink, ":assemble")
        result.assertTasksSkipped(tasks.debug.allToLink, ":assemble")

        sharedLibrary("build/lib/main/debug/hello").assertExists()

        succeeds "assembleRelease"
        result.assertTasksExecuted(tasks.release.allToLink, tasks.release.extract, ":assembleRelease")
        result.assertTasksSkipped(tasks.release.allToLink, tasks.release.extract, ":assembleRelease")

        sharedLibrary("build/lib/main/release/hello").assertExists()
    }
}
