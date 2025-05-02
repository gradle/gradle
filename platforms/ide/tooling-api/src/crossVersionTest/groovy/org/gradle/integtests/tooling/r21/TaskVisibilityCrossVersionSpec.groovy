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

package org.gradle.integtests.tooling.r21

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.Task
import org.gradle.tooling.model.TaskSelector
import org.gradle.tooling.model.gradle.BuildInvocations

class TaskVisibilityCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile << '''
include 'a'
include 'b'
include 'b:c'
rootProject.name = 'test'
'''
        buildFile << '''
task t1 {}
task t2 {
    group = 'foo'
}

project(':b') {
    task t3 {}
    task t2 {
        group = 'build'
    }
}

project(':b:c') {
    task t1 {
        group = 'build'
    }
    task t2 {
        group = 'build'
    }
}'''
    }

    def "task visibility is correct"() {
        def publicTasks = rootProjectImplicitTasks - rootProjectImplicitInvisibleTasks + ['t2']
        def publicSelectors = rootProjectImplicitSelectors - rootProjectImplicitInvisibleSelectors + ['t1', 't2']

        when:
        BuildInvocations model = withConnection { connection ->
            connection.getModel(BuildInvocations)
        }

        then:
        model.tasks.every { Task t -> publicTasks.contains(t.name) == t.public }
        model.taskSelectors.every { TaskSelector ts -> publicSelectors.contains(ts.name) == ts.public }
    }
}
