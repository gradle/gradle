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

package org.gradle.api.internal.project;

import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;
import org.gradle.api.*;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileTree;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.*;
import org.gradle.api.internal.artifacts.ModuleInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.plugins.ExtensionContainerInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.LoggingManager;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.resources.ResourceHandler;
import org.gradle.api.tasks.Directory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.WorkResult;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.configuration.project.ProjectConfigurationActionContainer;
import org.gradle.configuration.project.ProjectEvaluator;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.listener.ClosureBackedMethodInvocationDispatch;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.StandardOutputCapture;
import org.gradle.model.ModelPath;
import org.gradle.model.ModelRules;
import org.gradle.model.dsl.internal.GroovyModelDsl;
import org.gradle.model.internal.ModelRegistry;
import org.gradle.process.ExecResult;
import org.gradle.util.Configurable;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.Path;

import java.io.File;
import java.net.URI;
import java.util.*;

import static java.util.Collections.singletonMap;
import static org.gradle.util.GUtil.addMaps;
import static org.gradle.util.GUtil.isTrue;

public abstract class AbstractProject extends AbstractPluginAware implements ProjectInternal, DynamicObjectAware {
    private static Logger buildLogger = Logging.getLogger(Project.class);
    private final ClassLoaderScope classLoaderScope;
    private ServiceRegistry services;

    private final ProjectInternal rootProject;

    private final GradleInternal gradle;

    private ProjectEvaluator projectEvaluator;

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

    private ProjectStateInternal state;

    private FileResolver fileResolver;
    private FileOperations fileOperations;
    private ProcessOperations processOperations;

    private Factory<AntBuilder> antBuilderFactory;

    private AntBuilder ant;

    private Object buildDir = Project.DEFAULT_BUILD_DIR_NAME;

    private PluginContainer pluginContainer;

    private final int depth;

    private TaskContainerInternal taskContainer;

    private TaskContainerInternal implicitTasksContainer;

    private ProjectRegistry<ProjectInternal> projectRegistry;

    private DependencyHandler dependencyHandler;

    private ConfigurationContainerInternal configurationContainer;

    private ArtifactHandler artifactHandler;

    private RepositoryHandler repositoryHandler;

    private ScriptHandler scriptHandler;

    private ListenerBroadcast<ProjectEvaluationListener> evaluationListener = new ListenerBroadcast<ProjectEvaluationListener>(ProjectEvaluationListener.class);

    private LoggingManagerInternal loggingManager;

    private SoftwareComponentContainer softwareComponentContainer;

    private ExtensibleDynamicObject extensibleDynamicObject;

    private ProjectConfigurationActionContainer configurationActions;

    private final ModelRegistry modelRegistry;
    private final ModelRules modelRules;

    private String description;

    private final Path path;
    private final ScriptPluginFactory scriptPluginFactory;
    private final ScriptHandlerFactory scriptHandlerFactory;

    public AbstractProject(String name,
                           ProjectInternal parent,
                           File projectDir,
                           ScriptSource buildScriptSource,
                           GradleInternal gradle,
                           ServiceRegistryFactory serviceRegistryFactory,
                           ClassLoaderScope classLoaderScope
    ) {
        this.classLoaderScope = classLoaderScope;
        assert name != null;
        this.rootProject = parent != null ? parent.getRootProject() : this;
        this.projectDir = projectDir;
        this.parent = parent;
        this.name = name;
        this.state = new ProjectStateInternal();
        this.buildScriptSource = buildScriptSource;
        this.gradle = gradle;

        if (parent == null) {
            path = Path.ROOT;
            depth = 0;
        } else {
            String path = parent.absoluteProjectPath(name);
            depth = parent.getDepth() + 1;
            this.path = Path.path(path);
        }

        services = serviceRegistryFactory.createFor(this);
        fileResolver = services.get(FileResolver.class);
        antBuilderFactory = services.getFactory(AntBuilder.class);
        taskContainer = services.newInstance(TaskContainerInternal.class);
        implicitTasksContainer = services.newInstance(TaskContainerInternal.class);
        fileOperations = services.get(FileOperations.class);
        processOperations = services.get(ProcessOperations.class);
        projectEvaluator = services.get(ProjectEvaluator.class);
        repositoryHandler = services.get(RepositoryHandler.class);
        configurationContainer = services.get(ConfigurationContainerInternal.class);
        pluginContainer = services.get(PluginContainer.class);
        artifactHandler = services.get(ArtifactHandler.class);
        dependencyHandler = services.get(DependencyHandler.class);
        scriptHandler = services.get(ScriptHandler.class);
        projectRegistry = services.get(ProjectRegistry.class);
        loggingManager = services.get(LoggingManagerInternal.class);
        softwareComponentContainer = services.get(SoftwareComponentContainer.class);
        scriptPluginFactory = services.get(ScriptPluginFactory.class);
        scriptHandlerFactory = services.get(ScriptHandlerFactory.class);
        configurationActions = services.get(ProjectConfigurationActionContainer.class);
        modelRegistry = services.get(ModelRegistry.class);
        modelRules = services.get(ModelRules.class);

        extensibleDynamicObject = new ExtensibleDynamicObject(this, services.get(Instantiator.class));
        if (parent != null) {
            extensibleDynamicObject.setParent(parent.getInheritedScope());
        }
        extensibleDynamicObject.addObject(taskContainer.getTasksAsDynamicObject(), ExtensibleDynamicObject.Location.AfterConvention);

        evaluationListener.add(gradle.getProjectEvaluationBroadcaster());

        final ModelPath tasksModelPath = ModelPath.path(TaskContainerInternal.MODEL_PATH);
        modelRules.register(tasksModelPath.toString(), taskContainer);
        taskContainer.all(new Action<Task>() {
            public void execute(Task task) {
                String name = task.getName();
                modelRules.register(tasksModelPath.child(name).toString(), Task.class, new TaskFactory(taskContainer, name));
            }
        });
        taskContainer.whenObjectRemoved(new Action<Task>() {
            public void execute(Task task) {
                modelRules.remove(tasksModelPath.child(task.getName()).toString());
            }
        });
    }

    private static class TaskFactory implements Factory<Task> {
        private final TaskContainer tasks;
        private final String name;

        private TaskFactory(TaskContainer tasks, String name) {
            this.tasks = tasks;
            this.name = name;
        }

        public Task create() {
            return tasks.getByName(name);
        }
    }

    public ProjectInternal getRootProject() {
        return rootProject;
    }

    public GradleInternal getGradle() {
        return gradle;
    }

    public PluginContainer getPlugins() {
        return pluginContainer;
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

    public File getBuildFile() {
        return getBuildscript().getSourceFile();
    }

    public void setScript(groovy.lang.Script buildScript) {
        extensibleDynamicObject.addObject(new BeanDynamicObject(buildScript).withNoProperties().withNotImplementsMissing(),
                ExtensibleDynamicObject.Location.BeforeConvention);
    }

    public ScriptSource getBuildScriptSource() {
        return buildScriptSource;
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

    public DynamicObject getAsDynamicObject() {
        return extensibleDynamicObject;
    }

    public DynamicObject getInheritedScope() {
        return extensibleDynamicObject.getInheritable();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Object getGroup() {
        if (group != null) {
            return group;
        } else if (this == rootProject) {
            return "";
        }
        return rootProject.getName() + (getParent() == rootProject ? "" : "." + getParent().getPath().substring(1).replace(':', '.'));
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

    public ProjectStateInternal getState() {
        return state;
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

    public ConfigurationContainerInternal getConfigurations() {
        return configurationContainer;
    }

    public void setConfigurationContainer(ConfigurationContainerInternal configurationContainer) {
        this.configurationContainer = configurationContainer;
    }

    public Convention getConvention() {
        return extensibleDynamicObject.getConvention();
    }

    public String getPath() {
        return path.toString();
    }

    public int getDepth() {
        return depth;
    }

    public ProjectRegistry<ProjectInternal> getProjectRegistry() {
        return projectRegistry;
    }

    public int depthCompare(Project otherProject) {
        return new Integer(getDepth()).compareTo(otherProject.getDepth());
    }

    public int compareTo(Project otherProject) {
        int depthCompare = depthCompare(otherProject);
        if (depthCompare == 0) {
            return getPath().compareTo(otherProject.getPath());
        } else {
            return depthCompare;
        }
    }

    public String absoluteProjectPath(String path) {
        return this.path.absolutePath(path);
    }

    public String relativeProjectPath(String path) {
        return this.path.relativePath(path);
    }

    public ProjectInternal project(String path) {
        ProjectInternal project = findProject(path);
        if (project == null) {
            throw new UnknownProjectException(String.format("Project with path '%s' could not be found in %s.", path, this));
        }
        return project;
    }

    public ProjectInternal findProject(String path) {
        if (!isTrue(path)) {
            throw new InvalidUserDataException("A path must be specified!");
        }
        return projectRegistry.getProject(absoluteProjectPath(path));
    }

    public Set<Project> getAllprojects() {
        return new TreeSet<Project>(projectRegistry.getAllProjects(getPath()));
    }

    public Set<Project> getSubprojects() {
        return new TreeSet<Project>(projectRegistry.getSubProjects(getPath()));
    }

    public void subprojects(Action<? super Project> action) {
        configure(getSubprojects(), action);
    }

    public void allprojects(Action<? super Project> action) {
        configure(getAllprojects(), action);
    }

    public <T> Iterable<T> configure(Iterable<T> objects, Action<? super T> configureAction) {
        for (T object : objects) {
            configureAction.execute(object);
        }
        return objects;
    }

    public AntBuilder getAnt() {
        if (ant == null) {
            ant = createAntBuilder();
        }
        return ant;
    }

    public AntBuilder createAntBuilder() {
        return antBuilderFactory.create();
    }

    /**
     * This method is used when scripts access the project via project.x
     */
    public Project getProject() {
        return this;
    }

    public AbstractProject evaluate() {
        projectEvaluator.evaluate(this, state);
        state.rethrowFailure();
        return this;
    }

    public TaskContainerInternal getTasks() {
        return taskContainer;
    }

    public TaskContainerInternal getImplicitTasks() {
        return implicitTasksContainer;
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

    public Task createTask(Map args, String name, Closure action) {
        warnCreateTaskDeprecated();
        Map<String, Object> allArgs = new HashMap<String, Object>(args);
        allArgs.put(Task.TASK_NAME, name);
        allArgs.put(Task.TASK_ACTION, action);
        return taskContainer.create(allArgs);
    }

    public Task createTask(Map<String, ?> args, String name, Action<? super Task> action) {
        warnCreateTaskDeprecated();
        Map<String, Object> allArgs = new HashMap<String, Object>(args);
        allArgs.put(Task.TASK_NAME, name);
        if (action != null) {
            allArgs.put(Task.TASK_ACTION, action);
        }
        return taskContainer.create(allArgs);
    }

    private void warnCreateTaskDeprecated() {
        DeprecationLogger.nagUserOfReplacedMethod("Project.createTask()", "task()");
    }

    public void addChildProject(ProjectInternal childProject) {
        childProjects.put(childProject.getName(), childProject);
    }

    public File getProjectDir() {
        return projectDir;
    }

    public File getBuildDir() {
        return file(buildDir);
    }

    public void setBuildDir(Object path) {
        buildDir = path;
    }

    public void dependsOn(final String path) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("Project.dependsOn(String path)");
        DeprecationLogger.whileDisabled(new Factory<Void>() {
            public Void create() {
                dependsOn(path, true);
                return null;
            }
        });
    }

    public void dependsOn(String path, boolean evaluateDependsOnProject) {
        if (!isTrue(path)) {
            throw new InvalidUserDataException("You must specify a project!");
        }
        dependsOnProjects.add(project(path));
        if (evaluateDependsOnProject) {
            evaluationDependsOn(path);
        }
    }

    public void evaluationDependsOnChildren() {
        for (Project project : childProjects.values()) {
            DefaultProject defaultProjectToEvaluate = (DefaultProject) project;
            evaluationDependsOn(defaultProjectToEvaluate);
        }
    }

    public Project evaluationDependsOn(String path) {
        if (!isTrue(path)) {
            throw new InvalidUserDataException("You must specify a project!");
        }
        DefaultProject projectToEvaluate = (DefaultProject) project(path);
        return evaluationDependsOn(projectToEvaluate);
    }

    private Project evaluationDependsOn(DefaultProject projectToEvaluate) {
        if (projectToEvaluate.getState().getExecuting()) {
            throw new CircularReferenceException(String.format("Circular referencing during evaluation for %s.",
                    projectToEvaluate));
        }
        return projectToEvaluate.evaluate();
    }

    public Project childrenDependOnMe() {
        DeprecationLogger.nagUserOfDiscontinuedMethod("Project.childrenDependOnMe()");
        DeprecationLogger.whileDisabled(new Factory<Void>() {
            public Void create() {
                for (Project project : childProjects.values()) {
                    project.dependsOn(getPath(), false);
                }
                return null;
            }
        });

        return this;
    }

    public Project dependsOnChildren() {
        DeprecationLogger.nagUserOfDiscontinuedMethod("Project.dependsOnChildren()");
        return DeprecationLogger.whileDisabled(new Factory<Project>() {
            public Project create() {
                return dependsOnChildren(false);
            }
        });
    }

    public Project dependsOnChildren(final boolean evaluateDependsOnProject) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("Project.dependsOnChildren(boolean)");
        DeprecationLogger.whileDisabled(new Factory<Void>() {
            public Void create() {
                for (Project project : childProjects.values()) {
                    dependsOn(project.getPath(), evaluateDependsOnProject);
                }
                return null;
            }
        });
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
                foundTargets.put(project, new TreeSet<Task>(project.getTasks()));
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
        if (!isTrue(name)) {
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
        return fileOperations.file(path);
    }

    public File file(Object path, PathValidation validation) {
        return fileOperations.file(path, validation);
    }

    public URI uri(Object path) {
        return fileOperations.uri(path);
    }

    public ConfigurableFileCollection files(Object... paths) {
        return fileOperations.files(paths);
    }

    public ConfigurableFileCollection files(Object paths, Closure closure) {
        return fileOperations.files(paths, closure);
    }

    public ConfigurableFileTree fileTree(Object baseDir) {
        return fileOperations.fileTree(baseDir);
    }

    public ConfigurableFileTree fileTree(Object baseDir, Closure closure) {
        return fileOperations.fileTree(baseDir, closure);
    }

    public ConfigurableFileTree fileTree(Map<String, ?> args) {
        return fileOperations.fileTree(args);
    }

    public ConfigurableFileTree fileTree(Closure closure) {
        DeprecationLogger.nagUserOfDeprecated("fileTree(Closure)", "Use fileTree((Object){ baseDir }) to have the closure used as the file tree base directory");
        return fileOperations.fileTree(closure);
    }

    public FileTree zipTree(Object zipPath) {
        return fileOperations.zipTree(zipPath);
    }

    public FileTree tarTree(Object tarPath) {
        return fileOperations.tarTree(tarPath);
    }

    public ResourceHandler getResources() {
        return fileOperations.getResources();
    }

    public String relativePath(Object path) {
        return fileOperations.relativePath(path);
    }

    public File mkdir(Object path) {
        return fileOperations.mkdir(path);
    }

    public boolean delete(Object... paths) {
        return fileOperations.delete(paths);
    }

    /**
     * @deprecated Use the {@link #mkdir(Object)} instead.
     */
    @Deprecated
    public Directory dir(String path) {
        DeprecationLogger.nagUserOfReplacedMethod("AbstractProject.dir()", "mkdir()");
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
                dirTask = taskContainer.create(name, Directory.class);
            }
        }
        return dirTask;
    }

    public void setTaskContainer(TaskContainerInternal taskContainer) {
        this.taskContainer = taskContainer;
    }

    public Factory<AntBuilder> getAntBuilderFactory() {
        return antBuilderFactory;
    }

    public void setAntBuilderFactory(Factory<AntBuilder> antBuilderFactory) {
        this.antBuilderFactory = antBuilderFactory;
    }

    public DependencyHandler getDependencies() {
        return dependencyHandler;
    }

    public void setDependencyHandler(DependencyHandler dependencyHandler) {
        this.dependencyHandler = dependencyHandler;
    }

    public ProjectEvaluationListener getProjectEvaluationBroadcaster() {
        return evaluationListener.getSource();
    }

    public void beforeEvaluate(Action<? super Project> action) {
        evaluationListener.add("beforeEvaluate", action);
    }

    public void afterEvaluate(Action<? super Project> action) {
        evaluationListener.add("afterEvaluate", action);
    }

    public void beforeEvaluate(Closure closure) {
        evaluationListener.add(new ClosureBackedMethodInvocationDispatch("beforeEvaluate", closure));
    }

    public void afterEvaluate(Closure closure) {
        evaluationListener.add(new ClosureBackedMethodInvocationDispatch("afterEvaluate", closure));
    }

    public Logger getLogger() {
        return buildLogger;
    }

    public StandardOutputCapture getStandardOutputCapture() {
        return loggingManager;
    }

    public LoggingManager getLogging() {
        return loggingManager;
    }

    public SoftwareComponentContainer getComponents() {
        return softwareComponentContainer;
    }

    public Object property(String propertyName) throws MissingPropertyException {
        return extensibleDynamicObject.getProperty(propertyName);
    }

    public void setProperty(String name, Object value) {
        extensibleDynamicObject.setProperty(name, value);
    }

    public boolean hasProperty(String propertyName) {
        return extensibleDynamicObject.hasProperty(propertyName);
    }

    public Map<String, ?> getProperties() {
        return DeprecationLogger.whileDisabled(new Factory<Map<String, ?>>() {
            public Map<String, ?> create() {
                return extensibleDynamicObject.getProperties();
            }
        });
    }

    public WorkResult copy(Closure closure) {
        return fileOperations.copy(closure);
    }

    public WorkResult sync(Action<? super CopySpec> action) {
        return fileOperations.sync(action);
    }

    public CopySpec copySpec(Closure closure) {
        return fileOperations.copySpec(closure);
    }

    public CopySpec copySpec(Action<? super CopySpec> action) {
        return fileOperations.copySpec(action);
    }

    public ExecResult javaexec(Closure closure) {
        return processOperations.javaexec(closure);
    }

    public ExecResult exec(Closure closure) {
        return processOperations.exec(closure);
    }

    public ServiceRegistry getServices() {
        return services;
    }

    public ServiceRegistryFactory getServiceRegistryFactory() {
        return services.get(ServiceRegistryFactory.class);
    }

    public ModuleInternal getModule() {
        return services.get(DependencyMetaDataProvider.class).getModule();
    }

    public AntBuilder ant(Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, getAnt());
    }

    public void subprojects(Closure configureClosure) {
        configure(getSubprojects(), configureClosure);
    }

    public void allprojects(Closure configureClosure) {
        configure(getAllprojects(), configureClosure);
    }

    public Project project(String path, Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, project(path));
    }

    public Object configure(Object object, Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, object);
    }

    public Iterable<?> configure(Iterable<?> objects, Closure configureClosure) {
        for (Object object : objects) {
            configure(object, configureClosure);
        }
        return objects;
    }

    public void configurations(Closure configureClosure) {
        ((Configurable<?>) getConfigurations()).configure(configureClosure);
    }

    public void repositories(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getRepositories());
    }

    public void dependencies(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getDependencies());
    }

    public void artifacts(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getArtifacts());
    }

    public void buildscript(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getBuildscript());
    }

    public Task task(String task) {
        return taskContainer.create(task);
    }

    public Task task(Object task) {
        return taskContainer.create(task.toString());
    }

    public Task task(String task, Closure configureClosure) {
        return taskContainer.create(task).configure(configureClosure);
    }

    public Task task(Object task, Closure configureClosure) {
        return task(task.toString(), configureClosure);
    }

    public Task task(Map options, String task) {
        return taskContainer.create(addMaps(options, singletonMap(Task.TASK_NAME, task)));
    }

    public Task task(Map options, Object task) {
        return task(options, task.toString());
    }

    public Task task(Map options, String task, Closure configureClosure) {
        return taskContainer.create(addMaps(options, singletonMap(Task.TASK_NAME, task))).configure(configureClosure);
    }

    public Task task(Map options, Object task, Closure configureClosure) {
        return task(options, task.toString(), configureClosure);
    }

    public ProjectConfigurationActionContainer getConfigurationActions() {
        return configurationActions;
    }

    public ModelRegistry getModelRegistry() {
        return modelRegistry;
    }

    @Override
    protected ScriptPluginFactory getScriptPluginFactory() {
        return scriptPluginFactory;
    }

    @Override
    protected ScriptHandlerFactory getScriptHandlerFactory() {
        return scriptHandlerFactory;
    }

    @Override
    public ClassLoaderScope getClassLoaderScope() {
        return classLoaderScope;
    }

    /**
     * This is called by the task creation DSL. Need to find a cleaner way to do this...
     */
    public Object passThrough(Object object) {
        return object;
    }

    public <T> NamedDomainObjectContainer<T> container(Class<T> type) {
        Instantiator instantiator = getServices().get(Instantiator.class);
        return instantiator.newInstance(FactoryNamedDomainObjectContainer.class, type, instantiator, new DynamicPropertyNamer());
    }

    public <T> NamedDomainObjectContainer<T> container(Class<T> type, NamedDomainObjectFactory<T> factory) {
        Instantiator instantiator = getServices().get(Instantiator.class);
        return instantiator.newInstance(FactoryNamedDomainObjectContainer.class, type, instantiator, new DynamicPropertyNamer(), factory);
    }

    public <T> NamedDomainObjectContainer<T> container(Class<T> type, Closure factoryClosure) {
        Instantiator instantiator = getServices().get(Instantiator.class);
        return instantiator.newInstance(FactoryNamedDomainObjectContainer.class, type, instantiator, new DynamicPropertyNamer(), factoryClosure);
    }

    public ExtensionContainerInternal getExtensions() {
        return (ExtensionContainerInternal) getConvention();
    }

    // This is here temporarily as a quick way to expose it in the build script
    // Longer term it will not be available via Project, but be only available in a build script
    public void model(Closure action) {
        new GroovyModelDsl(modelRules).configure(action);
    }


}
