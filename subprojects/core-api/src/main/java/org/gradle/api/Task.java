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

package org.gradle.api;

import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.LoggingManager;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskDestroyables;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.api.tasks.TaskLocalState;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.api.tasks.TaskState;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * <p>A <code>Task</code> represents a single atomic piece of work for a build, such as compiling classes or generating
 * javadoc.</p>
 *
 * <p>Each task belongs to a {@link Project}. You can use the various methods on {@link
 * org.gradle.api.tasks.TaskContainer} to create and lookup task instances. For example, {@link
 * org.gradle.api.tasks.TaskContainer#create(String)} creates an empty task with the given name. You can also use the
 * {@code task} keyword in your build file: </p>
 * <pre>
 * task myTask
 * task myTask { configure closure }
 * task myTask(type: SomeType)
 * task myTask(type: SomeType) { configure closure }
 * </pre>
 *
 * <p>Each task has a name, which can be used to refer to the task within its owning project, and a fully qualified
 * path, which is unique across all tasks in all projects. The path is the concatenation of the owning project's path
 * and the task's name. Path elements are separated using the {@value org.gradle.api.Project#PATH_SEPARATOR}
 * character.</p>
 *
 * <h3>Task Actions</h3>
 *
 * <p>A <code>Task</code> is made up of a sequence of {@link Action} objects. When the task is executed, each of the
 * actions is executed in turn, by calling {@link Action#execute}. You can add actions to a task by calling {@link
 * #doFirst(Action)} or {@link #doLast(Action)}.</p>
 *
 * <p>Groovy closures can also be used to provide a task action. When the action is executed, the closure is called with
 * the task as parameter.  You can add action closures to a task by calling {@link #doFirst(groovy.lang.Closure)} or
 * {@link #doLast(groovy.lang.Closure)}.</p>
 *
 * <p>There are 2 special exceptions which a task action can throw to abort execution and continue without failing the
 * build. A task action can abort execution of the action and continue to the next action of the task by throwing a
 * {@link org.gradle.api.tasks.StopActionException}. A task action can abort execution of the task and continue to the
 * next task by throwing a {@link org.gradle.api.tasks.StopExecutionException}. Using these exceptions allows you to
 * have precondition actions which skip execution of the task, or part of the task, if not true.</p>
 *
 * <a name="dependencies"></a><h3>Task Dependencies and Task Ordering</h3>
 *
 * <p>A task may have dependencies on other tasks or might be scheduled to always run after another task.
 * Gradle ensures that all task dependencies and ordering rules are honored when executing tasks, so that the task is executed after
 * all of its dependencies and any "must run after" tasks have been executed.</p>
 *
 * <p>Dependencies to a task are controlled using {@link #dependsOn(Object...)} or {@link #setDependsOn(Iterable)},
 * and {@link #mustRunAfter(Object...)}, {@link #setMustRunAfter(Iterable)}, {@link #shouldRunAfter(Object...)} and
 * {@link #setShouldRunAfter(Iterable)} are used to specify ordering between tasks. You can use objects of any of
 * the following types to specify dependencies and ordering:</p>
 *
 * <ul>
 *
 * <li>A {@code String}, {@code CharSequence} or {@code groovy.lang.GString} task path or name. A relative path is interpreted relative to the task's {@link Project}. This
 * allows you to refer to tasks in other projects.</li>
 *
 * <li>A {@link Task}.</li>
 *
 * <li>A closure. The closure may take a {@code Task} as parameter. It may return any of the types listed here. Its
 * return value is recursively converted to tasks. A {@code null} return value is treated as an empty collection.</li>
 *
 * <li>A {@link TaskDependency} object.</li>
 *
 * <li>A {@link Buildable} object.</li>
 *
 * <li>A {@link org.gradle.api.file.RegularFileProperty} or {@link org.gradle.api.file.DirectoryProperty}.</li>
 *
 * <li>A {@code Iterable}, {@code Collection}, {@code Map} or array. May contain any of the types listed here. The elements of the
 * iterable/collection/map/array are recursively converted to tasks.</li>
 *
 * <li>A {@code Callable}. The {@code call()} method may return any of the types listed here. Its return value is
 * recursively converted to tasks. A {@code null} return value is treated as an empty collection.</li>
 *
 * </ul>
 *
 * <h3>Using a Task in a Build File</h3>
 *
 * <a name="properties"></a> <h4>Dynamic Properties</h4>
 *
 * <p>A {@code Task} has 4 'scopes' for properties. You can access these properties by name from the build file or by
 * calling the {@link #property(String)} method. You can change the value of these properties by calling the {@link #setProperty(String, Object)} method.</p>
 *
 * <ul>
 *
 * <li>The {@code Task} object itself. This includes any property getters and setters declared by the {@code Task}
 * implementation class.  The properties of this scope are readable or writable based on the presence of the
 * corresponding getter and setter methods.</li>
 *
 * <li>The <em>extensions</em> added to the task by plugins. Each extension is available as a read-only property with the same
 * name as the extension.</li>
 *
 * <li>The <em>convention</em> properties added to the task by plugins. A plugin can add properties and methods to a task through
 * the task's {@link Convention} object.  The properties of this scope may be readable or writable, depending on the convention objects.</li>
 *
 * <li>The <em>extra properties</em> of the task. Each task object maintains a map of additional properties. These
 * are arbitrary name -&gt; value pairs which you can use to dynamically add properties to a task object.  Once defined, the properties
 * of this scope are readable and writable.</li>
 *
 * </ul>
 *
 * <h4>Dynamic Methods</h4>
 *
 * <p>A {@link Plugin} may add methods to a {@code Task} using its {@link Convention} object.</p>
 *
 * <h4>Parallel Execution</h4>
 * <p>
 * By default, tasks are not executed in parallel unless a task is waiting on asynchronous work and another task (which
 * is not dependent) is ready to execute.
 *
 * Parallel execution can be enabled by the <code>--parallel</code> flag when the build is initiated.
 * In parallel mode, the tasks of different projects (i.e. in a multi project build) are able to be executed in parallel.
 */
public interface Task extends Comparable<Task>, ExtensionAware {
    String TASK_NAME = "name";

    String TASK_DESCRIPTION = "description";

    String TASK_GROUP = "group";

    String TASK_TYPE = "type";

    String TASK_DEPENDS_ON = "dependsOn";

    String TASK_OVERWRITE = "overwrite";

    String TASK_ACTION = "action";

    /**
     * <p>Returns the name of this task. The name uniquely identifies the task within its {@link Project}.</p>
     *
     * @return The name of the task. Never returns null.
     */
    @Internal
    String getName();

    /**
     * A {@link org.gradle.api.Namer} namer for tasks that returns {@link #getName()}.
     */
    class Namer implements org.gradle.api.Namer<Task> {
        public String determineName(Task c) {
            return c.getName();
        }
    }

    /**
     * <p>Returns the {@link Project} which this task belongs to.</p>
     *
     * @return The project this task belongs to. Never returns null.
     */
    @Internal
    Project getProject();

    /**
     * <p>Returns the sequence of {@link Action} objects which will be executed by this task, in the order of
     * execution.</p>
     *
     * @return The task actions in the order they are executed. Returns an empty list if this task has no actions.
     */
    @Internal
    List<Action<? super Task>> getActions();

    /**
     * <p>Sets the sequence of {@link Action} objects which will be executed by this task.</p>
     *
     * @param actions The actions.
     */
    void setActions(List<Action<? super Task>> actions);

    /**
     * <p>Returns a {@link TaskDependency} which contains all the tasks that this task depends on.</p>
     *
     * @return The dependencies of this task. Never returns null.
     */
    @Internal
    TaskDependency getTaskDependencies();

    /**
     * <p>Returns the dependencies of this task.</p>
     *
     * @return The dependencies of this task. Returns an empty set if this task has no dependencies.
     */
    @Internal
    Set<Object> getDependsOn();

    /**
     * <p>Sets the dependencies of this task. See <a href="#dependencies">here</a> for a description of the types of
     * objects which can be used as task dependencies.</p>
     *
     * @param dependsOnTasks The set of task paths.
     */
    void setDependsOn(Iterable<?> dependsOnTasks);

    /**
     * <p>Adds the given dependencies to this task. See <a href="#dependencies">here</a> for a description of the types
     * of objects which can be used as task dependencies.</p>
     *
     * @param paths The dependencies to add to this task. The path can be defined by:
     * <ul>
     * <li>A {@code String}, {@code CharSequence} or {@code groovy.lang.GString} task path or name. A relative path is interpreted relative to the task's {@link Project}. This
     * allows you to refer to tasks in other projects.</li>
     *
     * <li>A {@link Task}.</li>
     *
     * <li>A closure. The closure may take a {@code Task} as parameter. It may return any of the types listed here. Its
     * return value is recursively converted to tasks. A {@code null} return value is treated as an empty collection.</li>
     *
     * <li>A {@link TaskDependency} object.</li>
     *
     * <li>A {@link org.gradle.api.tasks.TaskReference} object.</li>
     *
     * <li>A {@link Buildable} object.</li>
     *
     * <li>A {@link org.gradle.api.file.RegularFileProperty} or {@link org.gradle.api.file.DirectoryProperty}.</li>
     *
     * <li>A {@code Iterable}, {@code Collection}, {@code Map} or array. May contain any of the types listed here. The elements of the
     * iterable/collection/map/array are recursively converted to tasks.</li>
     *
     * <li>A {@code Callable}. The {@code call()} method may return any of the types listed here. Its return value is
     * recursively converted to tasks. A {@code null} return value is treated as an empty collection.</li>
     *
     * <li>Anything else is treated as a failure.</li>
     * </ul>
     *
     * @return the task object this method is applied to
     */
    Task dependsOn(Object... paths);

    /**
     * <p>Execute the task only if the given closure returns true.  The closure will be evaluated at task execution
     * time, not during configuration.  The closure will be passed a single parameter, this task. If the closure returns
     * false, the task will be skipped.</p>
     *
     * <p>You may add multiple such predicates. The task is skipped if any of the predicates return false.</p>
     *
     * <p>Typical usage:<code>myTask.onlyIf{ dependsOnTaskDidWork() } </code></p>
     *
     * @param onlyIfClosure code to execute to determine if task should be run
     */
    void onlyIf(Closure onlyIfClosure);

    /**
     * <p>Execute the task only if the given spec is satisfied. The spec will be evaluated at task execution time, not
     * during configuration. If the Spec is not satisfied, the task will be skipped.</p>
     *
     * <p>You may add multiple such predicates. The task is skipped if any of the predicates return false.</p>
     *
     * <p>Typical usage (from Java):</p>
     * <pre>myTask.onlyIf(new Spec&lt;Task&gt;() {
     *    boolean isSatisfiedBy(Task task) {
     *       return task.dependsOnTaskDidWork();
     *    }
     * });
     * </pre>
     *
     * @param onlyIfSpec specifies if a task should be run
     */
    void onlyIf(Spec<? super Task> onlyIfSpec);

    /**
     * <p>Execute the task only if the given closure returns true.  The closure will be evaluated at task execution
     * time, not during configuration.  The closure will be passed a single parameter, this task. If the closure returns
     * false, the task will be skipped.</p>
     *
     * <p>The given predicate replaces all such predicates for this task.</p>
     *
     * @param onlyIfClosure code to execute to determine if task should be run
     */
    void setOnlyIf(Closure onlyIfClosure);

    /**
     * <p>Execute the task only if the given spec is satisfied. The spec will be evaluated at task execution time, not
     * during configuration. If the Spec is not satisfied, the task will be skipped.</p>
     *
     * <p>The given predicate replaces all such predicates for this task.</p>
     *
     * @param onlyIfSpec specifies if a task should be run
     */
    void setOnlyIf(Spec<? super Task> onlyIfSpec);

    /**
     * Returns the execution state of this task. This provides information about the execution of this task, such as
     * whether it has executed, been skipped, has failed, etc.
     *
     * @return The execution state of this task. Never returns null.
     */
    @Internal
    TaskState getState();

    /**
     * Sets whether the task actually did any work.  Most built-in tasks will set this automatically, but
     * it may be useful to manually indicate this for custom user tasks.
     * <p>This is useful when combined with onlyIf { dependsOnTaskDidWork() }.
     * @param didWork indicates if the task did any work
     */
    void setDidWork(boolean didWork);

    /**
     * <p>Checks if the task actually did any work.  Even if a Task executes, it may determine that it has nothing to
     * do.  For example, a compilation task may determine that source files have not changed since the last time a the
     * task was run.</p>
     *
     * @return true if this task did any work
     */
    @Internal
    boolean getDidWork();

    /**
     * <p>Returns the path of the task, which is a fully qualified name for the task. The path of a task is the path of
     * its {@link Project} plus the name of the task, separated by <code>:</code>.</p>
     *
     * @return the path of the task, which is equal to the path of the project plus the name of the task.
     */
    @Internal
    String getPath();

    /**
     * <p>Adds the given {@link Action} to the beginning of this task's action list.</p>
     *
     * @param action The action to add
     * @return the task object this method is applied to
     */
    Task doFirst(Action<? super Task> action);

    /**
     * <p>Adds the given closure to the beginning of this task's action list. The closure is passed this task as a
     * parameter when executed.</p>
     *
     * @param action The action closure to execute.
     * @return This task.
     */
    Task doFirst(Closure action);

    /**
     * <p>Adds the given {@link Action} to the beginning of this task's action list.</p>
     *
     * @param actionName An arbitrary string that is used for logging.
     * @param action The action to add
     * @return the task object this method is applied to
     *
     * @since 4.2
     */
    @Incubating
    Task doFirst(String actionName, Action<? super Task> action);

    /**
     * <p>Adds the given {@link Action} to the end of this task's action list.</p>
     *
     * @param action The action to add.
     * @return the task object this method is applied to
     */
    Task doLast(Action<? super Task> action);

    /**
     * <p>Adds the given {@link Action} to the end of this task's action list.</p>
     *
     * @param actionName An arbitrary string that is used for logging.
     * @param action The action to add.
     * @return the task object this method is applied to
     *
     * @since 4.2
     */
    @Incubating
    Task doLast(String actionName, Action<? super Task> action);

    /**
     * <p>Adds the given closure to the end of this task's action list.  The closure is passed this task as a parameter
     * when executed.</p>
     *
     * @param action The action closure to execute.
     * @return This task.
     */
    Task doLast(Closure action);

    /**
     * <p>Adds the given closure to the end of this task's action list.  The closure is passed this task as a parameter
     * when executed. You can call this method from your build script using the &lt;&lt; left shift operator.</p>
     *
     * @param action The action closure to execute.
     * @return This task.
     *
     * @deprecated Use {@link #doLast(Closure action)}
     */
    @Deprecated
    Task leftShift(Closure action);

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
    @Internal
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
     * <p>Returns the <code>AntBuilder</code> for this task.  You can use this in your build file to execute ant
     * tasks.</p>
     *
     * @return The <code>AntBuilder</code>
     */
    @Internal
    AntBuilder getAnt();

    /**
     * <p>Returns the logger for this task. You can use this in your build file to write log messages.</p>
     *
     * @return The logger. Never returns null.
     */
    @Internal
    Logger getLogger();

    /**
     * Returns the {@link org.gradle.api.logging.LoggingManager} which can be used to receive logging and to control the
     * standard output/error capture for this task. By default, System.out is redirected to the Gradle logging system at
     * the QUIET log level, and System.err is redirected at the ERROR log level.
     *
     * @return the LoggingManager. Never returns null.
     */
    @Internal
    LoggingManager getLogging();

    /**
     * <p>Returns the value of the given property of this task.  This method locates a property as follows:</p>
     *
     * <ol>
     *
     * <li>If this task object has a property with the given name, return the value of the property.</li>
     *
     * <li>If this task has an extension with the given name, return the extension. </li>
     *
     * <li>If this task's convention object has a property with the given name, return the value of the property.</li>
     *
     * <li>If this task has an extra property with the given name, return the value of the property.</li>
     *
     * <li>If not found, throw {@link MissingPropertyException}</li>
     *
     * </ol>
     *
     * @param propertyName The name of the property.
     * @return The value of the property, possibly null.
     * @throws MissingPropertyException When the given property is unknown.
     */
    @Nullable
    Object property(String propertyName) throws MissingPropertyException;

    /**
     * <p>Determines if this task has the given property. See <a href="#properties">here</a> for details of the
     * properties which are available for a task.</p>
     *
     * @param propertyName The name of the property to locate.
     * @return True if this project has the given property, false otherwise.
     */
    boolean hasProperty(String propertyName);

    /**
     * <p>Sets a property of this task.  This method searches for a property with the given name in the following
     * locations, and sets the property on the first location where it finds the property.</p>
     *
     * <ol>
     *
     * <li>The task object itself.  For example, the <code>enabled</code> project property.</li>
     *
     * <li>The task's convention object.</li>
     *
     * <li>The task's extra properties.</li>
     *
     * </ol>
     *
     * If the property is not found, a {@link groovy.lang.MissingPropertyException} is thrown.
     *
     * @param name The name of the property
     * @param value The value of the property
     */
    void setProperty(String name, Object value) throws MissingPropertyException;

    /**
     * <p>Returns the {@link Convention} object for this task. A {@link Plugin} can use the convention object to
     * contribute properties and methods to this task.</p>
     *
     * @return The convention object. Never returns null.
     */
    @Internal
    Convention getConvention();

    /**
     * Returns the description of this task.
     *
     * @return the description. May return null.
     */
    @Internal
    @Nullable
    String getDescription();

    /**
     * Sets a description for this task. This should describe what the task does to the user of the build. The
     * description will be displayed when <code>gradle tasks</code> is called.
     *
     * @param description The description of the task. Might be null.
     */
    void setDescription(@Nullable String description);

    /**
     * Returns the task group which this task belongs to. The task group is used in reports and user interfaces to
     * group related tasks together when presenting a list of tasks to the user.
     *
     * @return The task group for this task. Might be null.
     */
    @Internal
    @Nullable
    String getGroup();

    /**
     * Sets the task group which this task belongs to. The task group is used in reports and user interfaces to
     * group related tasks together when presenting a list of tasks to the user.
     *
     * @param group The task group for this task. Can be null.
     */
    void setGroup(@Nullable String group);

    /**
     * <p>Checks if any of the tasks that this task depends on {@link Task#getDidWork() didWork}.</p>
     *
     * @return true if any task this task depends on did work.
     *
     * @deprecated Build logic should not depend on this information about a task. Instead, declare
     * task inputs and outputs to allow Gradle to optimize task execution.
     */
    @Deprecated
    boolean dependsOnTaskDidWork();

    /**
     * <p>Returns the inputs of this task.</p>
     *
     * @return The inputs. Never returns null.
     */
    @Internal
    TaskInputs getInputs();

    /**
     * <p>Returns the outputs of this task.</p>
     *
     * @return The outputs. Never returns null.
     */
    @Internal
    TaskOutputs getOutputs();

    /**
     * <p>Returns the destroyables of this task.</p>
     * @return The destroyables.  Never returns null.
     *
     * @since 4.0
     */
    @Incubating
    @Internal
    TaskDestroyables getDestroyables();

    /**
     * Returns the local state of this task.
     *
     * @since 4.3
     */
    @Incubating
    TaskLocalState getLocalState();

    /**
     * <p>Returns a directory which this task can use to write temporary files to. Each task instance is provided with a
     * separate temporary directory. There are no guarantees that the contents of this directory will be kept beyond the
     * execution of the task.</p>
     *
     * @return The directory. Never returns null. The directory will already exist.
     */
    @Internal
    File getTemporaryDir();

    /**
     * <p>Specifies that this task must run after all of the supplied tasks.</p>
     *
     * <pre class='autoTested'>
     * task taskY {
     *     mustRunAfter "taskX"
     * }
     * </pre>
     *
     * <p>For each supplied task, this action adds a task 'ordering', and does not specify a 'dependency' between the tasks.
     * As such, it is still possible to execute 'taskY' without first executing the 'taskX' in the example.</p>
     *
     * <p>See <a href="#dependencies">here</a> for a description of the types of objects which can be used to specify
     * an ordering relationship.</p>
     *
     * @param paths The tasks this task must run after.
     *
     * @return the task object this method is applied to
     */
    @Incubating
    Task mustRunAfter(Object... paths);

    /**
     * <p>Specifies the set of tasks that this task must run after.</p>
     *
     * <pre class='autoTested'>
     * task taskY {
     *     mustRunAfter = ["taskX1", "taskX2"]
     * }
     * </pre>
     *
     * <p>For each supplied task, this action adds a task 'ordering', and does not specify a 'dependency' between the tasks.
     * As such, it is still possible to execute 'taskY' without first executing the 'taskX' in the example.</p>
     *
     * <p>See <a href="#dependencies">here</a> for a description of the types of objects which can be used to specify
     * an ordering relationship.</p>
     *
     * @param mustRunAfter The set of task paths this task must run after.
     */
    @Incubating
    void setMustRunAfter(Iterable<?> mustRunAfter);

    /**
     * <p>Returns tasks that this task must run after.</p>
     *
     * @return The tasks that this task must run after. Returns an empty set if this task has no tasks it must run after.
     */
    @Incubating
    @Internal
    TaskDependency getMustRunAfter();

    /**
     * <p>Adds the given finalizer tasks for this task.</p>
     *
     * <pre class='autoTested'>
     * task taskY {
     *     finalizedBy "taskX"
     * }
     * </pre>
     *
     * <p>See <a href="#dependencies">here</a> for a description of the types of objects which can be used to specify
     * a finalizer task.</p>
     *
     * @param paths The tasks that finalize this task.
     *
     * @return the task object this method is applied to
     */
    @Incubating
    Task finalizedBy(Object... paths);

    /**
     * <p>Specifies the set of finalizer tasks for this task.</p>
     *
     * <pre class='autoTested'>
     * task taskY {
     *     finalizedBy = ["taskX1", "taskX2"]
     * }
     * </pre>
     *
     * <p>See <a href="#dependencies">here</a> for a description of the types of objects which can be used to specify
     * a finalizer task.</p>
     *
     * @param finalizedBy The tasks that finalize this task.
     */
    @Incubating
    void setFinalizedBy(Iterable<?> finalizedBy);

    /**
     * <p>Returns tasks that finalize this task.</p>
     *
     * @return The tasks that finalize this task. Returns an empty set if there are no finalising tasks for this task.
     */
    @Incubating
    @Internal
    TaskDependency getFinalizedBy();

    /**
     * <p>Specifies that this task should run after all of the supplied tasks.</p>
     *
     * <pre class='autoTested'>
     * task taskY {
     *     shouldRunAfter "taskX"
     * }
     * </pre>
     *
     * <p>For each supplied task, this action adds a task 'ordering', and does not specify a 'dependency' between the tasks.
     * As such, it is still possible to execute 'taskY' without first executing the 'taskX' in the example.</p>
     *
     * <p>See <a href="#dependencies">here</a> for a description of the types of objects which can be used to specify
     * an ordering relationship.</p>
     *
     * @param paths The tasks this task should run after.
     *
     * @return the task object this method is applied to
     */
    @Incubating
    TaskDependency shouldRunAfter(Object... paths);

    /**
     * <p>Specifies the set of tasks that this task should run after.</p>
     *
     * <pre class='autoTested'>
     * task taskY {
     *     shouldRunAfter = ["taskX1", "taskX2"]
     * }
     * </pre>
     *
     * <p>For each supplied task, this action adds a task 'ordering', and does not specify a 'dependency' between the tasks.
     * As such, it is still possible to execute 'taskY' without first executing the 'taskX' in the example.</p>
     *
     * <p>See <a href="#dependencies">here</a> for a description of the types of objects which can be used to specify
     * an ordering relationship.</p>
     *
     * @param shouldRunAfter The set of task paths this task should run after.
     */
    @Incubating
    void setShouldRunAfter(Iterable<?> shouldRunAfter);

    /**
     * <p>Returns tasks that this task should run after.</p>
     *
     * @return The tasks that this task should run after. Returns an empty set if this task has no tasks it must run after.
     */
    @Incubating
    @Internal
    TaskDependency getShouldRunAfter();
}
