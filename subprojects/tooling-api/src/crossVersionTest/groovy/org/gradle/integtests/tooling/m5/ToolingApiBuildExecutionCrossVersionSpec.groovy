/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.tooling.m5


import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.tooling.BuildException
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.Task
import org.gradle.tooling.model.eclipse.EclipseProject

class ToolingApiBuildExecutionCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {
    def "can build the set of tasks for a project"() {
        file('build.gradle') << '''
task a {
   description = 'this is task a'
}
task b
task c
'''

        when:
        GradleProject project = withConnection { connection -> connection.getModel(GradleProject.class) }

        then:
        project.tasks*.name.toSet() == (["a", "b", "c"] + rootProjectImplicitTasksForGradleProjectModel).toSet()
        def taskA = project.tasks.find { it.name == 'a' }
        taskA != null
        taskA.path == ':a'
        taskA.description == 'this is task a'
        taskA.project == project
    }

    def "can execute a build for a project"() {
        file('settings.gradle') << 'rootProject.name="test"'
        file('build.gradle') << '''
apply plugin: 'java'
'''
        when:
        withConnection { connection ->
            def build = connection.newBuild()
            build.forTasks('jar')
            build.run()
        }

        then:
        file('build/libs/test.jar').assertIsFile()

        and:
        assertHasBuildSuccessfulLogging()

        when:
        withConnection { connection ->
            GradleProject project = connection.getModel(GradleProject.class)
            Task clean = project.tasks.find { it.name == 'clean' }
            connection.newBuild()
                .forTasks(clean)
                .run()
        }

        then:
        file('build/libs/test.jar').assertDoesNotExist()
    }

    def "receives progress while the build is executing"() {
        file('build.gradle') << '''
System.out.println 'this is stdout'
System.err.println 'this is stderr'
'''
        when:
        def progress = withBuild().progressMessages

        then:
        progress.size() >= 2
        progress.removeLast() == ''
        progress.every { it }
    }

    def "tooling api reports build failure"() {
        file('build.gradle') << 'broken'

        when:
        withConnection {
            it.newBuild().forTasks('jar').run()
        }

        then:
        BuildException e = thrown()
        e.message.startsWith('Could not execute build using')
        e.cause.message.contains('A problem occurred evaluating root project')

        and:
        failure.assertHasDescription('A problem occurred evaluating root project')
        assertHasBuildFailedLogging()
    }

    def "can build the set of tasks for an Eclipse project"() {
        file('build.gradle') << '''
task a {
   description = 'this is task a'
}
task b
task c
'''

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        def taskA = project.gradleProject.tasks.find { it.name == 'a' }
        taskA != null
        taskA.path == ':a'
        taskA.description == 'this is task a'
        taskA.project == project.gradleProject
        project.gradleProject.tasks.find { it.name == 'b' }
        project.gradleProject.tasks.find { it.name == 'c' }
    }

    def "does not resolve dependencies when building the set of tasks for a project"() {
        file('build.gradle') << """
apply plugin: 'java'
dependencies {
    ${implementationConfiguration} files { throw new RuntimeException('broken') }
}
"""

        when:
        GradleProject project = withConnection { connection -> connection.getModel(GradleProject.class) }

        then:
        !project.tasks.empty
    }
}
