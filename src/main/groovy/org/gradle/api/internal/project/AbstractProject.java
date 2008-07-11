/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.project;

import groovy.lang.Closure;
import groovy.lang.GString;
import groovy.lang.Script;
import groovy.util.AntBuilder;
import org.gradle.api.*;
import org.gradle.api.internal.DefaultTask;
import org.gradle.api.plugins.Convention;
import org.gradle.api.tasks.Directory;
import org.gradle.api.tasks.util.BaseDirConverter;
import org.gradle.util.Clock;
import org.gradle.util.GUtil;
import org.gradle.util.GradleUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.*;

/**
 * @author Hans Dockter
 */
public abstract class AbstractProject implements Project, Comparable {
    private static Logger logger = LoggerFactory.getLogger(AbstractProject.class);

    public static final int STATE_CREATED = 0;

    public static final int STATE_INITIALIZING = 1;

    public static final int STATE_INITIALIZED = 2;

    public static final String TASK_NAME = "name";

    public static final String TASK_TYPE = "type";

    public static final String TASK_DEPENDS_ON = "dependsOn";

    public static final String TASK_OVERWRITE = "overwrite";

    public static final String TASK_TYPE_LATE_INITIALIZER = "lateInitializer";


    private Project rootProject;

    private ProjectFactory projectFactory;

    // This is an implementation detail of Project. Therefore we don't use IoC here.
    private ProjectsTraverser projectsTraverser = new ProjectsTraverser();

    private BuildScriptProcessor buildScriptProcessor;

    private ClassLoader buildScriptClassLoader;

    private String buildFileName;

    private Script buildScript;

    private PluginRegistry pluginRegistry;

    private File rootDir;

    private Project parent;

    private String name;

    private Map<String, AbstractProject> childProjects = new HashMap<String, AbstractProject>();

    private Map<String, Task> tasks = new HashMap<String, Task>();

    private Set dependsOnProjects = new HashSet();

    private Map additionalProperties = new LinkedHashMap();

    private int state;

    private List plugins = new ArrayList();

    private BaseDirConverter baseDirConverter = new BaseDirConverter();

    private AntBuilder ant = null;

    private DependencyManager dependencies;

    private String archivesTaskBaseName;

    private String archivesBaseName;

    private String gradleUserHome;

    private String buildDirName = Project.DEFAULT_BUILD_DIR_NAME;

    private Convention convention;

    private DagAction configureByDag = null;

    private Map pluginApplyRegistry = new LinkedHashMap();

    private String path = null;

    private int depth = 0;

    private ProjectRegistry projectRegistry;

    public AbstractProject() {
        convention = new Convention(this);
    }

    public AbstractProject(String name, DefaultProject parent, File rootDir, DefaultProject rootProject, String buildFileName,
                           ClassLoader buildScriptClassLoader, ProjectFactory projectFactory, DependencyManager dependencyManager,
                           BuildScriptProcessor buildScriptProcessor, PluginRegistry pluginRegistry, ProjectRegistry projectRegistry) {
        assert name != null;
        assert (parent == null && rootProject == null) || (parent != null && rootProject != null);
        this.rootProject = rootProject != null ? rootProject : this;
        this.rootDir = rootDir;
        this.parent = parent;
        this.name = name;
        this.buildFileName = buildFileName;
        this.buildScriptClassLoader = buildScriptClassLoader;
        this.projectFactory = projectFactory;
        dependencyManager.setProject(this);
        this.dependencies = dependencyManager;
        this.buildScriptProcessor = buildScriptProcessor;
        this.pluginRegistry = pluginRegistry;
        this.projectRegistry = projectRegistry;
        this.state = STATE_CREATED;
        this.archivesTaskBaseName = Project.DEFAULT_ARCHIVES_TASK_BASE_NAME;
        this.archivesBaseName = name;

        if (parent == null) {
            path = Project.PATH_SEPARATOR;
        } else {
            path = parent == rootProject ? Project.PATH_SEPARATOR + name : parent.getPath() + Project.PATH_SEPARATOR + name;
        }

        if (parent != null) {
            depth = parent.getDepth() + 1;
        }

        projectRegistry.addProject(this);


        convention = new Convention(this);
    }

    public Project getRootProject() {
        return rootProject;
    }

    public void setRootProject(Project rootProject) {
        this.rootProject = rootProject;
    }

    public ProjectFactory getProjectFactory() {
        return projectFactory;
    }

    public void setProjectFactory(ProjectFactory projectFactory) {
        this.projectFactory = projectFactory;
    }

    public ProjectsTraverser getProjectsTraverser() {
        return projectsTraverser;
    }

    public void setProjectsTraverser(ProjectsTraverser projectsTraverser) {
        this.projectsTraverser = projectsTraverser;
    }

    public BuildScriptProcessor getBuildScriptProcessor() {
        return buildScriptProcessor;
    }

    public void setBuildScriptProcessor(BuildScriptProcessor buildScriptProcessor) {
        this.buildScriptProcessor = buildScriptProcessor;
    }

    public ClassLoader getBuildScriptClassLoader() {
        return buildScriptClassLoader;
    }

    public void setBuildScriptClassLoader(ClassLoader buildScriptClassLoader) {
        this.buildScriptClassLoader = buildScriptClassLoader;
    }

    public String getBuildFileName() {
        return buildFileName;
    }

    public String getBuildFileCacheName() {
        return buildFileName == null ? null : buildFileName.replaceAll("\\.", "_");
    }

    public void setBuildFileName(String buildFileName) {
        this.buildFileName = buildFileName;
    }

    public Script getBuildScript() {
        return buildScript;
    }

    public void setBuildScript(Script buildScript) {
        this.buildScript = buildScript;
    }

    public PluginRegistry getPluginRegistry() {
        return pluginRegistry;
    }

    public void setPluginRegistry(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    public File getRootDir() {
        return rootDir;
    }

    public void setRootDir(File rootDir) {
        this.rootDir = rootDir;
    }

    public Project getParent() {
        return parent;
    }

    public void setParent(Project parent) {
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map getChildProjects() {
        return childProjects;
    }

    public void setChildProjects(Map childProjects) {
        this.childProjects = childProjects;
    }

    public Map getTasks() {
        return tasks;
    }

    public void setTasks(Map tasks) {
        this.tasks = tasks;
    }

    public Set getDependsOnProjects() {
        return dependsOnProjects;
    }

    public void setDependsOnProjects(Set dependsOnProjects) {
        this.dependsOnProjects = dependsOnProjects;
    }

    public Map getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(Map additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public List getPlugins() {
        return plugins;
    }

    public void setPlugins(List plugins) {
        this.plugins = plugins;
    }

    public BaseDirConverter getBaseDirConverter() {
        return baseDirConverter;
    }

    public void setBaseDirConverter(BaseDirConverter baseDirConverter) {
        this.baseDirConverter = baseDirConverter;
    }

    public void setAnt(AntBuilder ant) {
        this.ant = ant;
    }

    public DependencyManager getDependencies() {
        return dependencies;
    }

    public void setDependencies(DependencyManager dependencies) {
        this.dependencies = dependencies;
    }

    public String getArchivesTaskBaseName() {
        return archivesTaskBaseName;
    }

    public void setArchivesTaskBaseName(String archivesTaskBaseName) {
        this.archivesTaskBaseName = archivesTaskBaseName;
    }


    public String getBuildDirName() {
        return buildDirName;
    }

    public void setBuildDirName(String buildDirName) {
        this.buildDirName = buildDirName;
    }

    public Convention getConvention() {
        return convention;
    }

    public void setConvention(Convention convention) {
        this.convention = convention;
    }

    public DagAction getConfigureByDag() {
        return configureByDag;
    }

    public void setConfigureByDag(DagAction configureByDag) {
        this.configureByDag = configureByDag;
    }

    public Map getPluginApplyRegistry() {
        return pluginApplyRegistry;
    }

    public void setPluginApplyRegistry(Map pluginApplyRegistry) {
        this.pluginApplyRegistry = pluginApplyRegistry;
    }

    public String getPath() {
        return path;
    }

    public int getDepth() {
        return depth;
    }

    public String getGradleUserHome() {
        return gradleUserHome;
    }

    public void setGradleUserHome(String gradleUserHome) {
        this.gradleUserHome = gradleUserHome;
    }

    public String getArchivesBaseName() {
        return archivesBaseName;
    }

    public void setArchivesBaseName(String archivesBaseName) {
        this.archivesBaseName = archivesBaseName;
    }

    public ProjectRegistry getProjectRegistry() {
        return projectRegistry;
    }

    public void setProjectRegistry(ProjectRegistry projectRegistry) {
        this.projectRegistry = projectRegistry;
    }

    public boolean equals(Object other) {
        AbstractProject otherProject = (AbstractProject) other;
        return path.equals(otherProject.getPath());
    }

    public int hashCode() {
        return path.hashCode();
    }

    public int depthCompare(Project otherProject) {
        return new Integer(getDepth()).compareTo(new Integer(otherProject.getDepth()));
    }

    public int compareTo(Object other) {
        AbstractProject otherProject = (AbstractProject) other;
        int depthCompare = depthCompare(otherProject);
        if (depthCompare == 0) {
            return path.compareTo(otherProject.path);
        } else {
            return depthCompare;
        }
    }

    public String absolutePath(String path) {
        if (!isAbsolutePath(path)) {
            String prefix = this == rootProject ? "" : Project.PATH_SEPARATOR;
            return this.path + prefix + path;
        }
        return path;
    }

    public static boolean isAbsolutePath(String path) {
        if (!GUtil.isTrue(path)) {
            throw new InvalidUserDataException("A path must be specified!");
        }
        return path.startsWith(Project.PATH_SEPARATOR);
    }

    public Project project(String path) {
        Project project = findProject(isAbsolutePath(path) ? path : absolutePath(path));
        if (project == null) {
            throw new UnknownProjectException("Project with path " + path + " could not be found");
        }
        return project;
    }

    public Project findProject(String absolutePath) {
        if (!GUtil.isTrue(absolutePath)) {
            throw new InvalidUserDataException("A path must be specified!");
        }
        if (!isAbsolutePath(path)) {
            throw new InvalidUserDataException("The path must be absolute!");
        }
        return projectRegistry.getProject(absolutePath);
    }

    public Set getAllprojects() {
        return projectRegistry.getAllProjects(this.path);
    }

    public Set getSubprojects() {
        return projectRegistry.getSubProjects(this.path);
    }

    public void subprojects(ProjectAction action) {
        applyActions(getSubprojects(), action);
    }

    public void allprojects(ProjectAction action) {
        applyActions(getAllprojects(), action);
    }

    public void applyActions(Set<Project> projects, ProjectAction action) {
        for (Project project : projects) {
            action.execute(project);
        }
    }

    public AntBuilder getAnt() {
        if (ant == null) {
            ant = new AntBuilder();
            GradleUtil.setAntLogging(ant);
        }
        return ant;
    }

    /**
     * This method is used when scripts access the project via project.x
     */
    public Project getProject() {
        return this;
    }

    public AbstractProject evaluate() {
        if (state == STATE_INITIALIZED) {
            return this;
        }
        Clock clock = new Clock();
        state = STATE_INITIALIZING;
        buildScript = buildScriptProcessor.createScript(this);
        Clock runClock = new Clock();
        try {
            buildScript.run();
        } catch (GradleException e) {
            ((GradleException) e).setScriptName(getBuildFileCacheName());
            throw e;
        } catch (Throwable t) {
            throw new GradleScriptException(t, getBuildFileCacheName());
        }
        logger.info("Timing: Running the build script took " + clock.getTime());
        state = STATE_INITIALIZED;
        lateInitializeTasks(tasks);
        logger.info("Project= " + path + " evaluated.");
        logger.info("Timing: Project evaluation took " + clock.getTime());
        return this;
    }

    private void lateInitializeTasks(Map<String, Task> tasks) {
        while (true) {
            Set<Task> uninitializedTasks = new HashSet();
            for (Task task : tasks.values()) {
                if (!task.getLateInitialized()) {
                    uninitializedTasks.add(task);
                }
            }
            if (uninitializedTasks.size() == 0) {
                break;
            }
            for (Task uninitializedTask : uninitializedTasks) {
                uninitializedTask.applyLateInitialize();
            }
        }
    }

    public Project usePlugin(String pluginName) {
        return usePlugin(pluginName, new HashMap());
    }

    public Project usePlugin(String pluginName, Map customValues) {
        if (usePluginInternal(pluginRegistry.getPlugin(pluginName), customValues) == null) {
            throw new InvalidUserDataException("Plugin with id " + pluginName + " can not be found!");
        }
        return this;
    }

    public Project usePlugin(Class pluginClass) {
        return usePlugin(pluginClass, new HashMap());
    }

    public Project usePlugin(Class pluginClass, Map customValues) {
        if (usePluginInternal(pluginRegistry.getPlugin(pluginClass), customValues) == null) {
            throw new InvalidUserDataException("Plugin class " + pluginClass + " can not be found!");
        }
        return this;
    }

    private Plugin usePluginInternal(Plugin plugin, Map customValues) {
        if (plugin == null) {
            return null;
        }
        pluginRegistry.apply(plugin.getClass(), this, pluginRegistry, customValues);
        plugins.add(plugin);
        return plugin;
    }

    public Task task(String name) {
        if (tasks.get(name) == null) {
            throw new InvalidUserDataException("Task with name= " + name + " does not exists!");
        }
        return tasks.get(name);
    }

    public Task createTask(String name) {
        return createTask(new HashMap(), name, null);
    }

    public Task createTask(Map args, String name) {
        return createTask(args, name, null);
    }

    public Task createTask(String name, TaskAction action) {
        return createTask(new HashMap(), name, action);
    }

    public Task createTask(Map args, String name, TaskAction action) {
        if (!GUtil.isTrue(name)) {
            throw new InvalidUserDataException("The name of the task must be set!");
        }
        checkTaskArgsAndCreateDefaultValues(args);
        if (!Boolean.valueOf(args.get(TASK_OVERWRITE).toString()) && tasks.get(name) != null) {
            throw new InvalidUserDataException("A task with this name already exists!");
        }
        Task task = createTaskObject((Class) args.get(TASK_TYPE), name);
        task.setLateInitalizeClosures((List<Closure>) args.get(TASK_TYPE_LATE_INITIALIZER));
        tasks.put(name, task);
        Object dependsOn = args.get(TASK_DEPENDS_ON);
        if (dependsOn instanceof String || (dependsOn instanceof GString)) {
            String singleDependencyName = (String) dependsOn;
            if (singleDependencyName == null) {
                throw new InvalidUserDataException("A dependency name must not be empty!");
            }
            args.put(TASK_DEPENDS_ON, Collections.singletonList(singleDependencyName));
        }
        Object[] dependsOnTasks;
        Object dependsOnTasksArg = args.get(TASK_DEPENDS_ON);
        if (dependsOnTasksArg instanceof Collection) {
            dependsOnTasks = (Object[]) ((Collection) dependsOnTasksArg).toArray(new Object[((Collection) dependsOnTasksArg).size()]);
        } else {
            dependsOnTasks = new Object[]{dependsOnTasksArg};
        }
        logger.debug("Adding dependencies: " + Arrays.asList(dependsOnTasks));

        task.dependsOn(dependsOnTasks);

        if (action != null) {
            task.doFirst(action);
        }
        return task;
    }

    private Task createTaskObject(Class type, String name) {
        try {
            Constructor constructor = type.getDeclaredConstructor(Project.class, String.class);
            return (Task) constructor.newInstance(this, name);
        } catch (Exception e) {
            throw new GradleException("Task creation error.", e);
        }
    }

    private void checkTaskArgsAndCreateDefaultValues(Map args) {
        setIfNull(args, TASK_TYPE, DefaultTask.class);
        setIfNull(args, TASK_DEPENDS_ON, new ArrayList());
        setIfNull(args, TASK_TYPE_LATE_INITIALIZER, new ArrayList());
        setIfNull(args, TASK_OVERWRITE, new Boolean(false));
    }

    private void setIfNull(Map map, String key, Object defaultValue) {
        if (map.get(key) == null) {
            map.put(key, defaultValue);
        }
    }

    public Project addChildProject(String name) {
        childProjects.put(name, projectFactory.createProject(name, this, rootDir, rootProject, buildScriptClassLoader));
        return childProjects.get(name);
    }

    public String getRelativeFilePath() {
        return "/" + rootProject.getName() + "/" + path.substring(1).replace(Project.PATH_SEPARATOR, "/");
    }

    public File getProjectDir() {
        return new File(rootDir.getParent(), getRelativeFilePath());
    }

    public File getBuildDir() {
        return new File(getProjectDir(), buildDirName);
    }

    public void dependsOn(String path) {
        dependsOn(path, true);
    }

    public void dependsOn(String path, boolean evaluateDependsOnProject) {
        if (!GUtil.isTrue(path)) {
            throw new InvalidUserDataException("You must specify a project!");
        }
        dependsOnProjects.add(project(absolutePath(path)));
        if (evaluateDependsOnProject) {
            evaluationDependsOn(path);
        }
    }

    public Project evaluationDependsOn(String path) {
        if (!GUtil.isTrue(path)) {
            throw new InvalidUserDataException("You must specify a project!");
        }
        DefaultProject projectToEvaluate = (DefaultProject) project(absolutePath(path));
        if (projectToEvaluate.getState() == DefaultProject.STATE_INITIALIZING) {
            throw new CircularReferenceException("Circular referencing during evaluation for project: " + projectToEvaluate);
        }
        return projectToEvaluate.evaluate();
    }

    public Project childrenDependOnMe() {
        for (AbstractProject project : childProjects.values()) {
            project.dependsOn(this.path, false);
        }
        return this;
    }

    public Project dependsOnChildren() {
        return dependsOnChildren(false);
    }

    public Project dependsOnChildren(boolean evaluateDependsOnProject) {
        for (AbstractProject project : childProjects.values()) {
            dependsOn(project.path, evaluateDependsOnProject);
        }
        return this;
    }

    public String toString() {
        return path;
    }

    public SortedMap getAllTasks(boolean recursive) {
        final SortedMap<Project, Set> foundTargets = new TreeMap<Project, Set>();
        ProjectAction action = new ProjectAction() {
            public void execute(Project project) {
                foundTargets.put(project, new TreeSet(project.getTasks().values()));
            }
        };
        if (recursive) {
            allprojects(action);
        } else {
            action.execute(this);
        }
        return foundTargets;
    }

    public Set<Task> getTasksByName(final String name, boolean recursive) {
        if (!GUtil.isTrue(name)) {
            throw new InvalidUserDataException("Name is not specified!");
        }
        final Set<Task> foundTasks = new HashSet<Task>();
        ProjectAction action = new ProjectAction() {
            public void execute(Project project) {
                if (project.getTasks().get(name) != null) {
                    foundTasks.add(project.getTasks().get(name));
                }
            }
        };
        if (recursive) {
            allprojects(action);
        } else {
            action.execute(this);
        }
        return foundTasks;
    }

    public File file(Object path) {
        return file(path, PathValidation.NONE);
    }

    public File file(Object path, PathValidation validation) {
        return baseDirConverter.baseDir(path.toString(), getProjectDir(), validation);
    }

    public Task dir(String path) {
        String resultTaskName = path;
        String[] pathElements = path.split("/");
        String name = "";
        for (String pathElement : pathElements) {
            name += name.length() != 0 ? "/" + pathElement : pathElement;
            if (tasks.get(name) != null) {
                if (!(tasks.get(name) instanceof Directory)) {
                    throw new InvalidUserDataException("A non directory task with this name already exsists.");
                }
            } else {
                HashMap map = new HashMap();
                map.put("type", Directory.class);
                createTask(map, name);
            }
        }
        return task(resultTaskName);
    }

}
