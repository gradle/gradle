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

package org.gradle.execution.taskpath;

import org.gradle.api.internal.project.ProjectInternal;

public class ResolvedTaskPath {
    private final String prefix;
    private final String taskName;
    private final ProjectInternal project;
    private final boolean isQualified;

    public ResolvedTaskPath(String prefix, String taskName, ProjectInternal project) {
        this.prefix = prefix;
        this.taskName = taskName;
        this.project = project;
        this.isQualified = prefix.length() > 0;
    }

    public boolean isQualified() {
        return isQualified;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getTaskName() {
        return taskName;
    }

    /**
     * @return for qualified path it returns the path the task lives in.
     * For unqualified path it returns the project the task path was searched from.
     */
    public ProjectInternal getProject() {
        return project;
    }

}
