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
import org.gradle.api.artifacts.ConfigurationContainer;
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
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.plugins.DefaultObjectConfigurationAction;
import org.gradle.api.internal.plugins.ExtensionContainerInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.resources.ResourceHandler;
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
import org.gradle.model.dsl.internal.NonTransformedModelDslBacking;
import org.gradle.model.dsl.internal.TransformedModelDslBacking;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.gradle.process.JavaExecSpec;
import org.gradle.util.Configurable;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.Path;

import javax.inject.Inject;
import java.io.File;
import java.net.URI;
import java.util.*;

import static java.util.Collections.singletonMap;
import static org.gradle.util.GUtil.addMaps;
import static org.gradle.util.GUtil.isTrue;

public abstract class AbstractProject extends AbstractPluginAware implements ProjectInternal, DynamicObjectAware {

    private static Logger buildLogger = Logging.getLogger(Project.class);
    private final ClassLoaderScope classLoaderScope;
    private final ClassLoaderScope baseClassLoaderScope;
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

    private ProjectStateInternal state;

    private FileResolver fileResolver;

    private Factory<AntBuilder> antBuilderFactory;

    private AntBuilder ant;

    private Object buildDir = Project.DEFAULT_BUILD_DIR_NAME;

    private final int depth;

    private TaskContainerInternal taskContainer;

    private DependencyHandler dependencyHandler;

    private ConfigurationContainer configurationContainer;

    private ArtifactHandler artifactHandler;

    private ListenerBroadcast<ProjectEvaluationListener> evaluationListener = new ListenerBroadcast<ProjectEvaluationListener>(ProjectEvaluationListener.class);

    private ExtensibleDynamicObject extensibleDynamicObject;

    private String description;

    private final Path path;

    public AbstractProject(String name,
                           ProjectInternal parent,
                           File projectDir,
                           ScriptSource buildScriptSource,
                           GradleInternal gradle,
                           ServiceRegistryFactory serviceRegistryFactory,
                           ClassLoaderScope selfClassLoaderScope,
                           ClassLoaderScope baseClassLoaderScope) {
        this.classLoaderScope = selfClassLoaderScope;
        this.baseClassLoaderScope = baseClassLoaderScope;
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
        taskContainer = services.newInstance(TaskContainerInternal.class);

        final ModelRegistry modelRegistry = services.get(ModelRegistry.class);

        modelRegistry.create(
                ModelCreators.of(ModelReference.of("serviceRegistry", ServiceRegistry.class), services)
                        .simpleDescriptor("Project.<init>.serviceRegistry()")
                        .build()
        );

        modelRegistry.create(
                ModelCreators.of(ModelReference.of("buildDir", File.class), new Factory<File>() {
                    public File create() {
                        return getBuildDir();
                    }
                })
                        .simpleDescriptor("Project.<init>.buildDir()")
                        .build()
        );

        modelRegistry.create(
                ModelCreators.of(ModelReference.of("projectIdentifier", ProjectIdentifier.class), this)
                        .simpleDescriptor("Project.<init>.projectIdentifier()")
                        .build()
        );

        modelRegistry.create(
                ModelCreators.of(ModelReference.of("extensions", ExtensionContainer.class), new Factory<ExtensionContainer>() {
                    public ExtensionContainer create() {
                        return getExtensions();
                    }
                })
                        .simpleDescriptor("Project.<init>.extensions()")
                        .build()
        );

        modelRegistry.create(
                ModelCreators.of(ModelReference.of(TaskContainerInternal.MODEL_PATH, ModelType.of(TaskContainer.class)), taskContainer)
                        .simpleDescriptor("Project.<init>.tasks()")
                        .withProjection(new PolymorphicDomainObjectContainerModelProjection<TaskContainerInternal, Task>(taskContainer, Task.class))
                        .build());

        taskContainer.all(new Action<Task>() {
            public void execute(final Task task) {
                final String name = task.getName();
                final ModelPath modelPath = TaskContainerInternal.MODEL_PATH.child(name);

                ModelState state = modelRegistry.state(modelPath);
                if (state == null || state.getStatus() != ModelState.Status.IN_CREATION) {
                    modelRegistry.create(
                            ModelCreators.of(ModelReference.of(modelPath, ModelType.typeOf(task)), task)
                                    .simpleDescriptor("Project.<init>.tasks." + name + "()")
                                    .build()
                    );
                }
            }
        });

        taskContainer.whenObjectRemoved(new Action<Task>() {
            public void execute(Task task) {
                modelRegistry.remove(TaskContainerInternal.MODEL_PATH.child(task.getName()));
            }
        });

        extensibleDynamicObject = new ExtensibleDynamicObject(this, services.get(Instantiator.class));
        if (parent != null) {
            extensibleDynamicObject.setParent(parent.getInheritedScope());
        }
        extensibleDynamicObject.addObject(taskContainer.getTasksAsDynamicObject(), ExtensibleDynamicObject.Location.AfterConvention);

        evaluationListener.add(gradle.getProjectEvaluationBroadcaster());
    }

    public ProjectInternal getRootProject() {
        return rootProject;
    }

    public GradleInternal getGradle() {
        return gradle;
    }

    @Inject
    public PluginContainer getPlugins() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
    }

    public ProjectEvaluator getProjectEvaluator() {
        if (projectEvaluator == null) {
            projectEvaluator = services.get(ProjectEvaluator.class);
        }
        return projectEvaluator;
    }

    public void setProjectEvaluator(ProjectEvaluator projectEvaluator) {
        this.projectEvaluator = projectEvaluator;
    }

    @Inject
    public ScriptHandler getBuildscript() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
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

    public ProjectStateInternal getState() {
        return state;
    }

    public FileResolver getFileResolver() {
        if (fileResolver == null) {
            fileResolver = services.get(FileResolver.class);
        }
        return fileResolver;
    }

    public void setFileResolver(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    public void setAnt(AntBuilder ant) {
        this.ant = ant;
    }

    public ArtifactHandler getArtifacts() {
        if (artifactHandler == null) {
            artifactHandler = services.get(ArtifactHandler.class);
        }
        return artifactHandler;
    }

    public void setArtifactHandler(ArtifactHandler artifactHandler) {
        this.artifactHandler = artifactHandler;
    }

    @Inject
    public RepositoryHandler getRepositories() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
    }

    public ConfigurationContainer getConfigurations() {
        if (configurationContainer == null) {
            configurationContainer = services.get(ConfigurationContainer.class);
        }
        return configurationContainer;
    }

    public void setConfigurationContainer(ConfigurationContainer configurationContainer) {
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

    @Inject
    public ProjectRegistry<ProjectInternal> getProjectRegistry() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
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
        return getProjectRegistry().getProject(absoluteProjectPath(path));
    }

    public Set<Project> getAllprojects() {
        return new TreeSet<Project>(getProjectRegistry().getAllProjects(getPath()));
    }

    public Set<Project> getSubprojects() {
        return new TreeSet<Project>(getProjectRegistry().getSubProjects(getPath()));
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
        return getAntBuilderFactory().create();
    }

    /**
     * This method is used when scripts access the project via project.x
     */
    public Project getProject() {
        return this;
    }

    public AbstractProject evaluate() {
        getProjectEvaluator().evaluate(this, state);
        state.rethrowFailure();
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
                //in configure-on-demand we don't know if the project was configured, hence explicit evaluate.
                // Not especially tidy, we should clean this up while working on new configuration model.
                ((ProjectInternal) project).evaluate();

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

    @Inject
    protected FileOperations getFileOperations() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
    }

    public File file(Object path) {
        return getFileOperations().file(path);
    }

    public File file(Object path, PathValidation validation) {
        return getFileOperations().file(path, validation);
    }

    public URI uri(Object path) {
        return getFileOperations().uri(path);
    }

    public ConfigurableFileCollection files(Object... paths) {
        return getFileOperations().files(paths);
    }

    public ConfigurableFileCollection files(Object paths, Closure closure) {
        return ConfigureUtil.configure(closure, getFileOperations().files(paths));
    }

    public ConfigurableFileTree fileTree(Object baseDir) {
        return getFileOperations().fileTree(baseDir);
    }

    public ConfigurableFileTree fileTree(Object baseDir, Closure closure) {
        return ConfigureUtil.configure(closure, getFileOperations().fileTree(baseDir));
    }

    public ConfigurableFileTree fileTree(Map<String, ?> args) {
        return getFileOperations().fileTree(args);
    }

    public FileTree zipTree(Object zipPath) {
        return getFileOperations().zipTree(zipPath);
    }

    public FileTree tarTree(Object tarPath) {
        return getFileOperations().tarTree(tarPath);
    }

    public ResourceHandler getResources() {
        return getFileOperations().getResources();
    }

    public String relativePath(Object path) {
        return getFileOperations().relativePath(path);
    }

    public File mkdir(Object path) {
        return getFileOperations().mkdir(path);
    }

    public boolean delete(Object... paths) {
        return getFileOperations().delete(paths);
    }

    public Factory<AntBuilder> getAntBuilderFactory() {
        if (antBuilderFactory == null) {
            antBuilderFactory = services.getFactory(AntBuilder.class);
        }
        return antBuilderFactory;
    }

    public DependencyHandler getDependencies() {
        if (dependencyHandler == null) {
            dependencyHandler = services.get(DependencyHandler.class);
        }
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
        return getLogging();
    }

    @Inject
    public LoggingManagerInternal getLogging() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
    }

    @Inject
    public SoftwareComponentContainer getComponents() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
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
        return copy(new ClosureBackedAction<CopySpec>(closure));
    }

    public WorkResult copy(Action<? super CopySpec> action) {
        return getFileOperations().copy(action);
    }

    public WorkResult sync(Action<? super CopySpec> action) {
        return getFileOperations().sync(action);
    }

    public CopySpec copySpec(Closure closure) {
        return copySpec(new ClosureBackedAction<CopySpec>(closure));
    }

    public CopySpec copySpec(Action<? super CopySpec> action) {
        return getFileOperations().copySpec(action);
    }

    @Inject
    protected ProcessOperations getProcessOperations() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
    }

    public ExecResult javaexec(Closure closure) {
        return javaexec(new ClosureBackedAction<JavaExecSpec>(closure));
    }

    public ExecResult javaexec(Action<? super JavaExecSpec> action) {
        return getProcessOperations().javaexec(action);
    }

    public ExecResult exec(Closure closure) {
        return exec(new ClosureBackedAction<ExecSpec>(closure));
    }

    public ExecResult exec(Action<? super ExecSpec> action) {
        return getProcessOperations().exec(action);
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

    @Inject
    public ProjectConfigurationActionContainer getConfigurationActions() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
    }

    @Inject
    public ModelRegistry getModelRegistry() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
    }


    @Override
    protected DefaultObjectConfigurationAction createObjectConfigurationAction() {
        return new DefaultObjectConfigurationAction(getFileResolver(), getScriptPluginFactory(), getScriptHandlerFactory(), getBaseClassLoaderScope(), this);
    }

    @Inject
    protected ScriptPluginFactory getScriptPluginFactory() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ScriptHandlerFactory getScriptHandlerFactory() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
    }

    public ClassLoaderScope getClassLoaderScope() {
        return classLoaderScope;
    }

    public ClassLoaderScope getBaseClassLoaderScope() {
        return baseClassLoaderScope;
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


    public void model(Closure<?> modelRules) {
        if (TransformedModelDslBacking.isTransformedBlock(modelRules)) {
            ClosureBackedAction.execute(new TransformedModelDslBacking(getModelRegistry(), modelRules.getOwner(), modelRules.getThisObject()), modelRules);
        } else {
            new NonTransformedModelDslBacking(getModelRegistry()).configure(modelRules);
        }
    }

}
