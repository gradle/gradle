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

import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibraries
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

@Requires(TestPrecondition.SWIFT_SUPPORT)
class SwiftCachingIntegrationTest extends AbstractInstalledToolChainIntegrationSpec implements DirectoryBuildCacheFixture {

    def app = new SwiftAppWithLibraries()

    def setupProject(TestFile project = temporaryFolder.testDirectory) {
        project.file('settings.gradle') << '''
            include 'hello', 'log'
            rootProject.name = 'app'
        '''
        project.file('settings.gradle') << localCacheConfiguration()
        project.file('build.gradle').text = '''
            apply plugin: 'swift-executable'
            dependencies {
                implementation project(':hello')
            }

            project(':hello') {
                apply plugin: 'swift-library'
                dependencies {
                    api project(':log')
                }
            }
            project(':log') {
                apply plugin: 'swift-library'
            }
        '''
        app.logLibrary.writeToProject(project.file('log'))
        app.library.writeToProject(project.file('hello'))
        app.executable.writeToProject(project)
    }

    def 'compilation can be cached'() {
        setupProject()

        when:
        withBuildCache().run compileTask(buildType)

        then:
        compileIsNotCached(buildType)

        when:
        withBuildCache().run 'clean', installTask(buildType)

        then:
        compileIsCached(buildType)
        installation(installDir(buildType)).exec().out == app.expectedOutput

        where:
        buildType << ['Debug', 'Release']
    }

    @Unroll
    def "compilation task is relocatable (#buildType)"() {
        def originalLocation = file('original-location')
        def newLocation = file('new-location')
        setupProject(originalLocation)
        setupProject(newLocation)

        when:
        inDirectory(originalLocation)
        withBuildCache().run compileTask(buildType)

        then:
        compileIsNotCached(buildType)

        when:
        originalLocation.deleteDir()
        executer.beforeExecute {
            inDirectory(newLocation)
        }

        run 'clean'
        withBuildCache().run compileTask(buildType), installTask(buildType)

        then:
        compileIsCached(buildType, newLocation)
        installation(installDir(newLocation, buildType)).exec().out == app.expectedOutput

        where:
        buildType << ['Debug', 'Release']
    }

    @Unroll
    def "downstream compilation can use cached artifacts (#buildType)"() {
        def originalLocation = file('original-location')
        def newLocation = file('new-location')
        def upstreamCompileTasks = [compileTask(':hello', buildType), compileTask(':log', buildType)] as String[]
        setupProject(originalLocation)
        setupProject(newLocation)

        when:
        inDirectory(originalLocation)
        withBuildCache().run compileTask(buildType), installTask(buildType)

        then:
        compileIsNotCached(buildType)

        when:
        originalLocation.deleteDir()
        executer.beforeExecute {
            inDirectory(newLocation)
        }
        withBuildCache().succeeds upstreamCompileTasks

        then:
        skipped(upstreamCompileTasks)

        when:
        run compileTask(buildType), installTask(buildType)

        then:
        skipped(upstreamCompileTasks)
        compileIsNotCached(buildType)
        installation(installDir(newLocation, buildType)).exec().out == app.expectedOutput

        where:
        buildType << ['Debug', 'Release']
    }

    TestFile installDir(TestFile projectDir = testDirectory, buildType) {
        projectDir.file("build/install/main/${buildType.toLowerCase()}")
    }

    void compileIsCached(String buildType, projectDir = temporaryFolder.testDirectory) {
        skipped compileTask(buildType)
        // checking the object file only works in `temporaryFolder.testDirectory` since the base class has a hard coded reference to it
        if (projectDir == temporaryFolder.testDirectory) {
            objectFileFor(projectDir.file("src/main/${app.main.sourceFile.path}/${app.main.sourceFile.name}"), "build/obj/main/${buildType.toLowerCase()}").assertExists()
        }
    }

    void compileIsNotCached(String buildType) {
        executedAndNotSkipped compileTask(buildType)
    }

    String compileTask(String project = '', String buildType) {
        "${project}:compile${buildType}Swift"
    }

    String installTask(String project = '', String buildType) {
        "${project}:install${buildType}"
    }

}
