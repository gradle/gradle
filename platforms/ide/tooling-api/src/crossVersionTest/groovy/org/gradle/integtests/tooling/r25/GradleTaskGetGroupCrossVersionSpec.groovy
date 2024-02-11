/*
 * Copyright 2015 the original author or authors.
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


package org.gradle.integtests.tooling.r25

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.gradle.BuildInvocations

class GradleTaskGetGroupCrossVersionSpec extends ToolingApiSpecification {

    def "provide getGroup on Task using GradleProject"() {
        file("build.gradle") << '''
task test1(group:'task group 1')
task test2(group:'task group 2')
'''

        when:
        def gradleProject = withConnection { ProjectConnection connection ->
            connection.getModel(GradleProject)
        }

        then:
        gradleProject != null
        gradleProject.tasks.findAll { it.name.startsWith('test') }.each {
            assert it.group == "task group ${it.name-'test'}"
        }
    }

    def "provide getGroup on Task using BuildInvocations"() {
        file("build.gradle") << '''
task test1(group:'task group 1')
task test2(group:'task group 2')
'''

        when:
        def buildInvocations = withConnection { ProjectConnection connection ->
            connection.getModel(BuildInvocations)
        }

        then:
        buildInvocations != null
        buildInvocations.tasks.findAll { it.name.startsWith('test') }.each {
            assert it.group == "task group ${it.name-'test'}"
        }
    }

    def "provide getGroup on Task using GradleProject shouldn't fail if group is null"() {
        file("build.gradle") << '''
task test1()
task test2()
'''

        when:
        def gradleProject = withConnection { ProjectConnection connection ->
            connection.getModel(GradleProject)
        }

        then:
        gradleProject != null
        gradleProject.tasks.findAll { it.name.startsWith('test') }.each {
            assert it.group == null
        }
    }

    def "provide getGroup on Task using BuildInvocations shouldn't fail if group is null"() {
        file("build.gradle") << '''
task test1()
task test2()
'''

        when:
        def buildInvocations = withConnection { ProjectConnection connection ->
            connection.getModel(BuildInvocations)
        }

        then:
        buildInvocations != null
        buildInvocations.tasks.findAll { it.name.startsWith('test') }.each {
            assert it.group == null
        }
    }

}
