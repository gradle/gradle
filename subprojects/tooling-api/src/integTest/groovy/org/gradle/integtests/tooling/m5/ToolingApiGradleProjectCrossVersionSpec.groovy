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
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.GradleTask

class ToolingApiGradleProjectCrossVersionSpec extends ToolingApiSpecification {

    def "provides tasks of a project"() {
        file('build.gradle') << '''
task a {
   description = 'this is task a'
}
task b
task c
'''

        when:
        GradleProject project = withConnection { it.getModel(GradleProject.class) }

        then:
        GradleTask taskA = project.tasks.find { it.name == 'a' }
        taskA.path == ':a'
        taskA.description == 'this is task a'
        taskA.project == project

        project.tasks.find { it.name == 'b' && it.path == ':b' }
        project.tasks.find { it.name == 'c' && it.path == ':c' }
    }

    def "provides hierarchy"() {
        file('settings.gradle') << "include 'a', 'a:b', 'a:c', 'a:c:d'"
        file('build.gradle') << '''
task rootTask
project (':a') { description = 'A rocks!' }
'''

        when:
        GradleProject project = withConnection { it.getModel(GradleProject.class) }

        then:
        project.tasks.find { it.name == 'rootTask' }

        project.children.size() == 1
        GradleProject a = project.children[0]
        a.name == 'a'
        a.path == ':a'
        a.description == 'A rocks!'

        a.children.size() == 2
        a.children.find { it.name == 'b' && it.path == ':a:b' }
        a.children.find { it.name == 'c' && it.path == ':a:c' }
        
        a.children.find { it.name == 'c'}.children[0].name == 'd'
    }

    def "can provide tasks for hierarchical project"() {
        file('settings.gradle') << "include 'a', 'a:b', 'a:c'"
        file('build.gradle') << '''
task rootTask
project(':a') { task taskA }
project(':a:b') { task taskAB }
project(':a:c') { task taskAC }

'''

        when:
        GradleProject project = withConnection { it.getModel(GradleProject.class) }

        then:
        project.tasks.find { it.name == 'rootTask' && it.project == project }
        !project.tasks.find { it.name == 'taskA' }

        GradleProject a = project.children[0]
        a.tasks.find { it.name == 'taskA' && it.project == a }
        !a.tasks.find { it.name == 'rootTask' }

        GradleProject ab = a.children.find { it.path == ':a:b' }
        ab.tasks.find { it.name == 'taskAB'}

        GradleProject ac = a.children.find { it.path == ':a:c' }
        ac.tasks.find { it.name == 'taskAC'}
    }
}
