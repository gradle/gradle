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

package org.gradle.api.internal.project

import org.gradle.api.*
import org.gradle.api.internal.DefaultTask
import org.gradle.api.internal.PathOrder
import org.gradle.api.tasks.Directory
import org.gradle.api.tasks.util.BaseDirConverter
import org.gradle.util.GradleUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
// todo add new public methods to interface
class DefaultProject implements Comparable, Project {
    private static Logger logger = LoggerFactory.getLogger(DefaultProject)

    static final int STATE_CREATED = 0

    static final int STATE_INITIALIZING = 1

    static final int STATE_INITIALIZED = 2

    static final String TASK_NAME = 'name'

    static final String TASK_TYPE = 'type'

    static final String TASK_DEPENDS_ON = 'dependsOn'

    static final String TASK_OVERWRITE = 'overwrite'

    static final String TASK_TYPE_LATE_INITIALIZER = 'lateInitializer'

    org.gradle.api.Project rootProject

    ProjectFactory projectFactory

    // This is an implementation detail of Project. Therefore we don't use IoC here.
    ProjectsTraverser projectsTraverser = new ProjectsTraverser()

    BuildScriptProcessor buildScriptProcessor

    BuildScriptFinder buildScriptFinder

    PluginRegistry pluginRegistry

    File rootDir

    org.gradle.api.Project parent

    String name

    SortedMap childProjects = new TreeMap()

    Map tasks = [:]

    Set dependsOnProjects = []

    Map additionalProperties = [:]

    int state

    Script projectScript

    List plugins = []

    BaseDirConverter baseDirConverter = new BaseDirConverter()

    AntBuilder ant = null

    DependencyManager dependencies

    String buildDirName = Project.DEFAULT_BUILD_DIR_NAME

    def convention

    Closure configureByDag = {}

    DefaultProject() {

    }

    DefaultProject(String name, DefaultProject parent, File rootDir, DefaultProject rootProject, ProjectFactory projectFactory,
                   DependencyManager dependencyManager, BuildScriptProcessor buildScriptProcessor, BuildScriptFinder buildScriptFinder,
                   PluginRegistry pluginRegistry) {
        assert name
        assert (!parent && !rootProject) || (parent && rootProject)
        this.rootProject = rootProject ?: this
        this.rootDir = rootDir
        this.parent = parent
        this.name = name
        this.projectFactory = projectFactory
        dependencyManager.project = this
        this.dependencies = dependencyManager
        this.buildScriptProcessor = buildScriptProcessor
        this.buildScriptFinder = buildScriptFinder
        this.pluginRegistry = pluginRegistry
        this.state = STATE_CREATED


    }

    /**
     * This method is used when scripts access the project via project.x
     */
    org.gradle.api.Project getProject() {
        this
    }

    List getAllprojects() {
        gatherProjects([this])
    }

    List getSubprojects() {
        gatherProjects(childProjects.values())
    }

    void subprojects(Closure configureClosure) {
        configureProjects(subprojects, configureClosure)
    }

    void allprojects(Closure configureClosure) {
        configureProjects(allprojects, configureClosure)
    }

    void configureProjects(List projects, Closure configureClosure) {
        projects.each {DefaultProject project ->
            GradleUtil.configure(configureClosure, project)
        }
    }

    private List gatherProjects(Collection rootCollection) {
        List projects = []
        projectsTraverser.traverse(rootCollection) {DefaultProject project -> projects << project}
        Collections.sort(projects)
        projects
    }

    DefaultProject evaluate() {
        if (state == STATE_INITIALIZED) {
            return this
        }
        state = STATE_INITIALIZING
        buildScriptProcessor.evaluate(this)
        state = STATE_INITIALIZED
        lateInitializeTasks(tasks)
        logger.info("Project=$path evaluated.")
        this
    }

    private void lateInitializeTasks(Map tasks) {
        while (true) {
            Set uninitializedTasks = tasks.values().findAll {Task task -> !task.lateInitialized}
            if (!uninitializedTasks) {break}
            uninitializedTasks.each {Task task ->
                task.applyLateInitialize()
            }
        }
    }

    Project usePlugin(String pluginName) {
        usePluginInternal(pluginName)
    }

    Project usePlugin(Class pluginClass) {
        usePluginInternal(pluginClass)
    }

    Project usePluginInternal(def pluginId) {
        Plugin plugin = pluginRegistry.getPlugin(pluginId)
        if (!plugin) {throw new InvalidUserDataException("Plugin with id $pluginId can not be found!")}
        plugin.apply(this, pluginRegistry)
        plugins << plugin
        this
    }

    Task task(String name) {
        task(name, null)
    }

    Task task(String name, Closure configureClosure) {
        if (!tasks[name]) {throw new InvalidUserDataException("Task with name=$name does not exists!")}
        if (configureClosure) {tasks[name].configure(configureClosure)}
        tasks[name]
    }

    Task createTask(String name) {
        createTask([:], name, null)
    }

    Task createTask(Map args, String name) {
        createTask(args, name, null)
    }

    Task createTask(String name, Closure action) {
        createTask([:], name, action)
    }

    Task createTask(Map args, String name, Closure action = null) {
        if (!name) {throw new InvalidUserDataException('The name of the task must be set!')}
        checkTaskArgsAndCreateDefaultValues(args)
        if (!args[TASK_OVERWRITE] && tasks[name]) {throw new InvalidUserDataException('A task with this name already exists!')}
        Task task = args[TASK_TYPE].metaClass.invokeConstructor([this, name] as Object[])
        task.lateInitalizeClosures = args[TASK_TYPE_LATE_INITIALIZER]
        tasks[name] = task
        if ((args[TASK_DEPENDS_ON] instanceof String) || (args[TASK_DEPENDS_ON] instanceof GString)) {
            String singleDependencyName = args[TASK_DEPENDS_ON]
            if (!singleDependencyName) {throw new InvalidUserDataException('A dependency name must not be empty!')}
            args[TASK_DEPENDS_ON] = [singleDependencyName]
        }
        logger.debug("Adding dependencies: ${args[TASK_DEPENDS_ON]}")

        task.dependsOn(args[TASK_DEPENDS_ON] as Object[])

        if (action) task.actions << action
        task
    }

    private void checkTaskArgsAndCreateDefaultValues(Map args) {
        if (!args[TASK_TYPE]) args[TASK_TYPE] = DefaultTask
        if (!args[TASK_DEPENDS_ON]) args[TASK_DEPENDS_ON] = []
        if (!args[TASK_TYPE_LATE_INITIALIZER]) args[TASK_TYPE_LATE_INITIALIZER] = []
        if (!args[TASK_OVERWRITE]) args[TASK_OVERWRITE] = false
    }

    DefaultProject addChildProject(String name) {
        childProjects[name] = projectFactory.createProject(name, this, rootDir, rootProject, projectFactory,
                buildScriptProcessor, buildScriptFinder, pluginRegistry)
    }

    String getPath() {
        String prefix = parent && parent.is(rootProject) ? '' : Project.PATH_SEPARATOR
        parent ? parent.path + prefix + name : Project.PATH_SEPARATOR
    }

    String getRelativeFilePath() {
        '/' + rootProject.name + '/' + path.substring(1).replace(Project.PATH_SEPARATOR, '/')
    }

    File getProjectDir() {
        new File(rootDir.parent, relativeFilePath)
    }

    File getBuildDir() {
        new File(projectDir, buildDirName)
    }

    void dependsOn(String path) {
        dependsOn(path, true)
    }

    void dependsOn(String path, boolean evaluateDependsOnProject) {
        if (!path) throw new InvalidUserDataException("You must specify a project!")
        dependsOnProjects << findProject(rootProject, absolutePath(path))
        if (evaluateDependsOnProject) {
            evaluationDependsOn(path)
        }
    }

    Project evaluationDependsOn(String path) {
        if (!path) throw new InvalidUserDataException("You must specify a project!")
        DefaultProject projectToEvaluate = findProject(rootProject, absolutePath(path))
        if (projectToEvaluate.state == DefaultProject.STATE_INITIALIZING) {
            throw new CircularReferenceException("Circular referencing during evaluation for project: $projectToEvaluate")
        }
        projectToEvaluate.evaluate()
    }

    Project childrenDependOnMe() {
        childProjects.values()*.dependsOn(this.path, false)
        this
    }

    Project dependsOnChildren() {
        dependsOnChildren(false)
    }

    Project dependsOnChildren(boolean evaluateDependsOnProject) {
        childProjects.values().each {dependsOn(it.path, evaluateDependsOnProject)}
        this
    }

    String toString() {
        path
    }

    Project project(String path) {
        project(path, null)
    }

    Project project(String path, Closure configureClosure = null) {
        if (!path) {
            throw new InvalidUserDataException("A path must be specified!")
        }
        Project project = findProject(rootProject,
                (isAbsolutePath(path)) ? path : absolutePath(path))
        GradleUtil.configure(configureClosure, project)
    }

    SortedMap getAllTasks(boolean recursive) {
        SortedMap foundTargets = new TreeMap()
        Closure select = {DefaultProject project ->
            foundTargets[project] = new TreeSet(project.tasks.values())
        }
        recursive ? projectsTraverser.traverse([this], select) : select(this)
        foundTargets
    }

    SortedMap getTasksByName(String name, boolean recursive) {
        if (!name) {
            throw new InvalidUserDataException('Name is not specified!')
        }
        SortedMap foundTargets = new TreeMap()
        Closure select = {DefaultProject project ->
            if (project.tasks[name]) {
                foundTargets[project] = project.tasks[name]
            }
        }
        recursive ? projectsTraverser.traverse([this], select) : select(this)
        foundTargets
    }

    File file(Object path) {
        file(path, PathValidation.NONE)
    }

    File file(Object path, PathValidation validation) {
        baseDirConverter.baseDir(path.toString(), projectDir, validation)
    }

    Task dir(String path) {
        String resultTaskName = path
        path.split('/').inject('') {name, pathElement ->
            name += (name ? "/$pathElement" : pathElement)
            if (tasks[name]) {
                if (!(task(name) instanceof Directory)) {
                    throw new InvalidUserDataException(
                            'A non directory task with this name already exsists.')
                }
            } else {
                createTask(name, type: Directory)
            }
            name
        }
        task(resultTaskName)
    }

    boolean equals(Object other) {
        path.equals(other.path)
    }

    public int hashCode() {
        return path.hashCode();
    }

    int compareTo(Object other) {
        PathOrder.compareTo(path, other.path)
    }

    public String absolutePath(String path) {
        if (!isAbsolutePath(path)) {
            String prefix = this.is(rootProject) ? '' : Project.PATH_SEPARATOR
            return "${this.path}$prefix$path"
        }
        path
    }

    static DefaultProject findProject(Project root, String absolutePath) {
        assert root
        assert DefaultProject.isAbsolutePath(absolutePath)
        logger.debug("Find project by absolute path: $absolutePath")
        absolutePath.split(Project.PATH_SEPARATOR).inject(root) {Project currentProject, String name ->
            if (currentProject.is(root) && !name) {return root}
            currentProject = currentProject.childProjects[name]
            if (!currentProject) throw new UnknownProjectException("Project with path $absolutePath could not be found")
            currentProject
        }
    }

    static boolean isAbsolutePath(String path) {
        assert path
        return path.startsWith(Project.PATH_SEPARATOR)
    }

    def propertyMissing(String name) {
        if (additionalProperties.keySet().contains(name)) {
            return additionalProperties[name]
        }
        if (convention?.metaClass?.hasProperty(convention, name)) {
            return convention."$name"
        }
        if (tasks[name]) {
            return tasks[name]
        }
        DefaultProject projectLooper = parent
        while (projectLooper) {
            if (projectLooper.additionalProperties.keySet().contains(name)) {
                return projectLooper."$name"
            } else if (projectLooper.convention?.metaClass?.hasProperty(convention, name)) {
                return projectLooper.convention."$name"
            }
            projectLooper = projectLooper.parent
        }
        throw new MissingPropertyException("$name is unknown property!")
    }

    boolean hasProperty(String name) {
        if (this.metaClass.hasProperty(this, name)) {return true}
        if (additionalProperties.keySet().contains(name)) {return true}
        if (convention?.metaClass?.hasProperty(convention, name)) {
            return true
        }
        DefaultProject projectLooper = parent
        while (projectLooper) {
            if (projectLooper.additionalProperties.keySet().contains(name)) {
                return true
            } else if (projectLooper.convention?.metaClass?.hasProperty(convention, name)) {
                return true
            }
            projectLooper = projectLooper.parent
        }

        tasks[name] ? true : false
    }

    def methodMissing(String name, args) {
        if (projectScript && projectScript.metaClass.respondsTo(projectScript, name, args)) {
            return projectScript.invokeMethod(name, args)
        }
        if (convention && convention.metaClass.respondsTo(convention, name, args)) {
            return convention.invokeMethod(name, args)
        }
        if (tasks[name] && args.size() == 1 && args[0] instanceof Closure) {
            return task(name, (Closure) args[0])
        }
        if (this.parent) {return this.parent.invokeMethod(name, args)}
        throw new MissingMethodException(name, this.class, args)
    }

    void setProperty(String name, value) {
        if (this.metaClass.hasProperty(this, name)) {
            this.metaClass.setProperty(this, name, value)
            return
        } else if (convention?.metaClass?.hasProperty(convention, name)) {
            convention.metaClass.setProperty(convention, name, value)
            return
        }
        project.additionalProperties[name] = value
    }

    AntBuilder getAnt() {
        if (ant == null) {
            ant = new AntBuilder()
            GradleUtil.setAntLogging(ant)
        }
        ant
    }

    AntBuilder ant(Closure configureClosure) {
        GradleUtil.configure(configureClosure, getAnt(), Closure.OWNER_FIRST)
    }

    DependencyManager dependencies(Closure configureClosure) {
        dependencies.configure(configureClosure)
    }
}