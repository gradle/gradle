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

import org.gradle.tooling.model.BuildableProject

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
        BuildableProject project = withConnection { connection -> connection.getModel(BuildableProject.class) }

        then:
        def taskA = project.tasks.find { it.name == 'a' }
        taskA != null
        taskA.path == ':a'
        taskA.description == 'this is task a'
        project.tasks.find { it.name == 'b' }
        project.tasks.find { it.name == 'c' }
    }
}
