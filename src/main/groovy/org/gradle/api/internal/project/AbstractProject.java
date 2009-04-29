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
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;
import groovy.util.AntBuilder;
import org.apache.commons.lang.StringUtils;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.*;
import org.gradle.api.artifacts.FileCollection;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.dsl.*;
import org.gradle.api.artifacts.repositories.InternalRepository;
import org.gradle.api.internal.BeanDynamicObject;
import org.gradle.api.internal.BuildInternal;
import org.gradle.api.internal.DynamicObject;
import org.gradle.api.internal.DynamicObjectHelper;
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.PathResolvingFileCollection;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.ResolverProvider;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactFactory;
import org.gradle.api.internal.plugins.DefaultConvention;
import org.gradle.api.internal.tasks.DefaultTaskContainer;
import org.gradle.api.invocation.Build;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.plugins.Convention;
import org.gradle.api.tasks.Directory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.util.BaseDirConverter;
import org.gradle.configuration.ProjectEvaluator;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * @author Hans Dockter
 */
public abstract class AbstractProject implements ProjectInternal {
    private static Logger logger = LoggerFactory.getLogger(AbstractProject.class);
    private static Logger buildLogger = LoggerFactory.getLogger(Project.class);

    public enum State {
        CREATED, INITIALIZING, INITIALIZED
    }

    private Project rootProject;

    private BuildInternal build;

    private ProjectEvaluator projectEvaluator;

    private ClassLoader buildScriptClassLoader;

    private File buildFile;

    private Script buildScript;

    private ScriptSource buildScriptSource;

    private PluginRegistry pluginRegistry;

    private File projectDir;

    private ProjectInternal parent;

    private String name;

    private Object group = DEFAULT_GROUP;

    private Object version = DEFAULT_VERSION;

    private Object status = DEFAULT_STATUS;

    private Map<String, Project> childProjects = new HashMap<String, Project>();

    private List<String> defaultTasks = new ArrayList<String>();

    private Set<Project> dependsOnProjects = new HashSet<Project>();

    private State state;

    private List<Plugin> plugins = new ArrayList<Plugin>();

    private BaseDirConverter baseDirConverter = new BaseDirConverter();

    private AntBuilderFactory antBuilderFactory;

    private AntBuilder ant = null;

    private String archivesTaskBaseName;

    private String archivesBaseName;

    private String buildDirName = Project.DEFAULT_BUILD_DIR_NAME;

    private Set<Class<? extends Plugin>> appliedPlugins = new HashSet<Class<? extends Plugin>>();

    private String path = null;

    private int depth = 0;

    private DefaultTaskContainer taskContainer;

    private IProjectRegistry<ProjectInternal> projectRegistry;

    private InternalRepository internalRepository;

    private ConfigurationContainerFactory configurationContainerFactory;

    private DependencyHandler dependencyHandler;

    private ConfigurationHandler configurationContainer;

    private PublishArtifactFactory publishArtifactFactory;

    private ArtifactHandler artifactHandler;

    private RepositoryHandlerFactory repositoryHandlerFactory;

    private RepositoryHandler repositoryHandler;

    private ListenerBroadcast<Action> afterEvaluateActions = new ListenerBroadcast<Action>(Action.class);
    private ListenerBroadcast<Action> beforeEvaluateActions = new ListenerBroadcast<Action>(Action.class);

    private StandardOutputRedirector standardOutputRedirector = new DefaultStandardOutputRedirector();
    private DynamicObjectHelper dynamicObjectHelper;


    public AbstractProject(String name) {
        this.name = name;
        dynamicObjectHelper = new DynamicObjectHelper(this);
        dynamicObjectHelper.setConvention(new DefaultConvention());
        taskContainer = null;
    }

    public AbstractProject(String name, ProjectInternal parent, File projectDir, File buildFile,
                           ScriptSource buildScriptSource, ClassLoader buildScriptClassLoader, ITaskFactory taskFactory,
                           ConfigurationContainerFactory configurationContainerFactory,
                           DependencyFactory dependencyFactory,
                           RepositoryHandlerFactory repositoryHandlerFactory,
                           PublishArtifactFactory publishArtifactFactory,
                           InternalRepository internalRepository,
                           AntBuilderFactory antBuilderFactory,
                           ProjectEvaluator projectEvaluator,
                           PluginRegistry pluginRegistry, IProjectRegistry projectRegistry,
                           BuildInternal build, Convention convention) {
        assert name != null;
        this.rootProject = parent != null ? parent.getRootProject() : this;
        this.projectDir = projectDir;
        this.parent = parent;
        this.name = name;
        this.buildFile = buildFile;
        this.buildScriptClassLoader = buildScriptClassLoader;
        this.internalRepository = internalRepository;
        this.configurationContainerFactory = configurationContainerFactory;
        this.configurationContainer = configurationContainerFactory.createConfigurationContainer(createResolverProvider(), createArtifactsProvider());
        this.repositoryHandlerFactory = repositoryHandlerFactory;
        this.repositoryHandlerFactory.setConvention(convention);
        this.repositoryHandler = repositoryHandlerFactory.createRepositoryHandler();
        this.dependencyHandler = new DependencyHandler(configurationContainer, dependencyFactory);
        this.publishArtifactFactory = publishArtifactFactory;
        this.artifactHandler = new ArtifactHandler(configurationContainer, publishArtifactFactory);
        this.antBuilderFactory = antBuilderFactory;
        this.projectEvaluator = projectEvaluator;
        this.pluginRegistry = pluginRegistry;
        this.projectRegistry = projectRegistry;
        this.state = State.CREATED;
        this.archivesTaskBaseName = Project.DEFAULT_ARCHIVES_TASK_BASE_NAME;
        this.archivesBaseName = name;
        this.buildScriptSource = buildScriptSource;
        this.build = build;
        taskContainer = new DefaultTaskContainer(this, taskFactory);

        if (parent == null) {
            path = Project.PATH_SEPARATOR;
        } else {
            path = parent.absolutePath(name);
        }

        dynamicObjectHelper = new DynamicObjectHelper(this);
        dynamicObjectHelper.setConvention(convention);
        if (parent != null) {
            dynamicObjectHelper.setParent(parent.getInheritedScope());
        }
        dynamicObjectHelper.addObject(taskContainer.getAsDynamicObject(), DynamicObjectHelper.Location.AfterConvention);

        if (parent != null) {
            depth = parent.getDepth() + 1;
        }

        projectRegistry.addProject(this);
    }

    private ResolverProvider createResolverProvider() {
        return new ResolverProvider() {
            public List<DependencyResolver> getResolvers() {
                return repositoryHandler.getResolvers();
            }
        };
    }

    private DependencyMetaDataProvider createArtifactsProvider() {
        return new DependencyMetaDataProvider() {
            public InternalRepository getInternalRepository() {
                return internalRepository;
            }

            public File getGradleUserHomeDir() {
                return build.getGradleUserHomeDir();
            }

            public Map getClientModuleRegistry() {
                return new HashMap();
            }

            public Module getModule() {
                return new Module() {
                    public String getGroup() {
                        return group.toString();
                    }

                    public String getName() {
                        return name;
                    }

                    public String getVersion() {
                        return version.toString();
                    }

                    public String getStatus() {
                        return status.toString();
                    }
                };
            }
        };
    }

    public RepositoryHandler createRepositoryHandler() {
        return repositoryHandlerFactory.createRepositoryHandler();
    }

    public Project getRootProject() {
        return rootProject;
    }

    public void setRootProject(Project rootProject) {
        this.rootProject = rootProject;
    }

    public Build getBuild() {
        return build;
    }

    public void setBuild(BuildInternal build) {
        this.build = build;
    }

    public ProjectEvaluator getProjectEvaluator() {
        return projectEvaluator;
    }

    public void setProjectEvaluator(ProjectEvaluator projectEvaluator) {
        this.projectEvaluator = projectEvaluator;
    }

    public ClassLoader getBuildScriptClassLoader() {
        return buildScriptClassLoader;
    }

    public void setBuildScriptClassLoader(ClassLoader buildScriptClassLoader) {
        this.buildScriptClassLoader = buildScriptClassLoader;
    }

    public File getBuildFile() {
        return buildFile;
    }

    public String getBuildFileClassName() {
        return buildScriptSource.getClassName();
    }

    public void setBuildFile(File buildFile) {
        this.buildFile = buildFile;
    }

    public Script getBuildScript() {
        return buildScript;
    }

    public void setBuildScript(Script buildScript) {
        this.buildScript = buildScript;
        dynamicObjectHelper.addObject(new BeanDynamicObject(buildScript).withNoProperties(), DynamicObjectHelper.Location.BeforeConvention);
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

    public File getRootDir() {
        return rootProject.getProjectDir();
    }

    public ProjectInternal getParent() {
        return parent;
    }

    public void setParent(ProjectInternal parent) {
        this.parent = parent;
        dynamicObjectHelper.setParent(parent == null ? null : parent.getInheritedScope());
    }

    public ProjectIdentifier getParentIdentifier() {
        return parent;
    }

    public DynamicObjectHelper getDynamicObjectHelper() {
        return dynamicObjectHelper;
    }

    public DynamicObject getInheritedScope() {
        return dynamicObjectHelper.getInheritable();
    }

    public String getName() {
        return name;
    }

    public Object getGroup() {
        return group;
    }

    public void setGroup(Object group) {
        this.group = group;
    }

    public Object getVersion() {
        return version;
    }

    public void setVersion(Object version) {
        this.version = version;
    }

    public Object getStatus() {
        return status;
    }

    public void setStatus(Object status) {
        this.status = status;
    }

    public Map<String, Project> getChildProjects() {
        return childProjects;
    }

    public void setChildProjects(Map<String, Project> childProjects) {
        this.childProjects = childProjects;
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
        return dynamicObjectHelper.getAdditionalProperties();
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        dynamicObjectHelper.setAdditionalProperties(additionalProperties);
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
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

    public ArtifactHandler getArtifacts() {
        return artifactHandler;
    }

    public void setArtifactHandler(ArtifactHandler artifactHandler) {
        this.artifactHandler = artifactHandler;
    }

    public RepositoryHandler getRepositories() {
        return repositoryHandler;
    }

    public void setRepositoryHandler(DefaultRepositoryHandler repositoryHandlerFactory) {
        this.repositoryHandler = repositoryHandlerFactory;
    }

    public RepositoryHandlerFactory getRepositoryHandlerFactory() {
        return repositoryHandlerFactory;
    }

    public void setRepositoryHandlerFactory(RepositoryHandlerFactory repositoryHandlerFactory) {
        this.repositoryHandlerFactory = repositoryHandlerFactory;
    }

    public ConfigurationHandler getConfigurations() {
        return configurationContainer;
    }

    public void setInternalRepository(InternalRepository internalRepository) {
        this.internalRepository = internalRepository;
    }

    public void setConfigurationContainer(ConfigurationHandler configurationContainer) {
        this.configurationContainer = configurationContainer;
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
        return dynamicObjectHelper.getConvention();
    }

    public void setConvention(Convention convention) {
        dynamicObjectHelper.setConvention(convention);
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

    public String getArchivesBaseName() {
        return archivesBaseName;
    }

    public void setArchivesBaseName(String archivesBaseName) {
        this.archivesBaseName = archivesBaseName;
    }

    public IProjectRegistry<ProjectInternal> getProjectRegistry() {
        return projectRegistry;
    }

    public void setProjectRegistry(IProjectRegistry<ProjectInternal> projectRegistry) {
        this.projectRegistry = projectRegistry;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractProject that = (AbstractProject) o;

        return path.equals(that.path);
    }

    @Override
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
        return PathHelper.isAbsolutePath(path);
    }

    public Project project(String path) {
        Project project = findProject(path);
        if (project == null) {
            throw new UnknownProjectException(String.format("Project with path '%s' could not be found in %s.", path, this));
        }
        return project;
    }

    public Project findProject(String path) {
        if (!GUtil.isTrue(path)) {
            throw new InvalidUserDataException("A path must be specified!");
        }
        return projectRegistry.getProject(isAbsolutePath(path) ? path : absolutePath(path));
    }

    public Set<Project> getAllprojects() {
        return new TreeSet<Project>(projectRegistry.getAllProjects(this.path));
    }

    public Set<Project> getSubprojects() {
        return new TreeSet<Project>(projectRegistry.getSubProjects(this.path));
    }

    public void subprojects(Action<? super Project> action) {
        applyActions(getSubprojects(), action);
    }

    public void allprojects(Action<? super Project> action) {
        applyActions(getAllprojects(), action);
    }

    public void applyActions(Set<Project> projects, Action<? super Project> action) {
        for (Project project : projects) {
            action.execute(project);
        }
    }

    public AntBuilder getAnt() {
        if (ant == null) {
            ant = createAntBuilder();
        }
        return ant;
    }

    public AntBuilder createAntBuilder() {
        return antBuilderFactory.createAntBuilder();
    }

    /**
     * This method is used when scripts access the project via project.x
     */
    public Project getProject() {
        return this;
    }

    public AbstractProject evaluate() {
        if (state == State.INITIALIZED) {
            return this;
        }
        Clock clock = new Clock();
        beforeEvaluateActions.getSource().execute(this);
        state = State.INITIALIZING;
        projectEvaluator.evaluate(this);
        logger.debug("Timing: Running the build script took " + clock.getTime());
        state = State.INITIALIZED;
        afterEvaluateActions.getSource().execute(this);
        logger.info(String.format("Project %s evaluated using %s.", path, getBuildScriptSource().getDisplayName()));
        logger.debug("Timing: Project evaluation took " + clock.getTime());
        return this;
    }

    public Project usePlugin(String pluginName) {
        return usePlugin(pluginName, new HashMap<String, Object>());
    }

    public Project usePlugin(String pluginName, Map<String, ?> customValues) {
        if (usePluginInternal(pluginRegistry.getPlugin(pluginName), customValues) == null) {
            throw new InvalidUserDataException("Plugin with id " + pluginName + " can not be found!");
        }
        return this;
    }

    public Project usePlugin(Class<? extends Plugin> pluginClass) {
        return usePlugin(pluginClass, new HashMap<String, Object>());
    }

    public Project usePlugin(Class<? extends Plugin> pluginClass, Map<String, ?> customValues) {
        if (usePluginInternal(pluginRegistry.getPlugin(pluginClass), customValues) == null) {
            throw new InvalidUserDataException("Plugin class " + pluginClass + " can not be found!");
        }
        return this;
    }

    private Plugin usePluginInternal(Plugin plugin, Map<String, ?> customValues) {
        if (plugin == null) {
            return null;
        }
        pluginRegistry.apply(plugin.getClass(), this, customValues);
        plugins.add(plugin);
        return plugin;
    }

    public Task findTask(String path) {
        if (!GUtil.isTrue(path)) {
            throw new InvalidUserDataException("A path must be specified!");
        }
        if (!path.contains(PATH_SEPARATOR)) {
            return taskContainer.findByName(path);
        }

        String projectPath = StringUtils.substringBeforeLast(path, PATH_SEPARATOR);
        Project project = findProject(!GUtil.isTrue(projectPath) ? PATH_SEPARATOR : projectPath);
        if (project == null) {
            return null;
        }
        return project.task(StringUtils.substringAfterLast(path, PATH_SEPARATOR));
    }

    public TaskContainer getTasks() {
        return taskContainer;
    }

    public Task task(String path) {
        Task task = findTask(path);
        if (task == null) {
            throw new UnknownTaskException(String.format("Task with path '%s' could not be found in %s.", path, this));
        }
        return task;
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
        return taskContainer.add(args, name, action);
    }

    public void addChildProject(ProjectInternal childProject) {
        childProjects.put(childProject.getName(), childProject);
    }

    public File getProjectDir() {
        return projectDir;
    }

    public void setProjectDir(File projectDir) {
        this.projectDir = projectDir;
    }

    public File getBuildDir() {
        return GFileUtils.canonicalise(new File(getProjectDir(), buildDirName));
    }

    public void dependsOn(String path) {
        dependsOn(path, true);
    }

    public void dependsOn(String path, boolean evaluateDependsOnProject) {
        if (!GUtil.isTrue(path)) {
            throw new InvalidUserDataException("You must specify a project!");
        }
        dependsOnProjects.add(project(path));
        if (evaluateDependsOnProject) {
            evaluationDependsOn(path);
        }
    }

    public Project evaluationDependsOn(String path) {
        if (!GUtil.isTrue(path)) {
            throw new InvalidUserDataException("You must specify a project!");
        }
        DefaultProject projectToEvaluate = (DefaultProject) project(path);
        if (projectToEvaluate.getState() == State.INITIALIZING) {
            throw new CircularReferenceException(String.format("Circular referencing during evaluation for %s.",
                    projectToEvaluate));
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
        if (parent != null) {
            return String.format("project '%s'", path);
        } else {
            return String.format("root project '%s'", name);
        }
    }

    public Map<Project, Set<Task>> getAllTasks(boolean recursive) {
        final Map<Project, Set<Task>> foundTargets = new TreeMap<Project, Set<Task>>();
        ProjectAction action = new ProjectAction() {
            public void execute(Project project) {
                foundTargets.put(project, new TreeSet<Task>(project.getTasks().getAll()));
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
                Task task = project.findTask(name);
                if (task != null) {
                    foundTasks.add(task);
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
        return GFileUtils.canonicalise(baseDirConverter.baseDir(path.toString(), getProjectDir(), validation));
    }

    public FileCollection files(Object... paths) {
        return new PathResolvingFileCollection(this, paths);
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
            if (taskContainer.findByName(name) != null) {
                if (!(taskContainer.findByName(name) instanceof Directory)) {
                    throw new InvalidUserDataException("A non directory task with this name already exsists.");
                }
            } else {
                taskContainer.add(name, Directory.class);
            }
        }
        return task(path);
    }

    public void setTaskContainer(DefaultTaskContainer taskContainer) {
        this.taskContainer = taskContainer;
    }

    public AntBuilderFactory getAntBuilderFactory() {
        return antBuilderFactory;
    }

    public void setAntBuilderFactory(AntBuilderFactory antBuilderFactory) {
        this.antBuilderFactory = antBuilderFactory;
    }

    public DependencyHandler getDependencies() {
        return dependencyHandler;
    }

    public void setDependencyHandler(DependencyHandler dependencyHandler) {
        this.dependencyHandler = dependencyHandler;
    }

    public ConfigurationContainerFactory getConfigurationContainerFactory() {
        return configurationContainerFactory;
    }

    public void setConfigurationContainerFactory(ConfigurationContainerFactory configurationContainerFactory) {
        this.configurationContainerFactory = configurationContainerFactory;
    }

    public PublishArtifactFactory getPublishArtifactFactory() {
        return publishArtifactFactory;
    }

    public void setPublishArtifactFactory(PublishArtifactFactory publishArtifactFactory) {
        this.publishArtifactFactory = publishArtifactFactory;
    }

    public void beforeEvaluate(Action<? super Project> action) {
        beforeEvaluateActions.add(action);
    }

    public void afterEvaluate(Action<? super Project> action) {
        afterEvaluateActions.add(action);
    }

    public void beforeEvaluate(Closure closure) {
        beforeEvaluateActions.add("execute", closure);
    }

    public void afterEvaluate(Closure closure) {
        afterEvaluateActions.add("execute", closure);
    }

    public Logger getLogger() {
        return buildLogger;
    }

    public StandardOutputRedirector getStandardOutputRedirector() {
        return standardOutputRedirector;
    }

    public void setStandardOutputRedirector(StandardOutputRedirector standardOutputRedirector) {
        this.standardOutputRedirector = standardOutputRedirector;
    }

    public void disableStandardOutputCapture() {
        standardOutputRedirector.flush();
        standardOutputRedirector.off();
    }

    public void captureStandardOutput(LogLevel level) {
        standardOutputRedirector.on(level);
    }

    public Object property(String propertyName) throws MissingPropertyException {
        return dynamicObjectHelper.getProperty(propertyName);
    }

    public boolean hasProperty(String propertyName) {
        return dynamicObjectHelper.hasProperty(propertyName);
    }

    public Map<String, ?> getProperties() {
        return dynamicObjectHelper.getProperties();
    }
}
