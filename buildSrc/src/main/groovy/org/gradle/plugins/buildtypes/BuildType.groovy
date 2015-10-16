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

class BuildType {
    String name
    List tasks
    Map projectProperties = [:]
    boolean active
    Closure propertiesAction

    BuildType(String name) {
        this.name = name
    }

    BuildType(String name, List tasks, Map projectProperties) {
        this(name)
        this.tasks = tasks
        this.projectProperties = projectProperties
    }

    void tasks(String... tasks) {
        this.tasks = tasks.toList()
    }

    void projectProperties(Map projectProperties) {
        this.projectProperties = projectProperties

        // this is so that when we configure the active buildType,
        // the project properties set for that buildType immediately
        // become active in the build
        if (active && propertiesAction != null) {
            propertiesAction.call(projectProperties)
        }
    }
}
