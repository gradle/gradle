/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.openapi.external.foundation;

/**
 * This is an abstraction of a gradle task
 *
 * This is a mirror of TaskView inside Gradle, but this is meant
 * to aid backward and forward compatibility by shielding you from direct
 * changes within gradle.
 * @deprecated No replacement
 */
@Deprecated
public interface TaskVersion1 {

    /**
     * @return the project this task is associated with
     */
    public ProjectVersion1 getProject();

    /**
     * @return the name of this task
     */
    public String getName();

    /**
     * @return this tasks description
     */
    public String getDescription();

    /**
     * returns whether or not this is a default task for its parent project. These are defined by specifying
     *
     * defaultTasks 'task name'
     *
     * in the gradle file. There can be multiple default tasks.
     *
     * @return true if its a default task, false if not.
     */
    public boolean isDefault();

    /**
     * This generates this task's full name. This is a colon-separated string of this task and its parent projects.
     *
     * Example: root_project:sub_project:sub_sub_project:task.
     */
    public String getFullTaskName();
}
