/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.api;

import groovy.lang.Closure;

import java.util.List;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public interface Task extends Comparable {
    final static String AUTOSKIP_PROPERTY_PREFIX = "skip.";
            
    /**
     *
     * @return the name of the task
     */
    String getName();

    /**
     *
     * @return The project this task belongs to.
     */
    Project getProject();

    /**
     *
     * @return the tasks list of action closures in the order they are executed
     */
    List getActions();

    /**
     *
     * @return a set of Strings containing the paths to the tasks this task depends on.
     */
    Set getDependsOn();

    /**
     * @return true if this task has been executed already, false otherwise. This is in particular relevant for
     * multiproject builds.
     */
    boolean getExecuted();

    /**
     *
     * @return the path of the task, which is equal to the path of the project plus the name of the task.
     */
    String getPath();

    /**
     * Adds the given task paths to the dependsOn task paths of this task. If the task path is a relative path
     * (which means just a name of a task), the path is interpreted as relative to the path of the project belonging
     * to this task. If the given path is absolute, the given path is used as is. That way you can directly refer to tasks of
     * other projects in a multiproject build. Although the reccomended way of establishing cross project dependencies,
     * is via the dependsOn method of the project object.
     * 
     * @param paths
     * @return the task object this method is applied to
     */
    Task dependsOn(Object[] paths);

    /**
     * Adds the given action closure to the beginning of the tasks action list.
     *
     * @param action
     * @return the task object this method is applied to
     */
    Task doFirst(Closure action);

    /**
     * Adds the given action closure to the end of the tasks action list.
     *
     * @param action
     * @return the task object this method is applied to
     */
    Task doLast(Closure action);

    /**
     * Removes all the actions of this task.
     *
     * @return the task object this method is applied to
     */
    Task deleteAllActions();

    Task lateInitialize(Closure configureClosure);

    Task afterDag(Closure configureClosure);

    boolean getLateInitialized();

    boolean getEnabled();
    
    void setEnabled(boolean enabled);

}
