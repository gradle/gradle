/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.tooling.r12rc1

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject

class BuildModelCrossVersionSpec extends ToolingApiSpecification {
    def "can run tasks before building Eclipse model"() {
        file('build.gradle').text = '''
apply plugin: 'java'

task setup {
    doLast {
        println "run"
        project.description = 'this is a project'
    }
}
'''

        when:
        HierarchicalEclipseProject project = withConnection { ProjectConnection connection ->
            connection.model(HierarchicalEclipseProject.class).forTasks('setup').get()
        }

        then:
        project.description == 'this is a project'
    }

    def "#description means do not run any tasks even when default tasks defined"() {
        file('build.gradle') << """
            defaultTasks = ["broken"]

            gradle.taskGraph.whenReady {
                throw new RuntimeException()
            }
        """

        when:
        withConnection { ProjectConnection connection ->
            def builder = connection.model(HierarchicalEclipseProject.class)
            action(builder)
            builder.get()
        }

        then:
        noExceptionThrown()
        assertHasConfigureSuccessfulLogging()

        where:
        description                 | action
        "no task names specified"   | { ModelBuilder b -> }
        "empty array of task names" | { ModelBuilder b -> b.forTasks() }
        "empty list of task names"  | { ModelBuilder b -> b.forTasks([]) }
    }

    def "#description means do not run any tasks even when build logic injects tasks to execute"() {
        file('build.gradle') << """
            gradle.startParameter.taskNames = ["broken2"]

            gradle.taskGraph.whenReady {
                throw new RuntimeException()
            }
        """

        when:
        withConnection { ProjectConnection connection ->
            def builder = connection.model(HierarchicalEclipseProject.class)
            action(builder)
            builder.get()
        }

        then:
        noExceptionThrown()
        assertHasConfigureSuccessfulLogging()

        where:
        description                 | action
        "no task names specified"   | { ModelBuilder b -> }
        "empty array of task names" | { ModelBuilder b -> b.forTasks() }
        "empty list of task names"  | { ModelBuilder b -> b.forTasks([]) }
    }
}
