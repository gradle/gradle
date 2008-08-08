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
 * <p>A <code>Task</code> represents an single step of a build, such as compiling classes or generating javadoc.</p>
 *
 * @author Hans Dockter
 */
public interface Task extends Comparable {
    public static final String TASK_NAME = "name";

    public static final String TASK_TYPE = "type";

    public static final String TASK_DEPENDS_ON = "dependsOn";

    public static final String TASK_OVERWRITE = "overwrite";

    public final static String AUTOSKIP_PROPERTY_PREFIX = "skip.";
            
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

    void setDependsOn(Set dependsOnTasks);

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
    Task dependsOn(Object... paths);

    /**
     * Adds the given action closure to the beginning of the tasks action list.
     *
     * @param action
     * @return the task object this method is applied to
     */
    Task doFirst(TaskAction action);

    /**
     * Adds the given action closure to the end of the tasks action list.
     *
     * @param action
     * @return the task object this method is applied to
     */
    Task doLast(TaskAction action);

    /**
     * Removes all the actions of this task.
     *
     * @return the task object this method is applied to
     */
    Task deleteAllActions();

    /**
     * Returns if this task is enabled or not.
     *
     * @return
     * @see #setEnabled(boolean)
     */
    boolean getEnabled();

    /**
     * Set the enabled state of a task. If a task is disabled none of the task actions are executed. Disabling a task
     * does not prevent the execution of the task's this task depends on.
     *
     * @param enabled The enabled state of this task (true or false)
     */
    void setEnabled(boolean enabled);

    /**
     * Applies the statements of the closure against this task object.
     *
     * @param configureClosure The closure to be applied (can be null).
     * @return This task
     */
    Task configure(Closure configureClosure);

    /**
     * Returns whether this task is dag neutral or not.
     *
     * @see #setDagNeutral(boolean)
     */
    boolean isDagNeutral();

    /**
     * Set's the dag neutral state of the task. The concept of dag neutrality is important to improve the performance,
     * when two primary tasks are executed as part of one build (e.g. <code>gradle clean install</code>). Gradle
     * guarantees that executing two tasks at once has the same behavior than executing them one after another. If the
     * execution of the first task changes the state of the task execution graph (e.g. if a task action changes a
     * project property), Gradle needs to rebuild the task execution graph before the execution of the second task.
     * If the first task plus all its dependent tasks declare themselves as dag neutral, Gradle does not rebuild the
     * graph.
     *
     * @param dagNeutral
     */
    void setDagNeutral(boolean dagNeutral);

    /**
     * Returns the list of skip properties. The returned list can be used to add further skip properties.
     * If a system property with the same key as one of the skip properties is
     * set to a value different than <i>false</i>, none of the task actions are executed. It has the same effect
     * as disabling the task. Therefore when starting gradle it is enough to say <code>gradle -Dskip.test</code> to
     * skip a task. You may, but don't need to assign a value.
     *
     * @return List of skip properties. Returns empty list when no skip properties are assigned.
     */
    List<String> getSkipProperties();
}

