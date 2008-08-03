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

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import org.gradle.api.plugins.Convention;
import org.gradle.api.internal.project.BuildScriptProcessor;

/**
 * <p>A <code>Project</code> represents a buildable thing, such as a java war or command-line application.  This
 * interface is the main API you use to interact with Gradle from your build script. Using <code>Project</code>, you can
 * programmatically access all Gradle's features.</p>
 *
 * <p>When your Gradle project's build script is evaluated, a <code>Project</code> instance is created, and the script
 * is executed against it.  Any property or method which your script uses which is not defined in the script is
 * delegated through to the associated <code>Project</code> object.  This means, that you can use any of the methods and
 * properties on the <code>Project</code> interface directly in your script.</p>
 *
 * <p>For example:
 * <pre>
 * createTask('some-task')  // Delegates to Project.createTask()
 * buildDir = file('build-dir') // Delegates to Project.file() and Project.setProperty()
 * </pre>
 * </p>
 *
 * <h3>Tasks</h3>
 *
 * <p>A project is essentially a collection of {@link Task} objects. Each task performs some basic piece of work, such
 * as compiling classes, or running unit tests, or zipping up a WAR file. You add tasks to a project using one of the
 * {@link #createTask(String)} methods.  You can locate existing tasks using one of the {@link #task(String)}
 * methods.</p>
 *
 * <p>Each task in a project is treated as a dynamic property, so that you can reference the task in your build script
 * using its name. See {@link #property(String)} for more details. For example:
 * <pre>
 * createTask('compile')
 * println compile.name
 * </pre>
 * </p>
 *
 * <h3>Dependencies</h3>
 *
 * <p>A project generally has a number of dependencies it needs in order to do its work.  Also, a project generally
 * produces a number of artifacts, which other projects can use.</p>
 *
 * <h3>Multi-project Builds</h3>
 *
 * <p>Projects are arranged into a hierarchy of projects. A project has a name, and a path which uniquely identifies it
 * in the hierarchy.</p>
 *
 * @author Hans Dockter
 */
public interface Project {
    public static final String DEFAULT_PROJECT_FILE = "build.gradle";

    public static final String DEFAULT_ARCHIVES_TASK_BASE_NAME = "archive";

    public static final String PATH_SEPARATOR = ":";

    public static final String DEFAULT_BUILD_DIR_NAME = "build";

    public static final String GRADLE_PROPERTIES = "gradle.properties";

    public static final String SYSTEM_PROP_PREFIX = "systemProp";

    public static final String TMP_DIR_NAME = ".gradle";

    public static final String CACHE_DIR_NAME = TMP_DIR_NAME + "/cache";

    /**
     * <p>Returns the root project for the hierarchy that this project belongs to.  In the case of a single-project
     * build, this method returns this project.</p>
     *
     * <p>You can access this property in your build script using <code>rootProject</code></p>
     *
     * @return The root project. Never returns null.
     */
    Project getRootProject();

    /**
     * <p>Returns the root directory of this project. The root directory is the directory which all relative file names
     * are interpreted against. The default value for the root directory is the directory containing the project's build
     * script.</p>
     *
     * <p>You can access this property in your build script using <code>rootDir</code></p>
     *
     * @return The root directory. Never returns null.
     */
    File getRootDir();

    /**
     * <p>Sets the root directory of this project.</p>
     *
     * <p>You can set this property in your build script using <code>rootDir = nnn</code></p>
     */
    void setRootDir(File rootDir);

    /**
     * <p>Returns the build directory of this project.  The build directory is the directory which all artifacts are
     * generated into.  The default value for the build directory is <code><i>rootDir</i>/build</code></p>
     *
     * <p>You can access this property in your build script using <code>buildDir</code></p>
     *
     * @return The build directory. Never returns null.
     */
    File getBuildDir();

    String getGradleUserHome();

    String getBuildFileName();

    String getBuildFileCacheName();

    /**
     * <p>Returns the parent project of this project, if any.</p>
     *
     * <p>You can access this property in your build script using <code>parent</code></p>
     *
     * @return The parent project, or null if this is the root project.
     */
    Project getParent();

    /**
     * <p>Returns the name of this project. The project's name is not necessarily unique within a project hierarchy. You
     * should use the {@link #getPath()} method for a unique identifier for the project.</p>
     *
     * <p>You can access this property in your build script using <code>name</code></p>
     *
     * @return The name of this project. Never return null.
     */
    String getName();

    /**
     * <p>Returns the direct children of this project.</p>
     *
     * <p>You can access this property in your build script using <code>childProjects</code></p>
     *
     * @return A map from child project name to child project. Returns an empty map if this this project does not have
     *         any children.
     */
    Map getChildProjects();

    /**
     * <p>Returns the set of projects which this project depends on.</p>
     *
     * <p>You can access this property in your build script using <code>childProjects</code></p>
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
     * <p>Returns this project. This method is useful in build scripts to explicitly access project properties and
     * methods. For example, using <code>project.name</code> can express intent better than using <code>name</code></p>
     *
     * <p>You can access this property in your build script using <code>project</code></p>
     *
     * @return This project. Never returns null.
     */
    Project getProject();

    /**
     * <p>Returns the set containing this project and its subprojects.</p>
     *
     * <p>You can access this property in your build script using <code>allprojects</code></p>
     *
     * @return The set of projects.
     */
    Set getAllprojects();

    /**
     * <p>Returns the set containing the subprojects of this project.</p>
     *
     * <p>You can access this property in your build script using <code>subprojects</code></p>
     *
     * @return The set of projects.  Returns an empty set if this project has no subprojects.
     */
    Set getSubprojects();

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
    Project usePlugin(Class pluginClass, Map customValues);

    /**
     * <p>Returns the {@link Task} from the project which has the same name the name argument. If no such task exists,
     * an exception is thrown.</p>
     *
     * <p>You can call this method in your build script using the task name. See {@link #property(String)} for more
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
     * <p>You can call this method in your build script using the task name followed by a code block.</p>
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
     * reference the task by name in your build script.  See {@link #property(String)} for more details</p>
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
     * reference the task by name in your build script.  See {@link #property(String)} for more details</p>
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
     * reference the task by name in your build script.  See {@link #property(String)} for more details</p>
     *
     * <p>If a task with the given name already exists in this project and the <code>override</code> option is not set
     * to true, an exception is thrown.</p>
     *
     * @param args The task creation options.
     * @param name The name of the task to be created
     * @return The newly created task object
     * @throws InvalidUserDataException If a task with the given name already exsists in this project.
     */
    Task createTask(Map args, String name) throws InvalidUserDataException;

    /**
     * <p>Creates a {@link Task} with the given name and adds it to this project. Before the task is returned, the given
     * action closure is passed to the task's {@link Task#doFirst(TaskAction)} method. A map of creation options can be
     * passed to this method to control how the task is created. See {@link #createTask(java.util.Map, String)} for the
     * available options.</p>
     *
     * <p>After the task is added to the project, it is made available as a property of the project, so that you can
     * reference the task by name in your build script.  See {@link #property(String)} for more details</p>
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
    Task createTask(Map args, String name, TaskAction action) throws InvalidUserDataException;

    String getArchivesTaskBaseName();

    void setArchivesTaskBaseName(String archivesBaseName);

    String getArchivesBaseName();

    void setArchivesBaseName(String archivesBaseName);

    String getPath();

    List<String> getDefaultTasks();

    void setDefaultTasks(List<String> defaultTasks);

    void dependsOn(String path);

    void dependsOn(String path, boolean evaluateDependsOnProject);

    Project evaluationDependsOn(String path);

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
     * <p>Returns a map of the tasks contained in this project.</p>
     *
     * @param recursive If true, returns the tasks of this project and its subprojects.  If false, returns the tasks of
     * just this project.
     * @return A map from project to a set of tasks.
     */
    SortedMap getAllTasks(boolean recursive);

    Set<Task> getTasksByName(String name, boolean recursive);

    File getProjectDir();

    /**
     * @param path An object which toString method value is interpreted as a relative path to the project dir
     */
    File file(Object path);

    /**
     * Returns a file which gets validated according to the validation type passed to the method. Possible validations
     * are: NONE, EXISTS, IS_FILE, IS_DIRECTORY
     *
     * @param path An object which toString method value is interpreted as a relative path to the project dir
     * @return a File, which path is the absolute path of the project directory plus the relative path of the method
     *         argument
     */
    File file(Object path, PathValidation validation);

    String absolutePath(String path);

    /**
     * <p>Returns the <code>AntBuilder</code> for this project. You can use this in your build script to execute ant
     * tasks</p>
     *
     * <p>You can access this property in your build script using <code>ant</code></p>
     *
     * @return The <code>AntBuilder</code> for this project. Never returns null.
     */
    AntBuilder getAnt();

    /**
     * <p>Return the {@link DependencyManager} for this project.</p>
     *
     * <p>You can access this property in your build script using <code>dependencies</code></p>
     *
     * @return The <code>DependencyManager</code>. Never returns null.
     */
    DependencyManager getDependencies();

    /**
     * <p>Return the {@link Convention} for this project.</p>
     *
     * <p>You can access this property in your build script using <code>convention</code>. You can also can also access
     * the properties of the convention object as if they were properties of this project. See {@link
     * #setProperty(String, Object)} and {@link #property(String)} for more details.</p>
     *
     * @return The <code>Convention</code>. Never returns null.
     */
    Convention getConvention();

    void setConvention(Convention convention);

    int depthCompare(Project otherProject);

    int getDepth();

    Map<String, Task> getTasks();

    Map getPluginApplyRegistry();

    public Project evaluate();

    public BuildScriptProcessor getBuildScriptProcessor();

    /**
     * <p>Executes the given {@link ProjectAction} against the subprojects of this project.</p>
     *
     * <p>You can call this method in your build script using <code>subprojects</code> followed by a code block.</p>
     *
     * @param action The action to execute.
     */
    public void subprojects(ProjectAction action);

    /**
     * <p>Executes the given {@link ProjectAction} against this project and its subprojects.</p>
     *
     * <p>You can call this method in your build script using <code>allprojects</code> followed by a code block.</p>
     *
     * @param action The action to execute.
     */
    public void allprojects(ProjectAction action);

    public void applyActions(Set<Project> projects, ProjectAction action);

    AfterEvaluateListener addAfterEvaluateListener(AfterEvaluateListener afterEvaluateListener);

    List<AfterEvaluateListener> getAfterEvaluateListeners();

    /**
     * <p>Determines if this project has the given property. See {@link #property(String)} for details of the properties
     * which are available for a project.</p>
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
