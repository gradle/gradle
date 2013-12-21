/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.gradleplugin.foundation.filters;

import org.gradle.foundation.ProjectView;
import org.gradle.foundation.TaskView;

/**
 * Interface for a filter that weeds out certain projects and tasks. Useful when trying to walk the projects and tasks.
 */
public interface ProjectAndTaskFilter {
    /**
     * Determines if the specified project should be allowed or not.
     *
     * @param project the project in question
     * @return true to allow it, false not to.
     */
    public boolean doesAllowProject(ProjectView project);

    /**
     * Determines if the specified task should be allowed or not.
     *
     * @param task the task in question
     * @return true to allow it, false not to.
     */
    public boolean doesAllowTask(TaskView task);
}
