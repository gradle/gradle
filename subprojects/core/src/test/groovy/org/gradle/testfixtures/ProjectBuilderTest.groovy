/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.testfixtures

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.problems.buildtree.ProblemDiagnosticsFactory
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.IncubationLogger
import org.gradle.util.internal.Resources
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicBoolean

class ProjectBuilderTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    @Rule
    public final Resources resources = new Resources()

    def "can create a root project"() {

        when:
        def project = ProjectBuilder.builder().build()

        then:
        project instanceof DefaultProject
        project.name == 'test'
        project.path == ':'
        project.projectDir.parentFile != null
        project.buildFile == project.file("build.gradle")
        project.gradle != null
        project.gradle.rootProject == project
        project.gradle.gradleHomeDir == project.file('gradleHome')
        project.gradle.gradleUserHomeDir == project.file('userHome')
    }

    def "can create a child project"() {

        when:
        def root = ProjectBuilder.builder().build()
        def child = ProjectBuilder.builder().withParent(root).build()

        then:
        child.name == 'test'
        child.path == ':test'
        child.projectDir == root.file("test")
        child.buildFile == child.file("build.gradle")
    }

    private Project buildProject() {
        ProjectBuilder.builder().withProjectDir(temporaryFolder.testDirectory).build()
    }

    def canCreateARootProjectWithAGivenProjectDir() {
        when:
        def project = buildProject()

        then:
        project.projectDir == temporaryFolder.testDirectory
        project.gradle.gradleHomeDir == project.file('gradleHome')
        project.gradle.gradleUserHomeDir == project.file('userHome')
    }

    def canApplyACustomPluginUsingClass() {
        when:
        def project = buildProject()
        project.apply plugin: CustomPlugin

        then:
        project.tasks.hello instanceof DefaultTask
    }

    def canApplyACustomPluginById() {
        when:
        def project = buildProject()
        project.apply plugin: 'custom-plugin'

        then:
        project.tasks.hello instanceof DefaultTask
    }

    def canApplyACustomPluginByType() {
        when:
        def project = buildProject()
        project.pluginManager.apply(CustomPlugin)

        then:
        project.tasks.hello instanceof DefaultTask
    }

    def canCreateAndExecuteACustomTask() {
        when:
        def project = buildProject()
        def task = project.task('custom', type: CustomTask)
        task.doStuff()

        then:
        task.property == 'some value'
    }

    @LeaksFileHandles("script jar is held open")
    def canApplyABuildScript() {
        when:
        def project = buildProject()
        project.apply from: resources.getResource('ProjectBuilderTest.gradle')

        then:
        project.tasks.hello instanceof DefaultTask
    }

    def "Can trigger afterEvaluate programmatically"() {
        setup:
        def latch = new AtomicBoolean()

        when:
        def project = buildProject()

        project.afterEvaluate {
            latch.getAndSet(true)
        }

        project.evaluate()

        then:
        noExceptionThrown()
        latch.get()
    }

    @Ignore
    @Issue("GRADLE-3136")
    def "Can trigger afterEvaluate programmatically after calling getTasksByName"() {
        setup:
        def latch = new AtomicBoolean()

        when:
        def project = buildProject()

        project.getTasksByName('myTask', true)

        project.afterEvaluate {
            latch.getAndSet(true)
        }

        project.evaluate()

        then:
        noExceptionThrown()
        latch.get()
    }

    def "ProjectBuilder can not be directly instantiated"() {
        expect:
        ProjectBuilder.constructors.size() == 0
    }

    def "does not emit deprecation warning when using the builder() method"() {
        given:
        def broadcaster = Mock(BuildOperationProgressEventEmitter)
        DeprecationLogger.init(Mock(ProblemDiagnosticsFactory), WarningMode.None, broadcaster)

        when:
        ProjectBuilder.builder()

        then:
        0 * broadcaster.progress(_)

        cleanup:
        IncubationLogger.reset()
    }
}


