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
import groovy.util.AntBuilder;

import java.util.List;
import java.util.Set;

import org.gradle.api.tasks.StopActionException;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.TaskDependency;
import org.slf4j.Logger;

/**
 * <p>A <code>Task</code> represents a single step of a build, such as compiling classes or generating javadoc.</p>
 *
 * <p>A task belongs to a {@link Project}. You can use the various methods on {@link Project} to create and lookup task
 * instances.</p>
 *
 * <h3>Task Actions</h3>
 *
 * <p>A <code>Task</code> is made up of a sequence of {@link TaskAction} objects. When the task is executed, each of the
 * actions is executed in turn, by calling {@link TaskAction#execute(Task)}.  You can add actions to a task by
 * calling {@link #doFirst(TaskAction)} or {@link #doLast(TaskAction)}.</p>
 *
 * <p>Groovy closures can also be used to provide a task action. When the action is executed, the closure is called with
 * the task as parameter.  You can add action closures to a task by calling {@link #doFirst(groovy.lang.Closure)} or
 * {@link #doLast(groovy.lang.Closure)}.</p>
 *
 * There are 2 special exceptions which a task action can throw to abort execution and continue without failing the
 * build. A task action can abort execution of the action and continue to the next action of the task by throwing a
 * {@link StopActionException}. A task action can abort execution of the task and continue to the next task by throwing
 * a {@link StopExecutionException}. Using these exceptions allows you to have precondition actions which skip execution
 * of the task, or part of the task, if not true.</p>
 *
 * <a name="dependencies"/><h3>Dependencies</h3>
 *
 * <p>A task may have dependencies on other tasks. Gradle ensures that tasks are executed in dependency order, so that
 * the dependencies of a task are executed before the task is executed.  You can add dependencies to a task using {@link
 * #dependsOn(Object[])} or {@link #setDependsOn(java.util.Set)}.  You can add objects of any of the following types as
 * a depedency:</p>
 *
 * <ul>
 *
 * <li>A string task path or name. A relative path is interpreted relative to the task's {@link Project}. This allows
 * you to refer to tasks in other projects, although the recommended way of establishing cross project dependencies, is
 * via the {@link Project#dependsOn(String)} method of the task's {@link Project}</li>
 *
 * <li>A {@link Task}.</li>
 *
 * <li>A closure. The closure may take a {@code Task} as parameter, and should return a {@code Task} or collection of
 * tasks.</li>
 *
 * <li>A {@link TaskDependency}.</li>
 *
 * <li>A {@code Collection} or {@code Map}. The collection/map is flattened and its elements added as described
 * above.</li>
 *
 * </ul>
 *
 * <h3>Using a Task in the Build File</h3>
 *
 * <p>A task generally provides no special build file behaviour, and can be used as a regular script object.</p>
 *
 * @author Hans Dockter
 */
public interface Task extends Comparable<Task> {
    public static final String TASK_NAME = "name";

    public static final String TASK_TYPE = "type";

    public static final String TASK_DEPENDS_ON = "dependsOn";

    public static final String TASK_OVERWRITE = "overwrite";

    public final static String AUTOSKIP_PROPERTY_PREFIX = "skip.";

    /**
     * </p>Returns the name of this task. The name uniquely identifies the task within its {@link Project}.</p>
     *
     * @return The name of the task. Never returns null.
     */
    String getName();

    /**
     * <p>Returns the {@link Project} which this task belongs to.</p>
     *
     * @return The project this task belongs to. Never returns null.
     */
    Project getProject();

    /**
     * <p>Returns the sequence of {@link TaskAction} objects which will be executed by this task, in the order of
     * execution.</p>
     *
     * @return The task actions in the order they are executed. Returns an empty list if this task has no actions.
     */
    List<TaskAction> getActions();

    /**
     * <p>Sets the sequence of {@link org.gradle.api.TaskAction} objects which will be executed by this task.</p>
     *
     * @param actions The actions.
     */
    void setActions(List<TaskAction> actions);

    /**
     * <p>Returns the {@link TaskDependency} which contains all the tasks that this task depends on.</p>
     *
     * @return The dependencies of this task. Never returns null.
     */
    TaskDependency getTaskDependencies();

    /**
     * <p>Returns the dependencies of this task.</p>
     *
     * @return The dependencies of this task. Returns an empty set if this task has no dependencies.
     */
    Set<Object> getDependsOn();

    /**
     * <p>Sets the dependencies of this task. See <a href="#dependencies">here</a> for a description of the types of
     * objects which can be used as task dependencies.</p>
     *
     * @param dependsOnTasks The set of task paths.
     */
    void setDependsOn(Set<?> dependsOnTasks);

    /**
     * <p>Returns true if this task has been executed.</p>
     *
     * @return true if this task has been executed already, false otherwise.
     */
    boolean getExecuted();

    /**
     * <p>Returns the path of the task, which is a fully qualified name for the task. The path of a task is the path of
     * its {@link Project} plus the name of the task, separated by <code>:</code>.</p>
     *
     * @return the path of the task, which is equal to the path of the project plus the name of the task.
     */
    String getPath();

    /**
     * <p>Adds the given dependencies to this task. See <a href="#dependencies">here</a> for a description of the types
     * of objects which can be used as task dependencies.</p>
     *
     * @param paths The dependencies to add to this task.
     * @return the task object this method is applied to
     */
    Task dependsOn(Object... paths);

    /**
     * <p>Adds the given {@link TaskAction} to the beginning of this task's action list.</p>
     *
     * @param action The action to add
     * @return the task object this method is applied to
     */
    Task doFirst(TaskAction action);

    /**
     * <p>Adds the given closure to the beginning of this task's action list. The closure is passed this task as a
     * parameter when executed.</p>
     *
     * @param action The action closure to execute.
     * @return This task.
     */
    Task doFirst(Closure action);

    /**
     * <p>Adds the given {@link TaskAction} to the end of this task's action list.</p>
     *
     * @param action The action to add.
     * @return the task object this method is applied to
     */
    Task doLast(TaskAction action);

    /**
     * <p>Adds the given closure to the end of this task's action list.  The closure is passed this task as a parameter
     * when executed.</p>
     *
     * @param action The action closure to execute.
     * @return This task.
     */
    Task doLast(Closure action);

    /**
     * <p>Removes all the actions of this task.</p>
     *
     * @return the task object this method is applied to
     */
    Task deleteAllActions();

    /**
     * <p>Returns if this task is enabled or not.</p>
     *
     * @see #setEnabled(boolean)
     */
    boolean getEnabled();

    /**
     * <p>Set the enabled state of a task. If a task is disabled none of the its actions are executed. Note that
     * disabling a task does not prevent the execution of the tasks which this task depends on.</p>
     *
     * @param enabled The enabled state of this task (true or false)
     */
    void setEnabled(boolean enabled);

    /**
     * <p>Applies the statements of the closure against this task object. The delegate object for the closure is set to
     * this task.</p>
     *
     * @param configureClosure The closure to be applied (can be null).
     * @return This task
     */
    Task configure(Closure configureClosure);

    /**
     * <p>Returns whether this task is dag neutral or not.</p>
     *
     * @see #setDagNeutral(boolean)
     */
    boolean isDagNeutral();

    /**
     * <p>Set's the dag neutral state of the task. The concept of dag neutrality is important to improve the
     * performance, when two primary tasks are executed as part of one build (e.g. <code>gradle clean install</code>).
     * Gradle guarantees that executing two tasks at once has the same behavior than executing them one after another.
     * If the execution of the first task changes the state of the task execution graph (e.g. if a task action changes a
     * project property), Gradle needs to rebuild the task execution graph before the execution of the second task. If
     * the first task plus all its dependent tasks declare themselves as dag neutral, Gradle does not rebuild the
     * graph.</p>
     */
    void setDagNeutral(boolean dagNeutral);

    /**
     * <p>Returns the list of skip properties. The returned list can be used to add further skip properties. If a system
     * property with the same key as one of the skip properties is set to a value different than <i>false</i>, none of
     * the task actions are executed. It has the same effect as disabling the task. Therefore when starting gradle it is
     * enough to say <code>gradle -Dskip.test</code> to skip a task. You may, but don't need to assign a value.</p>
     *
     * @return List of skip properties. Returns empty list when no skip properties are assigned.
     */
    List<String> getSkipProperties();

    /**
     * <p>Returns the <code>AntBuilder</code> for this task.  You can use this in your build file to execute ant
     * tasks.</p>
     *
     * @return The <code>AntBuilder</code>
     */
    AntBuilder getAnt();

    /**
     * <p>Returns the logger for this task. You can use this in your build file to write log messages.</p>
     *
     * @return The logger. Never returns null.
     */
    Logger getLogger();
}

