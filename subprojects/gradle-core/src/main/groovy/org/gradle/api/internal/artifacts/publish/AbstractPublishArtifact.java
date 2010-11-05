/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.publish;

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;

/**
 * @author Hans Dockter
 */
public abstract class AbstractPublishArtifact implements PublishArtifact {
    private TaskDependency taskDependency;

    public AbstractPublishArtifact(Object... tasks) {
        taskDependency = new DefaultTaskDependency();
        ((DefaultTaskDependency) taskDependency).add(tasks);
    }

    public TaskDependency getBuildDependencies() {
        return taskDependency;
    }

    public void setTaskDependency(TaskDependency taskDependency) {
        this.taskDependency = taskDependency;
    }

    @Override
    public String toString() {
        return String.format("%s %s:%s:%s:%s", getClass().getSimpleName(), getName(), getType(), getExtension(), getClassifier());
    }
}
