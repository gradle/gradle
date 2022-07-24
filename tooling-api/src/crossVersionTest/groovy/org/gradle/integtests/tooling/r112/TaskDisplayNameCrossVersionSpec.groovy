/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.integtests.tooling.r112

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.GradleProject

class TaskDisplayNameCrossVersionSpec extends ToolingApiSpecification {
    def "can get task's display name"() {
        file('build.gradle') << '''
task a
task b { description = 'this is task b' }
'''

        when:
        GradleProject project = withConnection { connection -> connection.getModel(GradleProject.class) }

        then:
        def taskA = project.tasks.find { it.name == 'a' }
        taskA != null
        taskA.path == ':a'
        taskA.displayName == /task ':a'/
        taskA.description == null
        taskA.project == project

        def taskB = project.tasks.find { it.name == 'b' }
        taskB != null
        taskB.path == ':b'
        taskB.displayName == /task ':b'/
        taskB.description == 'this is task b'
        taskB.project == project
    }
}
