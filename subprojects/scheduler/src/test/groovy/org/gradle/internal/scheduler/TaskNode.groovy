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

class TaskNode extends Node {
    final String project
    final String name
    final boolean fails
    private final NodeExecutionTracker executionTracker

    TaskNode(String project, String name, NodeExecutionTracker executionTracker, boolean fails) {
        this.executionTracker = executionTracker
        this.project = project
        this.name = name
        this.fails = fails
    }

    @Override
    boolean canExecuteInParallelWith(Node other) {
        return !(other instanceof TaskNode) || other.project != project
    }

    @Override
    void execute() {
        executionTracker.nodeExecuted(this)
        println "Executing ${fails ? " failing" : ""}task $this"
        if (fails) {
            setState(NodeState.FAILED)
        }
    }

    @Override
    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        TaskNode that = (TaskNode) o

        if (project != that.project) return false
        if (name != that.name) return false

        return true
    }

    @Override
    int hashCode() {
        int result
        result = project.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }

    @Override
    String toString() {
        return "$project:$name"
    }
}
