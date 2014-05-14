/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import org.gradle.TaskParameter;

import java.io.Serializable;

/**
 * Adapter to create TaskParameter for a simple task name.
 */
public class DefaultTaskParameter implements TaskParameter, Serializable {
    private final String taskName;
    private final String projectPath;

    public DefaultTaskParameter(String taskName) {
        this(taskName, null);
    }

    public DefaultTaskParameter(String taskName, String projectPath) {
        this.taskName = Preconditions.checkNotNull(taskName);
        this.projectPath = projectPath;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getProjectPath() {
        return projectPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultTaskParameter that = (DefaultTaskParameter) o;
        if (!Objects.equal(projectPath, that.projectPath)) {
            return false;
        }
        if (!Objects.equal(taskName, that.taskName)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = taskName != null ? taskName.hashCode() : 0;
        result = 31 * result + (projectPath != null ? projectPath.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DefaultTaskParameter{"
                + "taskName='" + taskName + '\''
                + ",projectPath='" + projectPath + '\''
                + '}';
    }
}
