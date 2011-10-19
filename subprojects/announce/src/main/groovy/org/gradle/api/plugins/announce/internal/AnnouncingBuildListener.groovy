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
package org.gradle.api.plugins.announce.internal

import org.gradle.BuildAdapter
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.plugins.announce.Announcer
import org.gradle.api.Task
import org.gradle.api.tasks.TaskState
import org.gradle.BuildResult

class AnnouncingBuildListener extends BuildAdapter implements TaskExecutionListener {
    private final Announcer announcer
    private List<Task> failed = []
    private Task lastFailed
    private int taskCount

    AnnouncingBuildListener(Announcer announcer) {
        this.announcer = announcer
    }

    void beforeExecute(Task task) {
        taskCount++
        if (lastFailed) {
            announcer.send("$lastFailed failed", taskFailureCountMessage)
            lastFailed = null
        }
    }

    void afterExecute(Task task, TaskState state) {
        if (state.failure) {
            lastFailed = task
            failed << task
        }
    }

    @Override
    void buildFinished(BuildResult result) {
        if (result.failure != null) {
            if (failed.isEmpty()) {
                announcer.send("Build failed", taskCountMessage)
            } else {
                announcer.send("Build failed", taskFailureMessage + "\n" + taskCountMessage)
            }
        } else {
            announcer.send("Build successful", taskCountMessage)
        }
    }

    private String getTaskFailureCountMessage() {
        if (failed.size() == 1) {
            return "1 task failed"
        }
        return "${failed.size()} tasks failed"
    }

    private String getTaskFailureMessage() {
        if (failed.size() == 1) {
            return "${failed[0]} failed"
        } else {
            return "${failed.size()} tasks failed"
        }
    }

    private String getTaskCountMessage() {
        if (taskCount == 0) {
            return "No tasks executed"
        }
        if (taskCount == 1) {
            return "1 task executed"
        }
        return "$taskCount tasks executed"
    }
}
