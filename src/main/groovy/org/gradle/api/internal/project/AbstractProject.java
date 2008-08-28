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

import groovy.lang.Script;
import groovy.util.AntBuilder;
import org.gradle.api.*;
import org.gradle.api.internal.dependencies.DependencyManagerFactory;
import org.gradle.api.plugins.Convention;
import org.gradle.api.tasks.Directory;
import org.gradle.api.tasks.util.BaseDirConverter;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.util.Clock;
import org.gradle.util.GUtil;
import org.gradle.util.GradleUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * @author Hans Dockter
 */
public abstract class AbstractProject implements ProjectInternal {
    private static Logger logger = LoggerFactory.getLogger(AbstractProject.class);

    public static final int STATE_CREATED = 0;

    public static final int STATE_INITIALIZING = 1;

    public static final int STATE_INITIALIZED = 2;

    private Project rootProject;

    private BuildScriptProcessor buildScriptProcessor;

    private ClassLoader buildScriptClassLoader;

    private String buildFileName;

    private Script buildScript;

    private ScriptSource buildScriptSource;

    private PluginRegistry pluginRegistry;

    private File rootDir;

    private Project parent;

    private String name;

    private Map<String, Project> childProjects = new HashMap<String, Project>();

    private Map<String, Task> tasks = new HashMap<String, Task>();

    private List<String> defaultTasks = new ArrayList<String>();

    private Set<Project> dependsOnProjects = new HashSet<Project>();

    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    private int state;

    private List<Plugin> plugins = new ArrayList<Plugin>();

    private BaseDirConverter baseDirConverter = new BaseDirConverter();

    private AntBuilder ant = null;

    private DependencyManager dependencies;

    private String archivesTaskBaseName;

    private String archivesBaseName;

    private String gradleUserHome;

    private String buildDirName = Project.DEFAULT_BUILD_DIR_NAME;

    private Convention convention;

    private Set<Class<? extends Plugin>> appliedPlugins = new HashSet<Class<? extends Plugin>>();

    private String path = null;

    private int depth = 0;

    private ProjectRegistry projectRegistry;

    private ITaskFactory taskFactory;

    private DependencyManagerFactory dependencyManagerFactory;

    private List<AfterEvaluateListener> afterEvaluateListeners = new ArrayList<AfterEvaluateListener>();

    private IProjectFactory projectFactory;

    public AbstractProject() {
        convention = new Convention(this);
    }

    public AbstractProject(String name, Project parent, File rootDir, String buildFileName, ScriptSource buildScriptSource,
                           ClassLoader buildScriptClassLoader, ITaskFactory taskFactory, DependencyManagerFactory dependencyManagerFactory,
                           BuildScriptProcessor buildScriptProcessor, PluginRegistry pluginRegistry, ProjectRegistry projectRegistry,
                           IProjectFactory projectFactory) {
        assert name != null;
        this.rootProject = parent != null ? parent.getRootProject() : this;
        this.rootDir = rootDir;
        this.parent = parent;
        this.name = name;
        this.buildFileName = buildFileName;
        this.buildScriptClassLoader = buildScriptClassLoader;
        this.taskFactory = taskFactory;
        this.dependencyManagerFactory = dependencyManagerFactory;
        this.dependencies = dependencyManagerFactory.createDependencyManager(this);
        this.buildScriptProcessor = buildScriptProcessor;
        this.pluginRegistry = pluginRegistry;
        this.projectRegistry = projectRegistry;
        this.state = STATE_CREATED;
        this.archivesTaskBaseName = Project.DEFAULT_ARCHIVES_TASK_BASE_NAME;
        this.archivesBaseName = name;
        this.projectFactory = projectFactory;
        this.buildScriptSource = buildScriptSource;

        if (parent == null) {
            path = Project.PATH_SEPARATOR;
        } else {
            path = parent.absolutePath(name);
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

    public String getBuildFileClassName() {
        return getBuildScript().getClass().getName();
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

    public ScriptSource getBuildScriptSource() {
        return buildScriptSource;
    }

    public void setBuildScriptSource(ScriptSource buildScriptSource) {
        this.buildScriptSource = buildScriptSource;
    }

    public PluginRegistry getPluginRegistry() {
        return pluginRegistry;
    }

    public void setPluginRegistry(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    public IProjectFactory getProjectFactory() {
        return projectFactory;
    }

    public void setProjectFactory(IProjectFactory projectFactory) {
        this.projectFactory = projectFactory;
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

    public Map<String, Project> getChildProjects() {
        return childProjects;
    }

    public void setChildProjects(Map<String, Project> childProjects) {
        this.childProjects = childProjects;
    }

    public Map<String, Task> getTasks() {
        return tasks;
    }

    public void setTasks(Map<String, Task> tasks) {
        this.tasks = tasks;
    }

    public List<String> getDefaultTasks() {
        return defaultTasks;
    }

    public void setDefaultTasks(List<String> defaultTasks) {
        this.defaultTasks = defaultTasks;
    }

    public Set<Project> getDependsOnProjects() {
        return dependsOnProjects;
    }

    public void setDependsOnProjects(Set<Project> dependsOnProjects) {
        this.dependsOnProjects = dependsOnProjects;
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public List<Plugin> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<Plugin> plugins) {
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

    public Set<Class<? extends Plugin>> getAppliedPlugins() {
        return appliedPlugins;
    }

    public void setAppliedPlugins(Set<Class<? extends Plugin>> appliedPlugins) {
        this.appliedPlugins = appliedPlugins;
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
        return new Integer(getDepth()).compareTo(otherProject.getDepth());
    }

    public int compareTo(Project otherProject) {
        int depthCompare = depthCompare(otherProject);
        if (depthCompare == 0) {
            return path.compareTo(otherProject.getPath());
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

    public Set<Project> getAllprojects() {
        return projectRegistry.getAllProjects(this.path);
    }

    public Set<Project> getSubprojects() {
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
        try {
            buildScript.run();
        } catch (GradleException e) {
            e.setScriptName(getBuildFileClassName());
            throw e;
        } catch (Throwable t) {
            throw new GradleScriptException(t, getBuildFileClassName());
        }
        logger.debug("Timing: Running the build script took " + clock.getTime());
        state = STATE_INITIALIZED;
        notifyAfterEvaluateListener();
        logger.info("Project= " + path + " evaluated.");
        logger.debug("Timing: Project evaluation took " + clock.getTime());
        return this;
    }

    private void notifyAfterEvaluateListener() {
        for (AfterEvaluateListener afterEvaluateListener : afterEvaluateListeners) {
            afterEvaluateListener.afterEvaluate(this);
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

    public Project usePlugin(Class<? extends Plugin> pluginClass) {
        return usePlugin(pluginClass, new HashMap());
    }

    public Project usePlugin(Class<? extends Plugin> pluginClass, Map customValues) {
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

    public void defaultTasks(String... defaultTasks) {
        if (defaultTasks == null) {
            throw new InvalidUserDataException("Default tasks must not be null!");
        }
        this.defaultTasks = new ArrayList<String>();
        for (String defaultTask : defaultTasks) {
            if (defaultTask == null) {
                throw new InvalidUserDataException("Default tasks must not be null!");
            }
            this.defaultTasks.add(defaultTask);
        }
    }

    public Task createTask(String name) {
        return createTask(new HashMap<String, Object>(), name, (TaskAction) null);
    }

    public Task createTask(Map<String, ?> args, String name) {
        return createTask(args, name, (TaskAction) null);
    }

    public Task createTask(String name, TaskAction action) {
        return createTask(new HashMap<String, Object>(), name, action);
    }

    public Task createTask(Map args, String name, TaskAction action) {
        tasks.put(name, taskFactory.createTask(this, tasks, args, name));
        if (action != null) {
            tasks.get(name).doFirst(action);
        }
        return tasks.get(name);
    }

    public Project addChildProject(String name) {
        childProjects.put(name, createChildProject(name));
        return childProjects.get(name);
    }

    protected ProjectInternal createChildProject(String name) {
        return projectFactory.createProject(name, this, rootDir, buildScriptClassLoader);
    }

    public String getRelativeFilePath() {
        return "/" + rootProject.getName() + "/" + path.substring(1).replace(Project.PATH_SEPARATOR, "/");
    }

    public File getProjectDir() {
        return new File(rootDir.getParent(), getRelativeFilePath()).getAbsoluteFile();
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
        for (Project project : childProjects.values()) {
            project.dependsOn(this.path, false);
        }
        return this;
    }

    public Project dependsOnChildren() {
        return dependsOnChildren(false);
    }

    public Project dependsOnChildren(boolean evaluateDependsOnProject) {
        for (Project project : childProjects.values()) {
            dependsOn(project.getPath(), evaluateDependsOnProject);
        }
        return this;
    }

    public String toString() {
        return path;
    }

    public SortedMap<Project, Set<Task>> getAllTasks(boolean recursive) {
        final SortedMap<Project, Set<Task>> foundTargets = new TreeMap<Project, Set<Task>>();
        ProjectAction action = new ProjectAction() {
            public void execute(Project project) {
                foundTargets.put(project, new TreeSet<Task>(project.getTasks().values()));
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

    public File relativePath(Object path) {
        File result = findRelativePath(path);
        if (result == null) {
            throw new GradleException("Path = " + path + " is not a subdirectory of the project root dir.");
        }
        return result;
    }

    public File findRelativePath(Object path) {
        File file = new File(path.toString());
        if (!file.isAbsolute()) {
            return file;
        }
        File loopFile = file;
        String relativePath = "";
        while (loopFile != null) {
            if (loopFile.equals(getProjectDir())) {
                break;
            }
            relativePath = loopFile.getName() + "/" + relativePath;
            loopFile = loopFile.getParentFile();
        }
        return loopFile == null ? null : new File(relativePath);
    }

    public Task dir(String path) {
        String[] pathElements = path.split("/");
        String name = "";
        for (String pathElement : pathElements) {
            name += name.length() != 0 ? "/" + pathElement : pathElement;
            if (tasks.get(name) != null) {
                if (!(tasks.get(name) instanceof Directory)) {
                    throw new InvalidUserDataException("A non directory task with this name already exsists.");
                }
            } else {
                HashMap<String, Object> map = new HashMap<String, Object>();
                map.put("type", Directory.class);
                createTask(map, name);
            }
        }
        return task(path);
    }

    public ITaskFactory getTaskFactory() {
        return taskFactory;
    }

    public void setTaskFactory(ITaskFactory taskFactory) {
        this.taskFactory = taskFactory;
    }

    public DependencyManagerFactory getDependencyManagerFactory() {
        return dependencyManagerFactory;
    }

    public void setDependencyManagerFactory(DependencyManagerFactory dependencyManagerFactory) {
        this.dependencyManagerFactory = dependencyManagerFactory;
    }

    public List<AfterEvaluateListener> getAfterEvaluateListeners() {
        return afterEvaluateListeners;
    }

    public AfterEvaluateListener addAfterEvaluateListener(AfterEvaluateListener afterEvaluateListener) {
        afterEvaluateListeners.add(afterEvaluateListener);
        return afterEvaluateListener;
    }
}
