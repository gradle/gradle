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

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.GradleProject

@ToolingApiVersion('>=1.12')
@TargetGradleVersion('>=1.0-milestone-5')
class TaskDisplayNameCrossVersionSpec extends ToolingApiSpecification {
    def "can get task's display name introduced in 1.12"() {
        file('build.gradle') << '''
task a
'''

        when:
        GradleProject project = withConnection { connection -> connection.getModel(GradleProject.class) }

        then:
        def taskA = project.tasks.find { it.name == 'a' }
        taskA != null
        taskA.path == ':a'
        taskA.displayName == 'a task (:a)'
        taskA.project == project
    }
}
