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

import com.google.common.collect.Maps;
import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;
import org.gradle.api.Action;
import org.gradle.api.AntBuilder;
import org.gradle.api.CircularReferenceException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.PathValidation;
import org.gradle.api.Project;
import org.gradle.api.ProjectConfigurationException;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.Task;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.CompatibilityRuleChain;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DeleteSpec;
import org.gradle.api.file.FileTree;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.DynamicPropertyNamer;
import org.gradle.api.internal.ExtensibleDynamicObject;
import org.gradle.api.internal.FactoryNamedDomainObjectContainer;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.NoConventionMapping;
import org.gradle.api.internal.ProcessOperations;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.plugins.DefaultObjectConfigurationAction;
import org.gradle.api.internal.plugins.ExtensionContainerInternal;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.resources.ResourceHandler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.configuration.project.ProjectConfigurationActionContainer;
import org.gradle.configuration.project.ProjectEvaluator;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.Actions;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.StandardOutputCapture;
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.internal.typeconversion.TypeConverter;
import org.gradle.listener.ClosureBackedMethodInvocationDispatch;
import org.gradle.model.Model;
import org.gradle.model.RuleSource;
import org.gradle.model.dsl.internal.NonTransformedModelDslBacking;
import org.gradle.model.dsl.internal.TransformedModelDslBacking;
import org.gradle.model.internal.core.DefaultNodeInitializerRegistry;
import org.gradle.model.internal.core.Hidden;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelRegistrations;
import org.gradle.model.internal.core.NodeInitializerRegistry;
import org.gradle.model.internal.manage.binding.StructBindingsStore;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static java.util.Collections.singletonMap;
import static org.gradle.api.internal.artifacts.ArtifactAttributes.*;
import static org.gradle.util.GUtil.addMaps;
import static org.gradle.util.GUtil.isTrue;

@NoConventionMapping
public class DefaultProject extends AbstractPluginAware implements ProjectInternal, DynamicObjectAware {
    private static final Action<AttributeMatchingStrategy<String>> ARTIFACT_ATTRIBUTE_CONFIG = new Action<AttributeMatchingStrategy<String>>() {
        @Override
        public void execute(AttributeMatchingStrategy<String> stringAttributeMatchingStrategy) {
            CompatibilityRuleChain<String> compatibilityRules = stringAttributeMatchingStrategy.getCompatibilityRules();
            compatibilityRules.assumeCompatibleWhenMissing();
        }
    };
    private static final Action<AttributesSchema> CONFIGURE_DEFAULT_SCHEMA_ACTION = new Action<AttributesSchema>() {
        @Override
        public void execute(AttributesSchema attributesSchema) {
            attributesSchema.attribute(ARTIFACT_FORMAT, ARTIFACT_ATTRIBUTE_CONFIG);
            attributesSchema.attribute(ARTIFACT_CLASSIFIER, ARTIFACT_ATTRIBUTE_CONFIG);
            attributesSchema.attribute(ARTIFACT_EXTENSION, ARTIFACT_ATTRIBUTE_CONFIG);
        }
    };

    private static final ModelType<ServiceRegistry> SERVICE_REGISTRY_MODEL_TYPE = ModelType.of(ServiceRegistry.class);
    private static final ModelType<File> FILE_MODEL_TYPE = ModelType.of(File.class);
    private static final ModelType<ProjectIdentifier> PROJECT_IDENTIFIER_MODEL_TYPE = ModelType.of(ProjectIdentifier.class);
    private static final ModelType<ExtensionContainer> EXTENSION_CONTAINER_MODEL_TYPE = ModelType.of(ExtensionContainer.class);
    private static final Logger BUILD_LOGGER = Logging.getLogger(Project.class);

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

    private final Map<String, Project> childProjects = Maps.newTreeMap();

    private List<String> defaultTasks = new ArrayList<String>();

    private ProjectStateInternal state;

    private FileResolver fileResolver;

    private Factory<AntBuilder> antBuilderFactory;

    private AntBuilder ant;

    private Object buildDir = Project.DEFAULT_BUILD_DIR_NAME;

    private File buildDirCached;

    private final int depth;

    private TaskContainerInternal taskContainer;

    private DependencyHandler dependencyHandler;

    private ConfigurationContainer configurationContainer;

    private ArtifactHandler artifactHandler;

    private ListenerBroadcast<ProjectEvaluationListener> evaluationListener = new ListenerBroadcast<ProjectEvaluationListener>(ProjectEvaluationListener.class);

    private ExtensibleDynamicObject extensibleDynamicObject;

    private String description;

    private final Path path;
    private Path identityPath;

    private final AttributesSchema configurationAttributesSchema;

    public DefaultProject(String name,
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
            path = parent.getProjectPath().child(name);
            depth = parent.getDepth() + 1;
        }

        services = serviceRegistryFactory.createFor(this);
        taskContainer = services.newInstance(TaskContainerInternal.class);

        extensibleDynamicObject = new ExtensibleDynamicObject(this, Project.class, services.get(Instantiator.class));
        if (parent != null) {
            extensibleDynamicObject.setParent(parent.getInheritedScope());
        }
        extensibleDynamicObject.addObject(taskContainer.getTasksAsDynamicObject(), ExtensibleDynamicObject.Location.AfterConvention);

        evaluationListener.add(gradle.getProjectEvaluationBroadcaster());

        configurationAttributesSchema = services.get(AttributesSchema.class);

        populateModelRegistry(services.get(ModelRegistry.class));
        configureSchema();
    }

    private void configureSchema() {
        configurationAttributesSchema(CONFIGURE_DEFAULT_SCHEMA_ACTION);
    }

    static class BasicServicesRules extends RuleSource {
        @Hidden @Model
        SourceDirectorySetFactory sourceDirectorySetFactory(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(SourceDirectorySetFactory.class);
        }

        @Hidden @Model
        ITaskFactory taskFactory(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(ITaskFactory.class);
        }

        @Hidden @Model
        Instantiator instantiator(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(Instantiator.class);
        }

        @Hidden @Model
        ModelSchemaStore schemaStore(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(ModelSchemaStore.class);
        }

        @Hidden @Model
        ManagedProxyFactory proxyFactory(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(ManagedProxyFactory.class);
        }

        @Hidden @Model
        StructBindingsStore structBindingsStore(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(StructBindingsStore.class);
        }

        @Hidden @Model
        NodeInitializerRegistry nodeInitializerRegistry(ModelSchemaStore schemaStore, StructBindingsStore structBindingsStore) {
            return new DefaultNodeInitializerRegistry(schemaStore, structBindingsStore);
        }

        @Hidden @Model
        TypeConverter typeConverter(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(TypeConverter.class);
        }

        @Hidden @Model
        FileOperations fileOperations(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(FileOperations.class);
        }
    }

    private void populateModelRegistry(ModelRegistry modelRegistry) {
        registerServiceOn(modelRegistry, "serviceRegistry", SERVICE_REGISTRY_MODEL_TYPE, services, instanceDescriptorFor("serviceRegistry"));
        // TODO:LPTR This ignores changes to Project.buildDir after model node has been created
        registerFactoryOn(modelRegistry, "buildDir", FILE_MODEL_TYPE, new Factory<File>() {
            @Override
            public File create() {
                return getBuildDir();
            }
        });
        registerInstanceOn(modelRegistry, "projectIdentifier", PROJECT_IDENTIFIER_MODEL_TYPE, this);
        registerInstanceOn(modelRegistry, "extensionContainer", EXTENSION_CONTAINER_MODEL_TYPE, getExtensions());
        modelRegistry.getRoot().applyToSelf(BasicServicesRules.class);
    }

    private <T> void registerInstanceOn(ModelRegistry modelRegistry, String path, ModelType<T> type, T instance) {
        registerFactoryOn(modelRegistry, path, type, Factories.constant(instance));
    }

    private <T> void registerFactoryOn(ModelRegistry modelRegistry, String path, ModelType<T> type, Factory<T> factory) {
        modelRegistry.register(ModelRegistrations
            .unmanagedInstance(ModelReference.of(path, type), factory)
            .descriptor(instanceDescriptorFor(path))
            .hidden(true)
            .build());
    }

    private <T> void registerServiceOn(ModelRegistry modelRegistry, String path, ModelType<T> type, T instance, String descriptor) {
        modelRegistry.register(ModelRegistrations.serviceInstance(ModelReference.of(path, type), instance)
            .descriptor(descriptor)
            .build()
        );
    }

    private String instanceDescriptorFor(String path) {
        return "Project.<init>." + path + "()";
    }

    public ProjectInternal getRootProject() {
        return rootProject;
    }

    public GradleInternal getGradle() {
        return gradle;
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
        group = rootProject.getName() + (getParent() == rootProject ? "" : "." + getParent().getPath().substring(1).replace(':', '.'));
        return group;
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

    @Override
    public Path getIdentityPath() {
        if (identityPath == null) {
            if (parent == null) {
                identityPath = gradle.getIdentityPath();
            } else {
                identityPath = parent.getIdentityPath().child(name);
            }
        }
        return identityPath;
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

    @Override
    public Path identityPath(String name) {
        return getIdentityPath().child(name);
    }

    @Override
    public Path getProjectPath() {
        return path;
    }

    @Override
    public Path projectPath(String name) {
        return path.child(name);
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

    public DefaultProject evaluate() {
        getProjectEvaluator().evaluate(this, state);
        return this;
    }

    @Override
    public ProjectInternal bindAllModelRules() {
        try {
            getModelRegistry().bindAllReferences();
        } catch (Exception e) {
            throw new ProjectConfigurationException(String.format("A problem occurred configuring %s.", this), e);
        }
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
        if (buildDirCached == null) {
            buildDirCached = file(buildDir);
        }
        return buildDirCached;
    }

    public void setBuildDir(Object path) {
        buildDir = path;
        buildDirCached = null;
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

    @Override
    public String getDisplayName() {
        StringBuilder builder = new StringBuilder();
        if (parent == null && gradle.getParent() == null) {
            builder.append("root project '");
            builder.append(name);
            builder.append('\'');
        } else {
            builder.append("project '");
            builder.append(getIdentityPath());
            builder.append("'");
        }
        return builder.toString();
    }

    public String toString() {
        return getDisplayName();
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
                // Don't force evaluation of rules here, let the task container do what it needs to
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

    public WorkResult delete(Action<? super DeleteSpec> action) {
        return getFileOperations().delete(action);
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
        return BUILD_LOGGER;
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

    public Object findProperty(String propertyName) {
        return hasProperty(propertyName) ? property(propertyName) : null;
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
        return copy(ConfigureUtil.configureUsing(closure));
    }

    public WorkResult copy(Action<? super CopySpec> action) {
        return getFileOperations().copy(action);
    }

    public WorkResult sync(Action<? super CopySpec> action) {
        return getFileOperations().sync(action);
    }

    public CopySpec copySpec(Closure closure) {
        return copySpec(ConfigureUtil.configureUsing(closure));
    }

    public CopySpec copySpec(Action<? super CopySpec> action) {
        return Actions.with(copySpec(), action);
    }

    public CopySpec copySpec() {
        return getFileOperations().copySpec();
    }

    @Inject
    protected ProcessOperations getProcessOperations() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
    }

    public ExecResult javaexec(Closure closure) {
        return javaexec(ConfigureUtil.configureUsing(closure));
    }

    public ExecResult javaexec(Action<? super JavaExecSpec> action) {
        return getProcessOperations().javaexec(action);
    }

    public ExecResult exec(Closure closure) {
        return exec(ConfigureUtil.configureUsing(closure));
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

    public Module getModule() {
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

    @Inject
    protected ModelSchemaStore getModelSchemaStore() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
    }

    @Override
    protected DefaultObjectConfigurationAction createObjectConfigurationAction() {
        return new DefaultObjectConfigurationAction(getFileResolver(), getScriptPluginFactory(), getScriptHandlerFactory(), getBaseClassLoaderScope(), this);
    }

    @Inject
    public PluginManagerInternal getPluginManager() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
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

    // Not part of the public API
    public void model(Closure<?> modelRules) {
        ModelRegistry modelRegistry = getModelRegistry();
        if (TransformedModelDslBacking.isTransformedBlock(modelRules)) {
            ClosureBackedAction.execute(new TransformedModelDslBacking(modelRegistry, this.getRootProject().getFileResolver()), modelRules);
        } else {
            new NonTransformedModelDslBacking(modelRegistry).configure(modelRules);
        }
    }

    @Inject
    protected DeferredProjectConfiguration getDeferredProjectConfiguration() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
    }

    public void addDeferredConfiguration(Runnable configuration) {
        getDeferredProjectConfiguration().add(configuration);
    }

    @Override
    public void fireDeferredConfiguration() {
        getDeferredProjectConfiguration().fire();
    }

    @Override
    public AttributesSchema configurationAttributesSchema(Action<? super AttributesSchema> configureAction) {
        configureAction.execute(configurationAttributesSchema);
        return configurationAttributesSchema;
    }

    @Override
    public AttributesSchema getAttributesSchema() {
        return configurationAttributesSchema;
    }
}
