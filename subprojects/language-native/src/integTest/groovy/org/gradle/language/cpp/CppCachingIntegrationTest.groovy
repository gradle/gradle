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

import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibraries
import org.gradle.test.fixtures.file.TestFile

class CppCachingIntegrationTest extends AbstractCppInstalledToolChainIntegrationTest implements DirectoryBuildCacheFixture {
    CppAppWithLibraries app = new CppAppWithLibraries()
    String compilationTask = ':compileDebugCpp'

    def setupProjectInDirectory(TestFile project) {
        project.file('settings.gradle') << "include 'lib1', 'lib2'"
        project.file('build.gradle').text = """
            apply plugin: 'cpp-executable'
            dependencies {
                implementation project(':lib1')
            }

            project(':lib1') {
                apply plugin: 'cpp-library'
                dependencies {
                    implementation project(':lib2')
                }
            }
            project(':lib2') {
                apply plugin: 'cpp-library'
            }
        """
        app.greeterLib.writeToProject(project.file('lib1'))
        app.loggerLib.writeToProject(project.file("lib2"))
        app.main.writeToProject(project)
        executer.beforeExecute {
            withArgument("-Dorg.gradle.caching.native=true")
        }
    }

    def 'compilation can be cached'() {
        setupProjectInDirectory(temporaryFolder.testDirectory)

        when:
        withBuildCache().succeeds compilationTask

        then:
        compileIsNotCached()

        when:
        withBuildCache().succeeds 'clean', ':installDebug'

        then:
        compileIsCached()
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    void compileIsCached() {
        assert skippedTasks.contains(compilationTask)
        objectFileFor(file('src/main/cpp/main.cpp'), "build/obj/main/debug").assertExists()
    }

    void compileIsNotCached() {
        assert !skippedTasks.contains(compilationTask)
    }

}
