/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.scheduler

import javax.annotation.Nullable

class TaskNode extends Node {
    final String project
    final String name
    final Throwable failure

    TaskNode(String project, String name, @Nullable Throwable failure) {
        this.project = project
        this.name = name
        this.failure = failure
    }

    @Override
    boolean canExecuteInParallelWith(Node other) {
        return !(other instanceof TaskNode) || other.project != project
    }

    @Override
    Throwable execute() {
        println "Executing node $this"
        return failure
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        TaskNode that = (TaskNode) o

        if (project != that.project) return false
        if (name != that.name) return false

        return true
    }

    int hashCode() {
        int result
        result = project.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }

    @Override
    String toString() {
        return "$project:$name" + (failure ? " (failing)" : "")
    }
}
