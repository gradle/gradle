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
    private DefaultTaskDependency taskDependency = new DefaultTaskDependency();

    public AbstractPublishArtifact(Object... tasks) {
        taskDependency.add(tasks);
    }

    public TaskDependency getTaskDependency() {
        return taskDependency;
    }

    public void setTaskDependency(DefaultTaskDependency taskDependency) {
        this.taskDependency = taskDependency;
    }

    // todo Discuss: What should be the equals rules for artifacts
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PublishArtifact that = (PublishArtifact) o;

        if (getClassifier() != null ? !getClassifier().equals(that.getClassifier()) : that.getClassifier() != null)
            return false;
        if (getExtension() != null ? !getExtension().equals(that.getExtension()) : that.getExtension() != null)
            return false;
        if (getName() != null ? !getName().equals(that.getName()) : that.getName() != null) return false;
        if (getType() != null ? !getType().equals(that.getType()) : that.getType() != null) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = (getName() != null ? getName().hashCode() : 0);
        result = 31 * result + (getExtension() != null ? getExtension().hashCode() : 0);
        result = 31 * result + (getType() != null ? getType().hashCode() : 0);
        result = 31 * result + (getClassifier() != null ? getClassifier().hashCode() : 0);
        return result;
    }
}
