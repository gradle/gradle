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
package org.gradle.integtests.tooling

import org.gradle.tooling.BuildException
import org.gradle.tooling.ProgressListener
import org.gradle.tooling.model.BuildableProject
import org.gradle.tooling.model.Task
import org.gradle.tooling.model.eclipse.EclipseProject
import spock.lang.Issue

class ToolingApiBuildExecutionIntegrationTest extends ToolingApiSpecification {
    def "can build the set of tasks for a project"() {
        dist.testFile('build.gradle') << '''
task a {
   description = 'this is task a'
}
task b
task c
'''

        when:
        def project = withConnection { connection -> connection.getModel(BuildableProject.class) }

        then:
        def taskA = project.tasks.find { it.name == 'a' }
        taskA != null
        taskA.path == ':a'
        taskA.description == 'this is task a'
        taskA.project == project
        project.tasks.find { it.name == 'b' }
        project.tasks.find { it.name == 'c' }
    }

    @Issue("GRADLE-1529")
    //this is just one of the ways of fixing the problem. See the issue for details
    def "should not show not executable tasks"() {
        dist.testFile('build.gradle') << '''
task a
task b
'''
        when:
        def project = withConnection { connection -> connection.getModel(BuildableProject.class) }

        then:
        def tasks = project.tasks.collect { it.name }
        assert tasks == ['a', 'b'] : "temp tasks like 'cleanEclipse', 'eclipse', e.g. should not show on this list: " + tasks
    }

    @Issue("GRADLE-1529")
    //this is just one of the ways of fixing the problem. See the issue for details
    def "should hide not executable tasks when necessary for a multi module build"() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = '''
project(':api') {
    apply plugin: 'java'
    apply plugin: 'eclipse'
}
'''
        projectDir.file('settings.gradle').text = "include 'api', 'impl'"

        when:
        EclipseProject eclipseProject = withConnection { connection -> connection.getModel(EclipseProject.class) }

        then:
        def rootTasks = eclipseProject.tasks.collect { it.name }

        EclipseProject api = eclipseProject.children[1]
        def apiTasks = api.tasks.collect { it.name }

        EclipseProject impl = eclipseProject.children[0]
        def implTasks = impl.tasks.collect { it.name }

        ['eclipse', 'cleanEclipse', 'eclipseProject', 'cleanEclipseProject'].each {
            assert !rootTasks.contains(it)
            assert !implTasks.contains(it)

            assert apiTasks.contains(it)
        }
    }

    def "can execute a build for a project"() {
        dist.testFile('settings.gradle') << 'rootProject.name="test"'
        dist.testFile('build.gradle') << '''
apply plugin: 'java'
'''
        when:
        withConnection { connection ->
            def build = connection.newBuild()
            build.forTasks('jar')
            build.run()
        }

        then:
        dist.testFile('build/libs/test.jar').assertIsFile()

        when:
        withConnection { connection ->
            BuildableProject project = connection.getModel(BuildableProject.class)
            Task clean = project.tasks.find { it.name == 'clean' }
            def build = connection.newBuild()
            build.forTasks(clean)
            build.run()
        }

        then:
        dist.testFile('build/libs/test.jar').assertDoesNotExist()
    }

    def "receives progress and logging while the build is executing"() {
        dist.testFile('build.gradle') << '''
System.out.println 'this is stdout'
System.err.println 'this is stderr'
'''
        def stdout = new ByteArrayOutputStream()
        def stderr = new ByteArrayOutputStream()
        def progressMessages = []

        when:
        withConnection { connection ->
            def build = connection.newBuild()
            build.standardOutput = stdout
            build.standardError = stderr
            build.addProgressListener({ event -> progressMessages << event.description } as ProgressListener)
            build.run()
        }

        then:
        stdout.toString().contains('this is stdout')
        stderr.toString().contains('this is stderr')
        progressMessages.size() >= 2
        progressMessages.pop() == ''
        progressMessages.every { it }
    }

    def "tooling api reports build failure"() {
        dist.testFile('build.gradle') << 'broken'

        when:
        withConnection { connection ->
            return connection.newBuild().forTasks('jar').run()
        }

        then:
        BuildException e = thrown()
        e.message.startsWith('Could not execute build using Gradle')
        e.cause.message.contains('A problem occurred evaluating root project')
    }

    def "can build the set of tasks for an Eclipse project"() {
        dist.testFile('build.gradle') << '''
task a {
   description = 'this is task a'
}
task b
task c
'''

        when:
        def project = withConnection { connection -> connection.getModel(EclipseProject.class) }

        then:
        def taskA = project.tasks.find { it.name == 'a' }
        taskA != null
        taskA.path == ':a'
        taskA.description == 'this is task a'
        taskA.project == project
        project.tasks.find { it.name == 'b' }
        project.tasks.find { it.name == 'c' }
    }

    def "does not resolve dependencies when building the set of tasks for a project"() {
        dist.testFile('build.gradle') << '''
apply plugin: 'java'
dependencies {
    compile files { throw new RuntimeException('broken') }
}
'''

        when:
        def project = withConnection { connection -> connection.getModel(BuildableProject.class) }

        then:
        !project.tasks.empty
    }

}
