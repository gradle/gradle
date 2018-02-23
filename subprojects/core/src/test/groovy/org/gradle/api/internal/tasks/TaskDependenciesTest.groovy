/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks

import org.gradle.api.Task
import spock.lang.Specification

class TaskDependenciesTest extends Specification {
    def "creates empty dependencies"() {
        expect:
        TaskDependencies.of([]) == TaskDependencies.EMPTY
    }

    def "return dependency when set contains multiple items"() {
        def task1 = Stub(Task)
        def task2 = Stub(Task)

        expect:
        def deps = TaskDependencies.of([task1, task2])
        deps instanceof DefaultTaskDependency
        deps.getDependencies(null) == [task1, task2] as Set
    }
}
