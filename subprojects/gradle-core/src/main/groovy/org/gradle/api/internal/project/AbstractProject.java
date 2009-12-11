/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.*;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandlerFactory;
import org.gradle.api.file.*;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.*;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.file.*;
import org.gradle.api.internal.file.archive.TarFileTree;
import org.gradle.api.internal.file.archive.ZipFileTree;
import org.gradle.api.internal.file.copy.CopyActionImpl;
import org.gradle.api.internal.file.copy.CopySpecImpl;
import org.gradle.api.internal.file.copy.FileCopyActionImpl;
import org.gradle.api.internal.file.copy.FileCopySpecVisitor;
import org.gradle.api.internal.initialization.ScriptClassLoaderProvider;
import org.gradle.api.internal.plugins.DefaultConvention;
import org.gradle.api.internal.plugins.DefaultObjectConfigurationAction;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ProjectPluginsContainer;
import org.gradle.api.tasks.Directory;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.internal.file.FileSet;
import org.gradle.configuration.ProjectEvaluator;
import org.gradle.configuration.ScriptObjectConfigurerFactory;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.util.*;

import java.io.File;
import java.util.*;

/**
 * @author Hans Dockter
 */
public abstract class AbstractProject implements ProjectInternal {
    private static Logger logger = Logging.getLogger(AbstractProject.class);
    private static Logger buildLogger = Logging.getLogger(Project.class);
    private ServiceRegistryFactory services;

    public enum State {
        CREATED, INITIALIZING, INITIALIZED
    }

    private final Project rootProject;

    private final GradleInternal gradle;

    private ProjectEvaluator projectEvaluator;

    private File buildFile;

    private Script buildScript;

    private ScriptSource buildScriptSource;

    private final File projectDir;

    private final ProjectInternal parent;

    private final String name;

    private Object group;

    private Object version;

    private Object status;

    private final Map<String, Project> childProjects = new HashMap<String, Project>();

    private List<String> defaultTasks = new ArrayList<String>();

    private Set<Project> dependsOnProjects = new HashSet<Project>();

    private State state;

    private FileResolver fileResolver;

    private AntBuilderFactory antBuilderFactory;

    private AntBuilder ant = null;

    private String buildDirName = Project.DEFAULT_BUILD_DIR_NAME;

    private ProjectPluginsContainer projectPluginsHandler;

    private final String path;

    private final int depth;

    private TaskContainerInternal taskContainer;

    private IProjectRegistry<ProjectInternal> projectRegistry;

    private DependencyHandler dependencyHandler;

    private ConfigurationContainer configurationContainer;

    private ArtifactHandler artifactHandler;

    private RepositoryHandlerFactory repositoryHandlerFactory;

    private RepositoryHandler repositoryHandler;

    private ScriptHandler scriptHandler;

    private ScriptClassLoaderProvider scriptClassLoaderProvider;

    private ListenerBroadcast<Action> afterEvaluateActions = new ListenerBroadcast<Action>(Action.class);
    private ListenerBroadcast<Action> beforeEvaluateActions = new ListenerBroadcast<Action>(Action.class);

    private StandardOutputRedirector standardOutputRedirector;
    private DynamicObjectHelper dynamicObjectHelper;
    private boolean nagged;

    public AbstractProject(String name) {
        this.name = name;
        dynamicObjectHelper = new DynamicObjectHelper(this);
        dynamicObjectHelper.setConvention(new DefaultConvention());
        projectDir = null;
        depth = 0;
        path = Project.PATH_SEPARATOR;
        rootProject = this;
        parent = null;
        gradle = null;
    }

    public AbstractProject(String name,
                           ProjectInternal parent,
                           File projectDir,
                           File buildFile,
                           ScriptSource buildScriptSource,
                           GradleInternal gradle,
                           ServiceRegistryFactory serviceRegistryFactory) {
        assert name != null;
        this.rootProject = parent != null ? parent.getRootProject() : this;
        this.projectDir = projectDir;
        this.parent = parent;
        this.name = name;
        this.buildFile = buildFile;
        this.state = State.CREATED;
        this.buildScriptSource = buildScriptSource;
        this.gradle = gradle;

        if (parent == null) {
            path = Project.PATH_SEPARATOR;
            depth = 0;
        } else {
            path = parent.absolutePath(name);
            depth = parent.getDepth() + 1;
        }

        fileResolver = new BaseDirConverter(getProjectDir());

        services = serviceRegistryFactory.createFor(this);
        antBuilderFactory = services.get(AntBuilderFactory.class);
        taskContainer = services.get(TaskContainerInternal.class);
        repositoryHandlerFactory = services.get(RepositoryHandlerFactory.class);
        projectEvaluator = services.get(ProjectEvaluator.class);
        repositoryHandler = services.get(RepositoryHandler.class);
        configurationContainer = services.get(ConfigurationContainer.class);
        projectPluginsHandler = services.get(ProjectPluginsContainer.class);
        artifactHandler = services.get(ArtifactHandler.class);
        dependencyHandler = services.get(DependencyHandler.class);
        scriptHandler = services.get(ScriptHandler.class);
        scriptClassLoaderProvider = services.get(ScriptClassLoaderProvider.class);
        projectRegistry = services.get(IProjectRegistry.class);
        standardOutputRedirector = services.get(StandardOutputRedirector.class);

        dynamicObjectHelper = new DynamicObjectHelper(this);
        dynamicObjectHelper.setConvention(services.get(Convention.class));
        if (parent != null) {
            dynamicObjectHelper.setParent(parent.getInheritedScope());
        }
        dynamicObjectHelper.addObject(taskContainer.getAsDynamicObject(), DynamicObjectHelper.Location.AfterConvention);
    }

    public RepositoryHandler createRepositoryHandler() {
        RepositoryHandler handler = repositoryHandlerFactory.createRepositoryHandler(getConvention());
        ((IConventionAware) handler).setConventionMapping(((IConventionAware) repositoryHandler).getConventionMapping());
        return handler;
    }

    public Project getRootProject() {
        return rootProject;
    }

    public GradleInternal getGradle() {
        return gradle;
    }

    public ProjectPluginsContainer getPlugins() {
        return projectPluginsHandler;
    }

    public void setProjectPluginsHandler(ProjectPluginsContainer projectPluginsHandler) {
        this.projectPluginsHandler = projectPluginsHandler;
    }

    public ProjectEvaluator getProjectEvaluator() {
        return projectEvaluator;
    }

    public void setProjectEvaluator(ProjectEvaluator projectEvaluator) {
        this.projectEvaluator = projectEvaluator;
    }

    public ScriptHandler getBuildscript() {
        return scriptHandler;
    }

    public ScriptClassLoaderProvider getClassLoaderProvider() {
        return scriptClassLoaderProvider;
    }

    public File getBuildFile() {
        return buildFile;
    }

    public void setBuildFile(File buildFile) {
        this.buildFile = buildFile;
    }

    public Script getScript() {
        return buildScript;
    }

    public void setScript(Script buildScript) {
        if (this.buildScript != null) {
            // Ignore
            return;
        }
        this.buildScript = buildScript;
        dynamicObjectHelper.addObject(new BeanDynamicObject(buildScript).withNoProperties(),
                DynamicObjectHelper.Location.BeforeConvention);
    }

    public ScriptSource getBuildScriptSource() {
        return buildScriptSource;
    }

    public void setBuildScriptSource(ScriptSource buildScriptSource) {
        this.buildScriptSource = buildScriptSource;
    }

    public File getRootDir() {
        return rootProject.getProjectDir();
    }

    public ProjectInternal getParent() {
        return parent;
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
        return group == null ? DEFAULT_GROUP : group;
    }

    public void setGroup(Object group) {
        this.group = group;
    }

    public Object getVersion() {
        return version == null ? DEFAULT_VERSION : version;
    }

    public void setVersion(Object version) {
        this.version = version;
    }

    public Object getStatus() {
        return status == null ? DEFAULT_STATUS : status;
    }

    public void setStatus(Object status) {
        this.status = status;
    }

    public Map<String, Project> getChildProjects() {
        return childProjects;
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

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public FileResolver getFileResolver() {
        return fileResolver;
    }

    public void setFileResolver(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
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

    public ConfigurationContainer getConfigurations() {
        return configurationContainer;
    }

    public void setConfigurationContainer(ConfigurationContainer configurationContainer) {
        this.configurationContainer = configurationContainer;
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

    public String getPath() {
        return path;
    }

    public int getDepth() {
        return depth;
    }

    public IProjectRegistry<ProjectInternal> getProjectRegistry() {
        return projectRegistry;
    }

    public void setProjectRegistry(IProjectRegistry<ProjectInternal> projectRegistry) {
        this.projectRegistry = projectRegistry;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

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
        logger.info(String.format("Evaluating %s using %s.", this, getBuildScriptSource().getDisplayName()));
        beforeEvaluateActions.getSource().execute(this);
        state = State.INITIALIZING;
        projectEvaluator.evaluate(this);
        logger.debug("Timing: Running the build script took " + clock.getTime());
        state = State.INITIALIZED;
        afterEvaluateActions.getSource().execute(this);
        logger.debug("Timing: Project evaluation took " + clock.getTime());
        return this;
    }

    public Project usePlugin(String pluginName) {
        projectPluginsHandler.usePlugin(pluginName, this);
        return this;
    }

    public Project usePlugin(Class<? extends Plugin> pluginClass) {
        projectPluginsHandler.usePlugin(pluginClass, this);
        return this;
    }

    public TaskContainerInternal getTasks() {
        return taskContainer;
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
        return createTask(new HashMap<String, Object>(), name, (Action) null);
    }

    public Task createTask(Map<String, ?> args, String name) {
        return createTask(args, name, (Action) null);
    }

    public Task createTask(String name, Action<? super Task> action) {
        return createTask(new HashMap<String, Object>(), name, action);
    }

    public Task createTask(String name, Closure action) {
        return createTask(new HashMap<String, Object>(), name, action);
    }

    public Task createTask(Map args, String name, Closure action) {
        warnDeprecated();
        Map<String, Object> allArgs = new HashMap<String, Object>(args);
        allArgs.put(Task.TASK_NAME, name);
        allArgs.put(Task.TASK_ACTION, action);
        return taskContainer.add(allArgs);
    }

    public Task createTask(Map<String, ?> args, String name, Action<? super Task> action) {
        warnDeprecated();
        Map<String, Object> allArgs = new HashMap<String, Object>(args);
        allArgs.put(Task.TASK_NAME, name);
        if (action != null) {
            allArgs.put(Task.TASK_ACTION, action);
        }
        return taskContainer.add(allArgs);
    }

    private void warnDeprecated() {
        if (!nagged) {
            logger.warn("The Project.createTask() method is deprecated and will be removed in the next version of Gradle. You should use the task() method instead.");
            nagged = true;
        }
    }

    public void addChildProject(ProjectInternal childProject) {
        childProjects.put(childProject.getName(), childProject);
    }

    public File getProjectDir() {
        return projectDir;
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
        Action<Project> action = new Action<Project>() {
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
        Action<Project> action = new Action<Project>() {
            public void execute(Project project) {
                Task task = project.getTasks().findByName(name);
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
        return fileResolver.resolve(path);
    }

    public File file(Object path, PathValidation validation) {
        return fileResolver.resolve(path, validation);
    }

    public ConfigurableFileCollection files(Object... paths) {
        return new PathResolvingFileCollection(fileResolver, taskContainer, paths);
    }

    public ConfigurableFileCollection files(Object paths, Closure closure) {
        PathResolvingFileCollection result = new PathResolvingFileCollection(fileResolver, taskContainer, paths);
        return ConfigureUtil.configure(closure, result);
    }

    public ConfigurableFileTree fileTree(Object baseDir) {
        return new FileSet(baseDir, fileResolver);
    }

    public FileSet fileTree(Map<String, ?> args) {
        return new FileSet(args, fileResolver);
    }

    public FileSet fileTree(Closure closure) {
        FileSet result = new FileSet(Collections.emptyMap(), fileResolver);
        return ConfigureUtil.configure(closure, result);
    }

    public FileTree zipTree(Object zipPath) {
        return new ZipFileTree(file(zipPath), getExpandDir());
    }

    public FileTree tarTree(Object tarPath) {
        return new TarFileTree(file(tarPath), getExpandDir());
    }

    private File getExpandDir() {
        return new File(getBuildDir(), "tmp/expandedArchives");
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

    public Directory dir(String path) {
        String[] pathElements = path.split("/");
        String name = "";
        Directory dirTask = null;
        for (String pathElement : pathElements) {
            name += name.length() != 0 ? "/" + pathElement : pathElement;
            Task task = taskContainer.findByName(name);
            if (task instanceof Directory) {
                dirTask = (Directory) task;
            } else if (task != null) {
                throw new InvalidUserDataException(String.format("Cannot add directory task '%s' as a non-directory task with this name already exists.", name));
            } else {
                dirTask = taskContainer.add(name, Directory.class);
            }
        }
        return dirTask;
    }

    public void setTaskContainer(TaskContainerInternal taskContainer) {
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

    public WorkResult copy(Closure closure) {
        CopyActionImpl action = new FileCopyActionImpl(fileResolver, new FileCopySpecVisitor());
        configure(action,  closure);
        action.execute();
        return action;
    }

    public CopySpec copySpec(Closure closure) {
        CopySpecImpl copySpec = new CopySpecImpl(fileResolver);
        configure(copySpec, closure);
        return copySpec;
    }

    public ServiceRegistryFactory getServiceRegistryFactory() {
        return services;
    }

    public Module getModuleForResolve() {
        return getServiceRegistryFactory().get(DependencyMetaDataProvider.class).getModuleForResolve();
    }

    public Module getModuleForPublicDescriptor() {
        return getServiceRegistryFactory().get(DependencyMetaDataProvider.class).getModuleForPublicDescriptor();
    }

    public void apply(Closure closure) {
        DefaultObjectConfigurationAction action = new DefaultObjectConfigurationAction(fileResolver, services.get(
                ScriptObjectConfigurerFactory.class), this);
        configure(action, closure);
        action.execute();
    }
}
