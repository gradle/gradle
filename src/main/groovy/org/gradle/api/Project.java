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
import groovy.lang.MissingPropertyException;
import groovy.util.AntBuilder;
import org.gradle.api.internal.project.BuildScriptProcessor;
import org.gradle.api.plugins.Convention;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

/**
 * <p>A <code>Project</code> represents a buildable thing, such as a java war or command-line application.  This
 * interface is the main API you use to interact with Gradle from your build file. From a <code>Project</code>, you can
 * programmatically access all Gradle's features.</p>
 *
 * <h3>Lifecycle</h3>
 *
 * <p>There is a one-to-one relationship between a <code>Project</code> and a <code>build.gradle</code> script. During
 * build initialisation, Gradle assembles a <code>Project</code> object for each project which is to participate in the
 * build, as follows:</p>
 *
 * <ul>
 *
 * <li>Create a {@link Settings} instance for the project</li>
 *
 * <li>Evaluate the <code>settings.gradle</code> script, if present, against the {@link Settings} object to configure
 * it.</li>
 *
 * <li>Use the configured {@link Settings} object to create a <code>Project</code> instance.</li>
 *
 * <li>Finally, evaluate the <code>Project</code> by executing the <code>build.gradle</code> script against it.</p>
 *
 * </ul>
 *
 * <h3>Tasks</h3>
 *
 * <p>A project is essentially a collection of {@link Task} objects. Each task performs some basic piece of work, such
 * as compiling classes, or running unit tests, or zipping up a WAR file. You add tasks to a project using one of the
 * {@link #createTask(String)} methods.  You can locate existing tasks using one of the {@link #task(String)}
 * methods.</p>
 *
 * <h3>Dependencies</h3>
 *
 * <p>A project generally has a number of dependencies it needs in order to do its work.  Also, a project generally
 * produces a number of artifacts, which other projects can use.  You use the {@link DependencyManager} returned by
 * {@link #getDependencies()} method to manage the dependencies and artifacts of the project.</p>
 *
 * <h3>Multi-project Builds</h3>
 *
 * <p>Projects are arranged into a hierarchy of projects. A project has a name, and a fully qualified path which
 * uniquely identifies it in the hierarchy.</p>
 *
 * <h3>Using a Project from the Build File</h3>
 *
 * <p>Gradle executes the project's build file against the <code>Project</code> instance to configure the project. Any
 * property or method which your script uses which is not defined in the script is delegated through to the associated
 * <code>Project</code> object.  This means, that you can use any of the methods and properties on the
 * <code>Project</code> interface directly in your script.</p>
 *
 * <p>For example:
 * <pre>
 * createTask('some-task')  // Delegates to Project.createTask()
 * reportDir = file('reports') // Delegates to Project.file() and Project.setProperty()
 * </pre>
 * </p>
 *
 * <p>You can also access the <code>Project</code> instance using the <code>project</code> property. This can make the
 * script more explicit and clearer in some cases.</p>
 *
 * <a name="properties"/> <h4>Properties</h4>
 *
 * <p>A project has 5 property 'scopes', which it searches for properties:</p>
 *
 * <ul>
 *
 * <li>The <code>Project</code> object itself. This scope includes any property getters and setters declared by the
 * <code>Project</code> implementation class.  For example, {@link #getRootProject()} is accessable as the
 * <code>rootProject</code> property.  The properties of this scope are readable or writable depending on the presence
 * of the corresponding getter or setter method.</li>
 *
 * <li>The <em>additional</em> properties of the project.  Each project maintains a map of additional properties, which
 * can contain any arbitrary name -> value pair.  The properties of this scope are readable and writable.</li>
 *
 * <li>The <em>convention</em> properties added to the project by each {@link Plugin} applied to the project. A {@link
 * Plugin} can add properties and method to a project through the project's {@link Convention} object.  The properties
 * of this scope may be readable or writable, depending on the convention objects.</li>
 *
 * <li>The tasks of the project.  A task is accessable as by using its name as the task name.  The properties of this
 * scope are read-only. For example, a task called <code>compile</code> is accessable as the <code>compile</code>
 * property.</li>
 *
 * <li>The additional properties and convention properties of the project's parent project. The properties of this scope
 * are read-only.</li>
 *
 * </ul>
 *
 * <p>When reading a property, the project searches the above scopes in order, and returns the value from the first
 * scope it finds the property in.  See {@link #property(String)} for more details.</p>
 *
 * <p>When writing a property, the project searched the above scopes in order, and sets the property in the first scope
 * it finds the property in.  If not found, the project adds the property to its map of additional properties. See
 * {@link #setProperty(String, Object)} for more details.</p>
 *
 * <h4>Methods</h4>
 *
 * <p>A project has 5 method 'scopes', which it searches for methods:</p>
 *
 * <ul>
 *
 * <li>The <code>Project</code> object itself.</li>
 *
 * <li>The build file.  The project searches for a matching method declared in the build file.</li>
 *
 * <li>The <em>convention</em> methods added to the project by each {@link Plugin} applied to the project. A {@link
 * Plugin} can add properties and method to a project through the project's {@link Convention} object.</li>
 *
 * <li>The tasks of the project. A method is added for each task, using the name of the task as the method name and
 * taking a single closure parameter. The method calls the {@link Task#configure(groovy.lang.Closure)} method for the
 * associated task with the provided closure.</li>
 *
 * <li>The parent project.</li>
 *
 * </ul>
 *
 * @author Hans Dockter
 */
public interface Project {
    /**
     * The default project build file name.
     */
    public static final String DEFAULT_PROJECT_FILE = "build.gradle";

    public static final String DEFAULT_ARCHIVES_TASK_BASE_NAME = "archive";

    /**
     * The hierarchy separator for project and task path names
     */
    public static final String PATH_SEPARATOR = ":";

    /**
     * The default build directory name.
     */
    public static final String DEFAULT_BUILD_DIR_NAME = "build";

    public static final String GRADLE_PROPERTIES = "gradle.properties";

    public static final String SYSTEM_PROP_PREFIX = "systemProp";

    public static final String TMP_DIR_NAME = ".gradle";

    public static final String CACHE_DIR_NAME = TMP_DIR_NAME + "/cache";

    /**
     * <p>Returns the root project for the hierarchy that this project belongs to.  In the case of a single-project
     * build, this method returns this project.</p>
     *
     * <p>You can access this property in your build file using <code>rootProject</code></p>
     *
     * @return The root project. Never returns null.
     */
    Project getRootProject();

    /**
     * <p>Returns the root directory of this project. The root directory is the directory which all relative file names
     * are interpreted against. The default value for the root directory is the directory containing the project's build
     * script.</p>
     *
     * <p>You can access this property in your build file using <code>rootDir</code></p>
     *
     * @return The root directory. Never returns null.
     */
    File getRootDir();

    /**
     * <p>Sets the root directory of this project.</p>
     *
     * <p>You can set this property in your build file using <code>rootDir = nnn</code></p>
     */
    void setRootDir(File rootDir);

    /**
     * <p>Returns the build directory of this project.  The build directory is the directory which all artifacts are
     * generated into.  The default value for the build directory is <code><i>rootDir</i>/build</code></p>
     *
     * <p>You can access this property in your build file using <code>buildDir</code></p>
     *
     * @return The build directory. Never returns null.
     */
    File getBuildDir();

    /**
     * <p>Returns the home directory for a Gradle user. Default's to <code><i>USER_HOME</i>/.gradle</code> </p>
     *
     * <p>You can access this property in your build file using <code>gradleUserHome</code></p>
     *
     * @return The Gradle user directory. Never returns null.
     */
    String getGradleUserHome();

    /**
     * <p>Returns the name of the build file Gradle tries to apply against this project object. The default is <code>
     * build.gradle</code>. The return value is independent on whether the project has a buildfile or not.</p>
     *
     * <p>You can access this property in your build file using <code>buildFileName</code></p>
     *
     * @return Current build file name. Never returns null.
     */
    String getBuildFileName();

    /**
     * <p>Returns the name of the cached build file corresponding to the build file Gradle tries to apply against this
     * project object. The default is <code>build_gradle</code>. The cached build file name is the same as the build
     * file name, except that dots are replaced with underscores. This transformation is necessary, as class names are
     * not allowed to have any dots. The return value is independent on whether the project has a buildfile or not.</p>
     *
     * @return Current build file cache name. Never returns null.
     */
    String getBuildFileCacheName();

    /**
     * <p>Returns the parent project of this project, if any.</p>
     *
     * <p>You can access this property in your build file using <code>parent</code></p>
     *
     * @return The parent project, or null if this is the root project.
     */
    Project getParent();

    /**
     * <p>Returns the name of this project. The project's name is not necessarily unique within a project hierarchy. You
     * should use the {@link #getPath()} method for a unique identifier for the project.</p>
     *
     * <p>You can access this property in your build file using <code>name</code></p>
     *
     * @return The name of this project. Never return null.
     */
    String getName();

    /**
     * <p>Returns the direct children of this project.</p>
     *
     * <p>You can access this property in your build file using <code>childProjects</code></p>
     *
     * @return A map from child project name to child project. Returns an empty map if this this project does not have
     *         any children.
     */
    Map<String, Project> getChildProjects();

    /**
     * <p>Returns the set of projects which this project depends on.</p>
     *
     * <p>You can access this property in your build file using <code>childProjects</code></p>
     *
     * @return The set of projects. Returns an empty set if this project depends on no projects.
     */
    Set<Project> getDependsOnProjects();

    /**
     * <p>Sets a property of this project.  This method searches for a property with the given name in the following
     * locations, and sets the property on the first location where it finds the property.</p>
     *
     * <ol>
     *
     * <li>The project object itself.  For example, the <code>rootDir</code> project property.</li>
     *
     * <li>The project's {@link Convention} object.  For example, the <code>srcRootName</code> java plugin
     * property.</li>
     *
     * <li>The project's additional properties.</li>
     *
     * </ol>
     *
     * <p>If the property is not found in any of these locations, it is added to the project's additional
     * properties.</p>
     *
     * @param name The name of the property
     * @param value The value of the property
     */
    void setProperty(String name, Object value);

    /**
     * <p>Returns this project. This method is useful in build files to explicitly access project properties and
     * methods. For example, using <code>project.name</code> can express intent better than using <code>name</code></p>
     *
     * <p>You can access this property in your build file using <code>project</code></p>
     *
     * @return This project. Never returns null.
     */
    Project getProject();

    /**
     * <p>Returns the set containing this project and its subprojects.</p>
     *
     * <p>You can access this property in your build file using <code>allprojects</code></p>
     *
     * @return The set of projects.
     */
    Set<Project> getAllprojects();

    /**
     * <p>Returns the set containing the subprojects of this project.</p>
     *
     * <p>You can access this property in your build file using <code>subprojects</code></p>
     *
     * @return The set of projects.  Returns an empty set if this project has no subprojects.
     */
    Set<Project> getSubprojects();

    /**
     * <p>Applies a {@link Plugin} to this project.</p>
     *
     * @param pluginName The name of the plugin.
     * @return This project.
     */
    Project usePlugin(String pluginName);

    /**
     * <p>Applies a {@link Plugin} to this project.</p>
     *
     * @param pluginName The name of the plugin.
     * @param customValues Configuration parameters to use to apply the plugin to this project.
     * @return This project.
     */
    Project usePlugin(String pluginName, Map customValues);

    /**
     * <p>Applies a {@link Plugin} to this project.</p>
     *
     * @param pluginClass The class of the plugin.  This class must implement the {@link Plugin} interface.
     * @param customValues Configuration parameters to use to apply the plugin to this project.
     * @return This project.
     */
    Project usePlugin(Class<? extends Plugin> pluginClass, Map customValues);

    /**
     * <p>Applies a {@link Plugin} to this project.</p>
     *
     * @param pluginClass The class of the plugin.  This class must implement the {@link Plugin} interface.
     * @return This project.
     */
    Project usePlugin(Class<? extends Plugin> pluginClass);

    /**
     * <p>Returns the {@link Task} from the project which has the same name the name argument. If no such task exists,
     * an exception is thrown.</p>
     *
     * <p>You can call this method in your build file using the task name. See <a href="#properties">here</a> for more
     * details.</p>
     *
     * @param name the name of the task to be returned
     * @return a task with the same name as the name argument. Never returns null.
     * @throws InvalidUserDataException If no task with the given name exists in this project.
     */
    Task task(String name) throws InvalidUserDataException;

    /**
     * <p>Returns the {@link Task} from this project which has the same name the name argument. Before the task is
     * returned, the given closure is passed to the task's {@link Task#configure(groovy.lang.Closure)} method. If no
     * such task exists, an exception is thrown.</p>
     *
     * <p>You can call this method in your build file using the task name followed by a code block.</p>
     *
     * @param name the name of the task to be returned
     * @param configurationClosure the closure to use to configure the task.
     * @return a task with the same name as the name argument. Never returns null.
     * @throws InvalidUserDataException If no task with the given name exists in this project.
     */
    Task task(String name, Closure configurationClosure) throws InvalidUserDataException;

    /**
     * <p>Creates a {@link Task} with the given name and adds it to this project. Calling this method is equivalent to
     * calling {@link #createTask(java.util.Map, String)} with an empty options map.</p>
     *
     * <p>After the task is added to the project, it is made available as a property of the project, so that you can
     * reference the task by name in your build file.  See <a href="#properties">here</a> for more details</p>
     *
     * <p>If a task with the given name already exists in this project, an exception is thrown.</p>
     *
     * @param name The name of the task to be created
     * @return The newly created task object
     * @throws InvalidUserDataException If a task with the given name already exists in this project.
     */
    Task createTask(String name) throws InvalidUserDataException;

    /**
     * <p>Creates a {@link Task} with the given name and adds it to this project. Before the task is returned, the given
     * action closure is passed to the task's {@link Task#doFirst(TaskAction)} method. Calling this method is equivalent
     * to calling {@link #createTask(java.util.Map, String, TaskAction)} with an empty options map.</p>
     *
     * <p>After the task is added to the project, it is made available as a property of the project, so that you can
     * reference the task by name in your build file.  See <a href="#properties">here</a> for more details</p>
     *
     * <p>If a task with the given name already exists in this project, an exception is thrown.</p>
     *
     * @param name The name of the task to be created
     * @param action The closure to be passed to the {@link Task#doFirst(TaskAction)} method of the created task.
     * @return The newly created task object
     * @throws InvalidUserDataException If a task with the given name already exsists in this project.
     */
    Task createTask(String name, TaskAction action) throws InvalidUserDataException;

    /**
     * <p>Creates a {@link Task} with the given name and adds it to this project. A map of creation options can be
     * passed to this method to control how the task is created. The following options are available:</p>
     *
     * <table>
     *
     * <tr><th>Option</th><th>Description</th><th>Default Value</th></tr>
     *
     * <tr><td><code>type</code></td><td>The class of the task to create.</td><td>{@link
     * org.gradle.api.internal.DefaultTask}</td></tr>
     *
     * <tr><td><code>overwrite</code></td><td>Replace an existing task?</td><td><code>false</code></td></tr>
     *
     * <tr><td><code>dependsOn</code></td><td>A task name or set of task names which this task depends
     * on</td><td><code>[]</code></td></tr>
     *
     * </table>
     *
     * <p>After the task is added to the project, it is made available as a property of the project, so that you can
     * reference the task by name in your build file.  See <a href="#properties">here</a> for more details</p>
     *
     * <p>If a task with the given name already exists in this project and the <code>override</code> option is not set
     * to true, an exception is thrown.</p>
     *
     * @param args The task creation options.
     * @param name The name of the task to be created
     * @return The newly created task object
     * @throws InvalidUserDataException If a task with the given name already exsists in this project.
     */
    Task createTask(Map<String, ?> args, String name) throws InvalidUserDataException;

    /**
     * <p>Creates a {@link Task} with the given name and adds it to this project. Before the task is returned, the given
     * action closure is passed to the task's {@link Task#doFirst(TaskAction)} method. A map of creation options can be
     * passed to this method to control how the task is created. See {@link #createTask(java.util.Map, String)} for the
     * available options.</p>
     *
     * <p>After the task is added to the project, it is made available as a property of the project, so that you can
     * reference the task by name in your build file.  See <a href="#properties">here</a> for more details</p>
     *
     * <p>If a task with the given name already exists in this project and the <code>override</code> option is not set
     * to true, an exception is thrown.</p>
     *
     * @param args The task creation options.
     * @param name The name of the task to be created
     * @param action The closure to be passed to the {@link Task#doFirst(TaskAction)} method of the created task.
     * @return The newly created task object
     * @throws InvalidUserDataException If a task with the given name already exsists in this project.
     */
    Task createTask(Map<String, ?> args, String name, TaskAction action) throws InvalidUserDataException;

    /**
     * <p>Creates a {@link Task} with the given name and adds it to this project. Before the task is returned, the given
     * action closure is passed to the task's {@link Task#doFirst(Closure)} method. Calling this method is equivalent to
     * calling {@link #createTask(java.util.Map, String, Closure)} with an empty options map.</p>
     *
     * <p>After the task is added to the project, it is made available as a property of the project, so that you can
     * reference the task by name in your build file.  See <a href="#properties">here</a> for more details</p>
     *
     * <p>If a task with the given name already exists in this project, an exception is thrown.</p>
     *
     * @param name The name of the task to be created
     * @param action The closure to be passed to the {@link Task#doFirst(Closure)} method of the created task.
     * @return The newly created task object
     * @throws InvalidUserDataException If a task with the given name already exsists in this project.
     */
    Task createTask(String name, Closure action);

    /**
     * <p>Creates a {@link Task} with the given name and adds it to this project. Before the task is returned, the given
     * action closure is passed to the task's {@link Task#doFirst(Closure)} method. A map of creation options can be
     * passed to this method to control how the task is created. See {@link #createTask(java.util.Map, String)} for the
     * available options.</p>
     *
     * <p>After the task is added to the project, it is made available as a property of the project, so that you can
     * reference the task by name in your build file.  See <a href="#properties">here</a> for more details</p>
     *
     * <p>If a task with the given name already exists in this project and the <code>override</code> option is not set
     * to true, an exception is thrown.</p>
     *
     * @param args The task creation options.
     * @param name The name of the task to be created
     * @param action The closure to be passed to the {@link Task#doFirst(Closure)} method of the created task.
     * @return The newly created task object
     * @throws InvalidUserDataException If a task with the given name already exsists in this project.
     */
    Task createTask(Map<String, ?> args, String name, Closure action);

    /**
     * <p>Returns the default prefix for naming archive tasks created via {@link org.gradle.api.tasks.bundling.Bundle}
     * tasks.</p>
     *
     * @return The archivesTaskBaseName (never null)
     * @see #setArchivesBaseName(String)
     */
    String getArchivesTaskBaseName();

    /**
     * <p>Sets the default prefix used for naming archive tasks. This is used if you create archive task via {@link
     * org.gradle.api.tasks.bundling.Bundle} tasks. For example <code>libs.jar()</code> creates an archive task with the
     * name of the default prefix plus <code> "_jar"</code>. The default for the default prefix is <i>archive</i>.</p>
     *
     * @param archivesTaskBaseName The value (never null) for the archivesTaskBaseName
     */
    void setArchivesTaskBaseName(String archivesTaskBaseName);

    /**
     * <p>Returns the default prefix for naming archives generated by bundle archive tasks.</p>
     *
     * @return The archivesBaseName (never null)
     * @see #setArchivesBaseName(String)
     */
    String getArchivesBaseName();

    /**
     * <p>The default prefix for naming the archives generated by archive task. This is used if you create archive task
     * via bundles. For example <code>libs.jar()</code> generates an archive with the name of the default prefix plus
     * <code> ".jar"</code>. The default for the default prefix is the name of the project.</p>
     *
     * @param archivesBaseName The value (never null) for the archivesBaseName
     */
    void setArchivesBaseName(String archivesBaseName);

    /**
     * <p>Returns the path of this project.  The path is the fully qualified name of the project.</p>
     *
     * @return The path. Never returns null.
     */
    String getPath();

    /**
     * <p>Returns the names of the default tasks of this project. These are used when no tasks names are provided when
     * starting the build.</p>
     *
     * @return The default task names. Returns an empty list if this project has no default tasks.
     */
    List<String> getDefaultTasks();

    /**
     * <p>Sets the names of the default tasks of this project. These are used when no tasks names are provided when
     * starting the build.</p>
     *
     * @param defaultTasks The default task names.
     */
    void setDefaultTasks(List<String> defaultTasks);

    /**
     * <p>Sets the names of the default tasks of this project. These are used when no tasks names are provided when
     * starting the build.</p>
     *
     * @param defaultTasks The default task names.
     */
    void defaultTasks(String... defaultTasks);

    /**
     * <p>Declares that this project has an execution dependency on the project with the given path.</p>
     *
     * @param path The path of the project which this project depends on.
     * @throws UnknownProjectException If no project with the given path exists.
     */
    void dependsOn(String path) throws UnknownProjectException;

    /**
     * <p>Declares that this project has an execution dependency on the project with the given path.</p>
     *
     * @param path The path of the project which this project depends on.
     * @param evaluateDependsOnProject If true, adds an evaluation dependency.
     * @throws UnknownProjectException If no project with the given path exists.
     */
    void dependsOn(String path, boolean evaluateDependsOnProject) throws UnknownProjectException;

    /**
     * <p>Declares that this project has an evaulation dependency on the project with the given path.</p>
     *
     * @param path The path of the project which this project depends on.
     * @throws UnknownProjectException If no project with the given path exists.
     */
    Project evaluationDependsOn(String path) throws UnknownProjectException;

    /**
     * <p>Declares that all child projects of this project have an execution dependency on this project.</p>
     *
     * @return this project.
     */
    Project childrenDependOnMe();

    /**
     * <p>Declares that this project have an execution dependency on each of its child projects.</p>
     *
     * @return this project.
     */
    Project dependsOnChildren();

    /**
     * <p>Declares that this project have an execution dependency on each of its child projects.</p>
     *
     * @param evaluateDependsOnProject If true, adds an evaluation dependency.
     * @return this project.
     */
    Project dependsOnChildren(boolean evaluateDependsOnProject);

    /**
     * <p>Locates a project by absolute path.</p>
     *
     * @param path The absolute path.
     * @return The project with the given path. Returns null if no such project exists.
     */
    Project findProject(String path);

    /**
     * <p>Locates a project by path. If the path is relative, it is interpreted relative to this project.</p>
     *
     * @param path The path.
     * @return The project with the given path. Never returns null.
     * @throws UnknownProjectException If no project with the given path exists.
     */
    Project project(String path) throws UnknownProjectException;

    /**
     * <p>Locates a project by path and configures it using the given closure. If the path is relative, it is
     * interpreted relative to this project.</p>
     *
     * @param path The path.
     * @return The project with the given path. Never returns null.
     * @throws UnknownProjectException If no project with the given path exists.
     */
    Project project(String path, Closure configureClosure);

    /**
     * <p>Returns a map of the tasks contained in this project, and optionally its subprojects.</p>
     *
     * @param recursive If true, returns the tasks of this project and its subprojects.  If false, returns the tasks of
     * just this project.
     * @return A map from project to a set of tasks.
     */
    SortedMap<Project, Set<Task>> getAllTasks(boolean recursive);

    /**
     * Returns the set of tasks with the given name contained in this project, and optionally its subprojects.</p>
     *
     * @param name The name of the task to locate.
     * @param recursive If true, returns the tasks of this project and its subprojects. If false, returns the tasks of
     * just this project.
     * @return The set of tasks. Returns an empty set if no such tasks exist in this project.
     */
    Set<Task> getTasksByName(String name, boolean recursive);

    /**
     * <p>The directory containing the project build file.</p>
     *
     * <p>You can access this property in your build file using <code>projectDir</code></p>
     *
     * @return The project directory. Never returns null.
     */
    File getProjectDir();

    /**
     * <p>Resolves a file path relative to the project directory of this project.</p>
     *
     * @param path An object which toString method value is interpreted as a relative path to the project directory.
     * @return The resolved file. Never returns null.
     */
    File file(Object path);

    /**
     * <p>Resolves a file path relative to the project directory of this project and validates it using the given
     * scheme. See {@link PathValidation} for the list of possible validations.</p>
     *
     * @param path An object which toString method value is interpreted as a relative path to the project directory.
     * @return The resolved file. Never returns null.
     * @throws InvalidUserDataException When the file does not meet the given validation constraint.
     */
    File file(Object path, PathValidation validation) throws InvalidUserDataException;

    /**
     * <p>Converts a name to an absolute project path, resolving names relative to this project.</p>
     *
     * @param path The path to convert.
     * @return The absolute path.
     */
    String absolutePath(String path);

    /**
     * <p>Returns the <code>AntBuilder</code> for this project. You can use this in your build file to execute ant
     * tasks.</p>
     *
     * <p>You can access this property in your build file using <code>ant</code></p>
     *
     * @return The <code>AntBuilder</code> for this project. Never returns null.
     */
    AntBuilder getAnt();

    /**
     * <p>Executes the given closure against the <code>AntBuilder</code> for this project. You can use this in your
     * build file to execute ant tasks.</p>
     *
     * <p>You can call this method in your build file using <code>ant</code> followed by a code block.</p>
     *
     * @param configureClosure The closure to execute against the <code>AntBuilder</code>. The closure receives no
     * paramters.
     * @return The <code>AntBuilder</code>. Never returns null.
     */
    AntBuilder ant(Closure configureClosure);

    /**
     * <p>Return the {@link DependencyManager} for this project.</p>
     *
     * <p>You can access this property in your build file using <code>dependencies</code></p>
     *
     * @return The <code>DependencyManager</code>. Never returns null.
     */
    DependencyManager getDependencies();

    /**
     * <p>Executes the given closure against the {@link DependencyManager} for this project.</p>
     *
     * <p>You can call this method in your build file using <code>dependencies</code> followed by a code block.</p>
     *
     * @param configureClosure The closure to execute against the {@link DependencyManager}. The closure receives no
     * parameters.
     * @return The <code>DependencyManager</code>. Never returns null.
     */
    DependencyManager dependencies(Closure configureClosure);

    /**
     * <p>Return the {@link Convention} for this project.</p>
     *
     * <p>You can access this property in your build file using <code>convention</code>. You can also can also access
     * the properties and methods of the convention object as if they were properties and methods of this project. See
     * <a href="#properties">here</a> for more details</p>
     *
     * @return The <code>Convention</code>. Never returns null.
     */
    Convention getConvention();

    /**
     * <p>Sets a {@link Convention} object for this project. Usually this is not needed as every project has a default
     * Convention object in use.</p>
     *
     * @param convention The convention object to set.
     * @see #getConvention()
     */
    void setConvention(Convention convention);

    /**
     * <p>Compares the nesting level of this project with another project of the multi-project hierarchy.</p>
     *
     * @param otherProject The project to compare the nesting level with.
     * @return a negative integer, zero, or a positive integer as this project has a nesting level less than, equal to,
     *         or greater than the specified object.
     * @see #getDepth()
     */
    int depthCompare(Project otherProject);

    /**
     * <p>Returns the nesting level of a project in a multi-project hierarchy. For single project builds this is always
     * 0. In a multi-project hierarchy 0 is returned for the root project.</p>
     */
    int getDepth();

    /**
     * <p>Returns the tasks of this project.</p>
     *
     * @return A map from task name to {@link Task} object. Returns an empty map when this project has no tasks.
     */
    Map<String, Task> getTasks();

    /**
     * <p>Returns the set of plugin types which have been applied to this project.</p>
     *
     * @return A set with class objects of plugins applied against this project. Returns an empty set if no plugins have
     *         been applied.
     */
    Set<Class> getAppliedPlugins();

    Project evaluate();

    BuildScriptProcessor getBuildScriptProcessor();

    /**
     * <p>Executes the given {@link ProjectAction} against the subprojects of this project.</p>
     *
     * @param action The action to execute.
     */
    void subprojects(ProjectAction action);

    /**
     * <p>Executes the given closure against each of the subprojects of this project.</p>
     *
     * <p>You can call this method in your build file using <code>subprojects</code> followed by a code block.</p>
     *
     * @param configureClosure The closure to execute. The closure receives no parameters.
     */
    void subprojects(Closure configureClosure);

    /**
     * <p>Executes the given {@link ProjectAction} against this project and its subprojects.</p>
     *
     * @param action The action to execute.
     */
    void allprojects(ProjectAction action);

    /**
     * <p>Executes the given closure against this project and its subprojects.</p>
     *
     * <p>You can call this method in your build file using <code>allprojects</code> followed by a code block.</p>
     *
     * @param configureClosure The closure to execute. The closure receives no parameters.
     */
    void allprojects(Closure configureClosure);

    /**
     *
     * @param projects
     * @param action
     */
    void applyActions(Set<Project> projects, ProjectAction action);

    /**
     * <p>Adds an {@link AfterEvaluateListener} to this project. Such a listener gets notified when the build file
     * belonging to this project has been executed. A parent project may for example add such a listener to its child
     * project. Such a listener can futher configure those child projects based on the state of the child projects after
     * there build files have been run.</p>
     *
     * @param afterEvaluateListener The listener (never null) to be added.
     * @return The added afterEvaluateListener
     * @see org.gradle.api.AfterEvaluateListener
     */
    AfterEvaluateListener addAfterEvaluateListener(AfterEvaluateListener afterEvaluateListener);

    /**
     * <p>Adds the given closure as an {@link AfterEvaluateListener}. See {@link #addAfterEvaluateListener(AfterEvaluateListener)}
     * for more details.</p>
     *
     * @param afterEvaluateListener The listener to be added.
     */
    void addAfterEvaluateListener(Closure afterEvaluateListener);

    /**
     * <p>Returns all {@link AfterEvaluateListener}s of this project.</p>
     *
     * @see #addAfterEvaluateListener(AfterEvaluateListener)
     * @see org.gradle.api.AfterEvaluateListener
     */
    List<AfterEvaluateListener> getAfterEvaluateListeners();

    /**
     * <p>Determines if this project has the given property. See <a href="#properties">here</a> for details of the
     * properties which are available for a project.</p>
     *
     * @param propertyName The name of the property to locate.
     * @return True if this project has the given property, false otherwise.
     */
    boolean hasProperty(String propertyName);

    /**
     * Returns the value of the given property.  This method locates a property as follows:</p>
     *
     * <ol>
     *
     * <li>If this project object has a property with the given name, return the value of the property.</li>
     *
     * <li>If this project's convention object has a property with the given name, return the value of the
     * property.</li>
     *
     * <li>If this project has an additional property with the given name, return the value of the property.</li>
     *
     * <li>If this project has a task with the given name, return the task.</li>
     *
     * <li>Search up through this project's ancestor projects for a convention property or additional property with the
     * given name.</li>
     *
     * <li>If not found, throw {@link MissingPropertyException}</li>
     *
     * </ol>
     *
     * @param propertyName The name of the property.
     * @throws MissingPropertyException When the given property is unknown.
     */
    Object property(String propertyName) throws MissingPropertyException;
}
