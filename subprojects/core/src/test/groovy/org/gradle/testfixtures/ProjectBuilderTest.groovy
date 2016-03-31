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
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Resources
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicBoolean

class ProjectBuilderTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    @Rule
    public final Resources resources = new Resources()

    def canCreateARootProject() {

        when:
        def project = ProjectBuilder.builder().build()

        then:
        project instanceof DefaultProject
        project.name == 'test'
        project.path == ':'
        project.projectDir.parentFile != null
        project.gradle != null
        project.gradle.rootProject == project
        project.gradle.gradleHomeDir == project.file('gradleHome')
        project.gradle.gradleUserHomeDir == project.file('userHome')
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

    def canApplyABuildScript() {
        when:
        def project = buildProject()
        project.apply from: resources.getResource('ProjectBuilderTest.gradle')

        then:
        project.tasks.hello instanceof DefaultTask
    }

    def "Can trigger afterEvaluate programmatically"() {
        setup:
        def latch = new AtomicBoolean(false)

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
        def latch = new AtomicBoolean(false)

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
}


