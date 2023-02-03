/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.tasks.diagnostics.internal

import org.gradle.api.Task
import org.gradle.api.tasks.TaskDependency
import org.gradle.util.Path
import spock.lang.Specification

abstract class AbstractTaskModelSpec extends Specification {
    def taskDetails(String path) {
        return taskDetails([:], path)
    }

    def taskDetails(Map properties, String path) {
        TaskDetails task = Mock()
        _ * task.path >> Path.path(path)
        _ * task.toString() >> path
        _ * task.description >> properties.description
        _ * task.dependencies >> ((properties.dependencies ?: []) as Set)
        return task
    }

    def task(String name, String group = null, Task... dependencies) {
        Task task = Mock()
        _ * task.toString() >> name
        _ * task.name >> name
        _ * task.path >> ":$name"
        _ * task.group >> group
        _ * task.compareTo(!null) >> { args -> name.compareTo(args[0].name) }
        TaskDependency dep = Mock()
        _ * dep.getDependencies(task) >> {dependencies as Set}
        _ * task.taskDependencies >> dep
        return task
    }
}
