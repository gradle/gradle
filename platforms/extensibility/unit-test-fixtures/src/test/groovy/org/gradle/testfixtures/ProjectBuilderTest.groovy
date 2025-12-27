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
import org.gradle.api.problems.Problems
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.problems.buildtree.ProblemStream
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
    public final Resources resources = new Resources(null)

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
        def buildOperationProgressEventEmitter = Mock(BuildOperationProgressEventEmitter)
        DeprecationLogger.init(WarningMode.None, buildOperationProgressEventEmitter, Stub(Problems), Stub(ProblemStream))

        when:
        ProjectBuilder.builder()

        then:
        0 * buildOperationProgressEventEmitter.progress(_)

        cleanup:
        IncubationLogger.reset()
    }

    @Issue("https://github.com/gradle/gradle/issues/32928")
    def "settings directory matches root directory for root project with explicit dir"() {
        given:
        def projectDir = temporaryFolder.testDirectory

        when:
        def project = ProjectBuilder.builder()
            .withProjectDir(projectDir)
            .build()

        then:
        project.projectDir == projectDir
        project.rootDir == projectDir
        project.layout.settingsDirectory.asFile == projectDir
    }

    @Issue("https://github.com/gradle/gradle/issues/32928")
    def "settings directory matches root directory for default temp project"() {
        when:
        def project = ProjectBuilder.builder().build()

        then:
        project.projectDir == project.rootDir
        project.layout.settingsDirectory.asFile == project.rootDir
    }

    @Issue("https://github.com/gradle/gradle/issues/32928")
    def "child project inherits settings directory from root"() {
        given:
        def rootDir = temporaryFolder.testDirectory
        def root = ProjectBuilder.builder()
            .withProjectDir(rootDir)
            .build()

        when:
        def child = ProjectBuilder.builder()
            .withName("child")
            .withParent(root)
            .build()

        then:
        root.layout.settingsDirectory.asFile == rootDir
        child.layout.settingsDirectory.asFile == rootDir
        child.projectDir == new File(rootDir, "child")
        child.rootDir == rootDir
    }

    def "properties from gradle properties files are accessible"() {
        def rootDir = temporaryFolder.testDirectory.file("root-dir")
        def userHome = rootDir.file("user-home")

        rootDir.file("gradle.properties") << """
            foo=one
        """

        userHome.file("gradle.properties") << """
            bar=two
        """

        when:
        def project = ProjectBuilder.builder()
            .withProjectDir(rootDir)
            .withGradleUserHomeDir(userHome)
            .build()

        then:
        project.providers.gradleProperty("foo").getOrNull() == "one"
        project.providers.gradleProperty("bar").getOrNull() == "two"
    }
}
