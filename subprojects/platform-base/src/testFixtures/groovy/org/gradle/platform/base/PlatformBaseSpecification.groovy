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

package org.gradle.platform.base

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.language.base.ProjectSourceSet
import org.gradle.model.ModelMap
import org.gradle.model.internal.type.ModelTypes
import org.gradle.util.TestUtil
import spock.lang.Specification

abstract class PlatformBaseSpecification extends Specification {
    final def project = TestUtil.createRootProject()

    ModelMap<Task> realizeTasks() {
        project.modelRegistry.find("tasks", ModelTypes.modelMap(Task))
    }

    ComponentSpecContainer realizeComponents() {
        project.modelRegistry.find("components", ComponentSpecContainer)
    }

    ProjectSourceSet realizeSourceSets() {
        project.modelRegistry.find("sources", ProjectSourceSet)
    }

    BinaryContainer realizeBinaries() {
        project.modelRegistry.find("binaries", BinaryContainer)
    }

    def dsl(@DelegatesTo(Project) Closure closure) {
        closure.delegate = project
        closure()
        project.tasks.realize()
        project.bindAllModelRules()
    }
}
