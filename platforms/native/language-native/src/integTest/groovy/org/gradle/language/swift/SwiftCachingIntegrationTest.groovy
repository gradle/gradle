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
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibraries
import org.gradle.test.fixtures.file.TestFile

@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC)
class SwiftCachingIntegrationTest extends AbstractInstalledToolChainIntegrationSpec implements DirectoryBuildCacheFixture {

    def app = new SwiftAppWithLibraries()

    def setupProject(TestFile project = temporaryFolder.testDirectory) {
        project.createDirs("hello", "log")
        project.file('settings.gradle') << '''
            include 'hello', 'log'
            rootProject.name = 'app'
        '''
        project.file('settings.gradle') << localCacheConfiguration()
        project.file('build.gradle').text = '''
            apply plugin: 'swift-application'
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
        app.application.writeToProject(project)
    }

    def 'compilation can be cached'() {
        setupProject()
        def allCompileTasks = ['', ':hello', ':log'].collect { compileTask(it, buildType) } as String[]

        when:
        withBuildCache().run compileTask(buildType)

        then:
        executedAndNotSkipped allCompileTasks

        when:
        withBuildCache().run 'clean', installTask(buildType)

        then:
        skipped allCompileTasks
        installation(installDir(buildType)).exec().out == app.expectedOutput

        where:
        buildType << ['Debug', 'Release']
    }

    def "compilation task is relocatable (#buildType)"() {
        def originalLocation = file('original-location')
        def newLocation = file('new-location')
        def allCompileTasks = ['', ':hello', ':log'].collect { compileTask(it, buildType) } as String[]
        setupProject(originalLocation)
        setupProject(newLocation)

        when:
        inDirectory(originalLocation)
        withBuildCache().run compileTask(buildType)

        then:
        executedAndNotSkipped allCompileTasks

        when:
        originalLocation.deleteDir()
        executer.beforeExecute {
            inDirectory(newLocation)
        }

        run 'clean'
        withBuildCache().run compileTask(buildType), installTask(buildType)

        then:
        skipped allCompileTasks
        installation(installDir(newLocation, buildType)).exec().out == app.expectedOutput

        where:
        buildType << ['Debug', 'Release']
    }

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
        executedAndNotSkipped compileTask(buildType)
        executedAndNotSkipped upstreamCompileTasks

        when:
        originalLocation.deleteDir()
        executer.beforeExecute {
            inDirectory(newLocation)
        }
        withBuildCache().run upstreamCompileTasks

        then:
        skipped upstreamCompileTasks

        when:
        run compileTask(buildType), installTask(buildType)

        then:
        skipped upstreamCompileTasks
        executedAndNotSkipped compileTask(buildType)
        installation(installDir(newLocation, buildType)).exec().out == app.expectedOutput

        where:
        buildType << ['Debug', 'Release']
    }

    TestFile installDir(TestFile projectDir = testDirectory, buildType) {
        projectDir.file("build/install/main/${buildType.toLowerCase()}")
    }

    String compileTask(String project = '', String buildType) {
        "${project}:compile${buildType}Swift"
    }

    String installTask(String project = '', String buildType) {
        "${project}:install${buildType}"
    }

}
