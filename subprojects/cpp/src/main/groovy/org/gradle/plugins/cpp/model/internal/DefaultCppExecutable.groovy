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
package org.gradle.plugins.cpp.model.internal

import org.gradle.api.tasks.TaskDependency
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.internal.file.FileResolver

import org.gradle.plugins.cpp.model.CppExecutable

class DefaultCppExecutable implements CppExecutable {

    private String name
    private file
    private DefaultTaskDependency taskDependency

    private FileResolver fileResolver

    DefaultCppExecutable(String name, FileResolver fileResolver) {
        this.name = name
        this.fileResolver = fileResolver
        this.taskDependency = new DefaultTaskDependency()
    }

    String getName() {
        name
    }

    TaskDependency getBuildDependencies() {
        taskDependency
    }

    void setFile(file) {
        this.file = file
    }

    DefaultCppExecutable file(file) {
        setFile(file)
        this
    }

    File getFile() {
        fileResolver.resolve(file)
    }

    DefaultCppExecutable builtBy(Object... tasks) {
        taskDependency.add(tasks)
        this
    }
}