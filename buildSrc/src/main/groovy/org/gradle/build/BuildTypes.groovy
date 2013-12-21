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
package org.gradle.build

import org.gradle.api.Project
import org.gradle.api.GradleException

class BuildTypes {

    private final Project project
    private final activeNames = []

    BuildTypes(Project project) {
        this.project = project
    }

    List<String> getActive() {
        new LinkedList(activeNames)
    }

    boolean isActive(String name) {
        name in activeNames
    }

    def methodMissing(String name, args) {
        args = args.toList()
        def properties = [:]
        if (args.first() instanceof Map) {
            properties.putAll(args.remove(0))
        }
        def tasks = args*.toString()

        register(name, tasks, properties)
    }

    private register(name, tasks, projectProperties) {
        project.task(name) {
            group = "Build Type"
            def abbreviation = name[0] + name[1..-1].replaceAll("[a-z]", "")
            def taskNames = project.gradle.startParameter.taskNames

            def usedName = taskNames.find { it in [name, abbreviation] }
            if (usedName) {
                activeNames << name
                def index = taskNames.indexOf(usedName)
                taskNames.remove((int)index)
                tasks.reverse().each {
                    taskNames.add(index, it)
                }
                projectProperties.each { k, v ->
                    if (!project.hasProperty(k)) {
                        project.ext."$k" = null
                    }
                    project."$k" = v
                }
            }

            doFirst {
                throw new GradleException("'$name' is a build type and has to be invoked directly, and its name can only be abbreviated to '$abbreviation'.")
            }
         }
    }

}