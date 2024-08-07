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
import org.gradle.api.Action;
import org.gradle.api.AntBuilder;
import org.gradle.api.CircularReferenceException;
import org.gradle.api.InvalidUserCodeException;
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
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.DependencyLockingHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DeleteSpec;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.SyncSpec;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.ProcessOperations;
import org.gradle.api.internal.artifacts.DependencyManagementServices;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.UnknownProjectFinder;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.DefaultProjectLayout;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.internal.plugins.DefaultObjectConfigurationAction;
import org.gradle.api.internal.plugins.ExtensionContainerInternal;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.project.taskfactory.TaskInstantiator;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.project.IsolatedProject;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.resources.ResourceHandler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.configuration.internal.ListenerBuildOperationDecorator;
import org.gradle.configuration.project.ProjectConfigurationActionContainer;
import org.gradle.configuration.project.ProjectEvaluator;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.extensibility.ExtensibleDynamicObject;
import org.gradle.internal.extensibility.NoConventionMapping;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.instantiation.generator.AsmBackedClassGenerator;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.StandardOutputCapture;
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.model.ModelContainer;
import org.gradle.internal.model.RuleBasedPluginListener;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.TextUriResourceLoader;
import org.gradle.internal.service.CloseableServiceRegistry;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
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
import org.gradle.model.internal.core.NamedEntityInstantiator;
import org.gradle.model.internal.core.NodeInitializerRegistry;
import org.gradle.model.internal.manage.binding.StructBindingsStore;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.normalization.InputNormalizationHandler;
import org.gradle.normalization.internal.InputNormalizationHandlerInternal;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.gradle.process.JavaExecSpec;
import org.gradle.util.Configurable;
import org.gradle.util.Path;
import org.gradle.util.internal.ClosureBackedAction;
import org.gradle.util.internal.ConfigureUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
import java.util.concurrent.Callable;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Collections.singletonMap;
import static org.gradle.util.internal.ConfigureUtil.configureUsing;
import static org.gradle.util.internal.GUtil.addMaps;

@NoConventionMapping
public abstract class DefaultProject extends AbstractPluginAware implements ProjectInternal, DynamicObjectAware {
    private static final ModelType<ServiceRegistry> SERVICE_REGISTRY_MODEL_TYPE = ModelType.of(ServiceRegistry.class);
    private static final ModelType<File> FILE_MODEL_TYPE = ModelType.of(File.class);
    private static final ModelType<ProjectIdentifier> PROJECT_IDENTIFIER_MODEL_TYPE = ModelType.of(ProjectIdentifier.class);
    private static final ModelType<ExtensionContainer> EXTENSION_CONTAINER_MODEL_TYPE = ModelType.of(ExtensionContainer.class);
    private static final Logger BUILD_LOGGER = Logging.getLogger(Project.class);

    private final ProjectState owner;
    private final ClassLoaderScope classLoaderScope;
    private final ClassLoaderScope baseClassLoaderScope;
    private final ServiceRegistry services;

    private final ProjectInternal rootProject;

    private final GradleInternal gradle;

    private final ScriptSource buildScriptSource;

    private final File projectDir;

    private final File buildFile;

    @Nullable
    private final ProjectInternal parent;

    private final String name;

    private Object group;

    private Object version;

    private Property<Object> status;

    private List<String> defaultTasks = new ArrayList<>();

    private final ProjectStateInternal state;

    private Factory<AntBuilder> antBuilderFactory;

    private AntBuilder ant;

    private final int depth;

    private final TaskContainerInternal taskContainer;

    private ListenerBroadcast<ProjectEvaluationListener> evaluationListener = newProjectEvaluationListenerBroadcast();

    private final ListenerBroadcast<RuleBasedPluginListener> ruleBasedPluginListenerBroadcast = new ListenerBroadcast<>(RuleBasedPluginListener.class);

    private final ExtensibleDynamicObject extensibleDynamicObject;

    private final DynamicLookupRoutine dynamicLookupRoutine;

    private String description;

    private boolean preparedForRuleBasedPlugins;

    public DefaultProject(
        String name,
        @Nullable ProjectInternal parent,
        File projectDir,
        File buildFile,
        ScriptSource buildScriptSource,
        GradleInternal gradle,
        ProjectState owner,
        ServiceRegistryFactory serviceRegistryFactory,
        ClassLoaderScope selfClassLoaderScope,
        ClassLoaderScope baseClassLoaderScope
    ) {
        this.owner = owner;
        this.classLoaderScope = selfClassLoaderScope;
        this.baseClassLoaderScope = baseClassLoaderScope;
        this.rootProject = parent != null ? parent.getRootProject() : this;
        this.projectDir = projectDir;
        this.buildFile = buildFile;
        this.parent = parent;
        this.name = name;
        this.state = new ProjectStateInternal();
        this.buildScriptSource = buildScriptSource;
        this.gradle = gradle;

        if (parent == null) {
            depth = 0;
        } else {
            depth = parent.getDepth() + 1;
        }

        services = serviceRegistryFactory.createFor(this);
        taskContainer = services.get(TaskContainerInternal.class);

        extensibleDynamicObject = new ExtensibleDynamicObject(this, Project.class, services.get(InstantiatorFactory.class).decorateLenient(services));

        @Nullable DynamicObject parentInherited = services.get(CrossProjectModelAccess.class).parentProjectDynamicInheritedScope(this);
        if (parentInherited != null) {
            extensibleDynamicObject.setParent(parentInherited);
        }
        extensibleDynamicObject.addObject(taskContainer.getTasksAsDynamicObject(), ExtensibleDynamicObject.Location.AfterConvention);

        evaluationListener.add(gradle.getProjectEvaluationBroadcaster());

        ruleBasedPluginListenerBroadcast.add((RuleBasedPluginListener) project -> populateModelRegistry(services.get(ModelRegistry.class)));

        dynamicLookupRoutine = services.get(DynamicLookupRoutine.class);
    }

    @SuppressWarnings("unused")
    static class BasicServicesRules extends RuleSource {
        @Hidden
        @Model
        ProjectLayout projectLayoutService(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(ProjectLayout.class);
        }

        @Hidden
        @Model
        ObjectFactory objectFactory(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(ObjectFactory.class);
        }

        @Hidden
        @Model
        NamedEntityInstantiator<Task> taskFactory(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(TaskInstantiator.class);
        }

        @Hidden
        @Model
        CollectionCallbackActionDecorator collectionCallbackActionDecorator(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(CollectionCallbackActionDecorator.class);
        }

        @Hidden
        @Model
        Instantiator instantiator(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(Instantiator.class);
        }

        @Hidden
        @Model
        ModelSchemaStore schemaStore(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(ModelSchemaStore.class);
        }

        @Hidden
        @Model
        ManagedProxyFactory proxyFactory(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(ManagedProxyFactory.class);
        }

        @Hidden
        @Model
        StructBindingsStore structBindingsStore(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(StructBindingsStore.class);
        }

        @Hidden
        @Model
        NodeInitializerRegistry nodeInitializerRegistry(ModelSchemaStore schemaStore, StructBindingsStore structBindingsStore) {
            return new DefaultNodeInitializerRegistry(schemaStore, structBindingsStore);
        }

        @Hidden
        @Model
        TypeConverter typeConverter(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(TypeConverter.class);
        }

        @Hidden
        @Model
        FileOperations fileOperations(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(FileOperations.class);
        }
    }

    private ListenerBroadcast<ProjectEvaluationListener> newProjectEvaluationListenerBroadcast() {
        return new ListenerBroadcast<>(ProjectEvaluationListener.class);
    }

    private void populateModelRegistry(ModelRegistry modelRegistry) {
        registerServiceOn(modelRegistry, "serviceRegistry", SERVICE_REGISTRY_MODEL_TYPE, services, instanceDescriptorFor("serviceRegistry"));
        // TODO:LPTR This ignores changes to Project.layout.buildDirectory after model node has been created
        registerFactoryOn(modelRegistry, "buildDir", FILE_MODEL_TYPE, () -> getLayout().getBuildDirectory().getAsFile().get());
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

    @Override
    public ProjectInternal getRootProject() {
        return getRootProject(this);
    }

    @Override
    public ProjectInternal getRootProject(ProjectInternal referrer) {
        return getCrossProjectModelAccess().access(referrer, rootProject);
    }

    @Override
    public GradleInternal getGradle() {
        return getCrossProjectModelAccess().gradleInstanceForProject(this, gradle);
    }

    @Inject
    protected abstract ProjectEvaluator getProjectEvaluator();

    @Inject
    @Override
    public abstract ScriptHandlerInternal getBuildscript();

    @Override
    public File getBuildFile() {
        return buildFile;
    }

    @Override
    public void setScript(groovy.lang.Script buildScript) {
        extensibleDynamicObject.addObject(new BeanDynamicObject(buildScript).withNoProperties().withNotImplementsMissing(),
            ExtensibleDynamicObject.Location.BeforeConvention);
    }

    @Override
    public ScriptSource getBuildScriptSource() {
        return buildScriptSource;
    }

    @Override
    public File getRootDir() {
        return rootProject.getProjectDir();
    }

    @Override
    @Nullable
    public ProjectInternal getParent() {
        return getParent(this);
    }

    @Nullable
    @Override
    public ProjectInternal getParent(ProjectInternal referrer) {
        if (parent == null) {
            return null;
        }
        return getCrossProjectModelAccess().access(referrer, parent);
    }

    @Nullable
    @Override
    public ProjectIdentifier getParentIdentifier() {
        return parent;
    }

    @Override
    public DynamicObject getAsDynamicObject() {
        return extensibleDynamicObject;
    }

    @Override
    public DynamicObject getInheritedScope() {
        return extensibleDynamicObject.getInheritable();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    @Nullable
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    @Override
    public Object getGroup() {
        if (group != null) {
            return group;
        } else if (this.equals(rootProject)) {
            return "";
        }
        group = rootProject.getName() + (getParent().equals(rootProject) ? "" : "." + getParent().getPath().substring(1).replace(':', '.'));
        return group;
    }

    @Override
    public void setGroup(Object group) {
        this.group = group;
    }

    @Override
    public Object getVersion() {
        return version == null ? DEFAULT_VERSION : version;
    }

    @Override
    public void setVersion(Object version) {
        this.version = version;
    }

    @Override
    public Object getStatus() {
        return getInternalStatus().get();
    }

    @Override
    public void setStatus(Object s) {
        getInternalStatus().set(s);
    }

    @Override
    public Property<Object> getInternalStatus() {
        if (status == null) {
            status = getObjects().property(Object.class).convention(DEFAULT_STATUS);
        }
        return status;
    }

    @Override
    public Map<String, Project> getChildProjectsUnchecked() {
        Map<String, Project> childProjects = new TreeMap<>();
        for (ProjectState project : owner.getChildProjects()) {
            childProjects.put(project.getName(), project.getMutableModel());
        }
        return childProjects;
    }

    @Override
    public Map<String, Project> getChildProjects() {
        return getChildProjects(this);
    }

    @Override
    public Map<String, Project> getChildProjects(ProjectInternal referrer) {
        return getCrossProjectModelAccess().getChildProjects(referrer, this);
    }

    @Override
    public List<String> getDefaultTasks() {
        return defaultTasks;
    }

    @Override
    public void setDefaultTasks(List<String> defaultTasks) {
        this.defaultTasks = defaultTasks;
    }

    @Override
    public ProjectStateInternal getState() {
        return state;
    }

    @Inject
    @Override
    public abstract FileResolver getFileResolver();

    @Inject
    @Override
    public abstract TaskDependencyFactory getTaskDependencyFactory();

    public void setAnt(AntBuilder ant) {
        this.ant = ant;
    }

    @Inject
    @Override
    public abstract ArtifactHandler getArtifacts();

    @Inject
    @Override
    public abstract RepositoryHandler getRepositories();

    @Inject
    @Override
    public abstract RoleBasedConfigurationContainerInternal getConfigurations();

    @Deprecated
    @Override
    public org.gradle.api.plugins.Convention getConvention() {
        return extensibleDynamicObject.getConvention();
    }

    @Override
    public String getPath() {
        return owner.getProjectPath().toString();
    }

    @Override
    public String getBuildTreePath() {
        return getIdentityPath().getPath();
    }

    @Override
    public Path getIdentityPath() {
        return owner.getIdentityPath();
    }

    @Override
    public int getDepth() {
        return depth;
    }

    @Inject
    protected abstract CrossProjectModelAccess getCrossProjectModelAccess();

    @Override
    public int depthCompare(Project otherProject) {
        return ProjectOrderingUtil.depthCompare(this, otherProject);
    }

    @Override
    public int compareTo(Project otherProject) {
        return ProjectOrderingUtil.compare(this, otherProject);
    }

    @Override
    public String absoluteProjectPath(String path) {
        return getProjectPath().absolutePath(path);
    }

    @Override
    public Path identityPath(String name) {
        return getIdentityPath().child(name);
    }

    @Override
    public Path getProjectPath() {
        return owner.getProjectPath();
    }

    @Nonnull
    @Override
    public ProjectIdentity getProjectIdentity() {
        return owner.getIdentity();
    }

    @Override
    public ModelContainer<ProjectInternal> getModel() {
        return getOwner();
    }

    @Override
    public Path getBuildPath() {
        return gradle.getIdentityPath();
    }

    @Override
    public Path projectPath(String name) {
        return getProjectPath().child(name);
    }

    @Override
    public boolean isScript() {
        return false;
    }

    @Override
    public boolean isRootScript() {
        return false;
    }

    @Override
    public boolean isPluginContext() {
        return false;
    }

    @Override
    public String relativeProjectPath(String path) {
        return getProjectPath().relativePath(path);
    }

    @Override
    public ProjectInternal project(String path) {
        return project(this, path);
    }

    @Override
    public ProjectInternal project(ProjectInternal referrer, String path) throws UnknownProjectException {
        ProjectInternal project = getCrossProjectModelAccess().findProject(referrer, this, path);
        if (project == null) {
            throw new UnknownProjectException(String.format("Project with path '%s' could not be found in %s.", path, this));
        }
        return project;
    }

    @Override
    public ProjectInternal findProject(String path) {
        return findProject(this, path);
    }

    @Nullable
    @Override
    public ProjectInternal findProject(ProjectInternal referrer, String path) {
        return getCrossProjectModelAccess().findProject(referrer, this, path);
    }

    @Override
    public Set<Project> getAllprojects() {
        return Cast.uncheckedCast(getAllprojects(this));
    }

    @Override
    public Set<? extends ProjectInternal> getAllprojects(ProjectInternal referrer) {
        return getCrossProjectModelAccess().getAllprojects(referrer, this);
    }

    @Override
    public void allprojects(Closure configureClosure) {
        allprojects(this, ConfigureUtil.configureUsing(configureClosure));
    }

    @Override
    public void allprojects(Action<? super Project> action) {
        allprojects(this, action);
    }

    @Override
    public void allprojects(ProjectInternal referrer, Action<? super Project> action) {
        getProjectConfigurator().allprojects(getAllprojects(referrer), action);
    }

    @Override
    public Set<Project> getSubprojects() {
        return Cast.uncheckedCast(getSubprojects(this));
    }

    @Override
    public Set<? extends ProjectInternal> getSubprojects(ProjectInternal referrer) {
        return getCrossProjectModelAccess().getSubprojects(referrer, this);
    }

    @Override
    public void subprojects(Closure configureClosure) {
        subprojects(this, ConfigureUtil.configureUsing(configureClosure));
    }

    @Override
    public void subprojects(Action<? super Project> action) {
        subprojects(this, action);
    }

    @Override
    public void subprojects(ProjectInternal referrer, Action<? super Project> configureAction) {
        getProjectConfigurator().subprojects(getSubprojects(referrer), configureAction);
    }

    @Override
    public <T> Iterable<T> configure(Iterable<T> objects, Action<? super T> configureAction) {
        for (T object : objects) {
            configureAction.execute(object);
        }
        return objects;
    }

    @Override
    public AntBuilder getAnt() {
        if (ant == null) {
            ant = createAntBuilder();
        }
        return ant;
    }

    @Override
    public AntBuilder createAntBuilder() {
        return getAntBuilderFactory().create();
    }

    /**
     * This method is used when scripts access the project via project.
     */
    @Override
    public ProjectInternal getProject() {
        return this;
    }

    @Override
    public IsolatedProject getIsolated() {
        return new DefaultIsolatedProject(this, rootProject);
    }

    @Override
    public DefaultProject evaluate() {
        getProjectEvaluator().evaluate(this, state);
        return this;
    }

    @Override
    public ProjectInternal bindAllModelRules() {
        try {
            getModelRegistry().bindAllReferences();
        } catch (Exception e) {
            throw new ProjectConfigurationException(String.format("A problem occurred configuring %s.", getDisplayName()), e);
        }
        return this;
    }

    @Override
    public TaskContainerInternal getTasks() {
        return taskContainer;
    }

    @Override
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

    @Override
    public File getProjectDir() {
        return projectDir;
    }

    @Override
    @Deprecated
    public File getBuildDir() {
        return getLayout().getBuildDirectory().getAsFile().get();
    }

    @Override
    @Deprecated
    public void setBuildDir(File path) {
        setBuildDir((Object) path);
    }

    @Override
    @Deprecated
    public void setBuildDir(Object path) {
        getLayout().setBuildDirectory(path);
    }

    @Override
    public void evaluationDependsOnChildren() {
        for (ProjectState project : owner.getChildProjects()) {
            ProjectInternal defaultProjectToEvaluate = project.getMutableModel();
            evaluationDependsOn(defaultProjectToEvaluate);
        }
    }

    @Override
    public Project evaluationDependsOn(String path) {
        if (isNullOrEmpty(path)) {
            throw new InvalidUserDataException("You must specify a project!");
        }
        ProjectInternal projectToEvaluate = project(path);
        return evaluationDependsOn(projectToEvaluate);
    }

    private Project evaluationDependsOn(ProjectInternal projectToEvaluate) {
        if (projectToEvaluate.getState().isConfiguring()) {
            throw new CircularReferenceException(String.format("Circular referencing during evaluation for %s.",
                projectToEvaluate));
        }
        projectToEvaluate.getOwner().ensureConfigured();
        return projectToEvaluate;
    }

    @Override
    public String getDisplayName() {
        StringBuilder builder = new StringBuilder();
        if (parent == null && gradle.isRootBuild()) {
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

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public Map<Project, Set<Task>> getAllTasks(boolean recursive) {
        final Map<Project, Set<Task>> foundTargets = new TreeMap<Project, Set<Task>>();
        Action<Project> action = new Action<Project>() {
            @Override
            public void execute(Project project) {
                // Don't force evaluation of rules here, let the task container do what it needs to
                ((ProjectInternal) project).getOwner().ensureTasksDiscovered();

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

    @Override
    public Set<Task> getTasksByName(final String name, boolean recursive) {
        if (isNullOrEmpty(name)) {
            throw new InvalidUserDataException("Name is not specified!");
        }
        final Set<Task> foundTasks = new HashSet<Task>();
        Action<Project> action = new Action<Project>() {
            @Override
            public void execute(Project project) {
                // Don't force evaluation of rules here, let the task container do what it needs to
                ((ProjectInternal) project).getOwner().ensureTasksDiscovered();

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
    @Override
    public abstract FileOperations getFileOperations();

    @Inject
    @Override
    public abstract ProviderFactory getProviders();

    @Inject
    @Override
    public abstract ObjectFactory getObjects();

    @Inject
    @Override
    public abstract DefaultProjectLayout getLayout();

    @Override
    public File file(Object path) {
        return getFileOperations().file(path);
    }

    @Override
    public File file(Object path, PathValidation validation) {
        return getFileOperations().file(path, validation);
    }

    @Override
    public URI uri(Object path) {
        return getFileOperations().uri(path);
    }

    @Override
    public ConfigurableFileCollection files(Object... paths) {
        return getObjects().fileCollection().from(paths);
    }

    @Override
    public ConfigurableFileCollection files(Object paths, Closure closure) {
        return ConfigureUtil.configure(closure, files(paths));
    }

    @Override
    public ConfigurableFileCollection files(Object paths, Action<? super ConfigurableFileCollection> configureAction) {
        ConfigurableFileCollection files = files(paths);
        configureAction.execute(files);
        return files;
    }

    @Override
    public ConfigurableFileTree fileTree(Object baseDir) {
        return getFileOperations().fileTree(baseDir);
    }

    @Override
    public ConfigurableFileTree fileTree(Object baseDir, Closure closure) {
        return ConfigureUtil.configure(closure, fileTree(baseDir));
    }

    @Override
    public ConfigurableFileTree fileTree(Object baseDir, Action<? super ConfigurableFileTree> configureAction) {
        ConfigurableFileTree fileTree = fileTree(baseDir);
        configureAction.execute(fileTree);
        return fileTree;
    }

    @Override
    public ConfigurableFileTree fileTree(Map<String, ?> args) {
        return getFileOperations().fileTree(args);
    }

    @Override
    public FileTree zipTree(Object zipPath) {
        return getFileOperations().zipTree(zipPath);
    }

    @Override
    public FileTree tarTree(Object tarPath) {
        return getFileOperations().tarTree(tarPath);
    }

    @Override
    public <T> Provider<T> provider(Callable<? extends T> value) {
        return getProviders().provider(value);
    }

    @Override
    public ResourceHandler getResources() {
        return getFileOperations().getResources();
    }

    @Override
    public String relativePath(Object path) {
        return getFileOperations().relativePath(path);
    }

    @Override
    public File mkdir(Object path) {
        return getFileOperations().mkdir(path);
    }

    @Override
    public boolean delete(Object... paths) {
        return getFileOperations().delete(paths);
    }

    @Override
    public WorkResult delete(Action<? super DeleteSpec> action) {
        return getFileOperations().delete(action);
    }

    public Factory<AntBuilder> getAntBuilderFactory() {
        if (antBuilderFactory == null) {
            antBuilderFactory = services.getFactory(AntBuilder.class);
        }
        return antBuilderFactory;
    }

    @Inject
    @Override
    public abstract DependencyHandler getDependencies();

    @Inject
    @Override
    public abstract DependencyFactory getDependencyFactory();

    @Override
    public ProjectEvaluationListener getProjectEvaluationBroadcaster() {
        return evaluationListener.getSource();
    }

    @Override
    public void beforeEvaluate(Action<? super Project> action) {
        assertEagerContext("beforeEvaluate(Action)");
        evaluationListener.add("beforeEvaluate", getListenerBuildOperationDecorator().decorate("Project.beforeEvaluate", action));
    }

    @Override
    public void afterEvaluate(Action<? super Project> action) {
        assertEagerContext("afterEvaluate(Action)");
        failAfterProjectIsEvaluated("afterEvaluate(Action)");
        evaluationListener.add("afterEvaluate", getListenerBuildOperationDecorator().decorate("Project.afterEvaluate", action));
    }

    @Override
    public void beforeEvaluate(Closure closure) {
        assertEagerContext("beforeEvaluate(Closure)");
        evaluationListener.add(new ClosureBackedMethodInvocationDispatch("beforeEvaluate", getListenerBuildOperationDecorator().decorate("Project.beforeEvaluate", Cast.<Closure<?>>uncheckedNonnullCast(closure))));
    }

    @Override
    public void afterEvaluate(Closure closure) {
        assertEagerContext("afterEvaluate(Closure)");
        failAfterProjectIsEvaluated("afterEvaluate(Closure)");
        evaluationListener.add(new ClosureBackedMethodInvocationDispatch("afterEvaluate", getListenerBuildOperationDecorator().decorate("Project.afterEvaluate", Cast.<Closure<?>>uncheckedNonnullCast(closure))));
    }

    private void failAfterProjectIsEvaluated(String methodPrototype) {
        if (!state.isUnconfigured() && !state.isConfiguring()) {
            throw new InvalidUserCodeException("Cannot run Project." + methodPrototype + " when the project is already evaluated.");
        }
    }

    @Override
    public Logger getLogger() {
        return BUILD_LOGGER;
    }

    @Override
    public StandardOutputCapture getStandardOutputCapture() {
        return getLogging();
    }

    @Inject
    @Override
    public abstract LoggingManagerInternal getLogging();

    @Inject
    @Override
    public abstract SoftwareComponentContainer getComponents();

    @Override
    public void components(Action<? super SoftwareComponentContainer> configuration) {
        configuration.execute(getComponents());
    }

    /**
     * This is an implementation of the {@link groovy.lang.GroovyObject}'s corresponding method.
     * The interface itself is mixed-in at runtime, but we want to keep this implementation as it
     * properly handles the dynamicLookupRoutine.
     *
     * @see AsmBackedClassGenerator.ClassBuilderImpl#addDynamicMethods
     */
    @SuppressWarnings("JavadocReference")
    @Nullable
    public Object getProperty(String propertyName) {
        return property(propertyName);
    }

    /**
     * This is an implementation of the {@link groovy.lang.GroovyObject}'s corresponding method.
     * The interface itself is mixed-in at runtime, but we want to keep this implementation as it
     * properly handles the dynamicLookupRoutine.
     *
     * @see AsmBackedClassGenerator.ClassBuilderImpl#addDynamicMethods
     */
    @SuppressWarnings("JavadocReference")
    @Nullable
    public Object invokeMethod(String name, Object args) {
        if (args instanceof Object[]) {
            // Spread the 'args' array as varargs:
            return dynamicLookupRoutine.invokeMethod(extensibleDynamicObject, name, (Object[]) args);
        } else {
            return dynamicLookupRoutine.invokeMethod(extensibleDynamicObject, name, args);
        }
    }

    @Override
    public Object property(String propertyName) throws MissingPropertyException {
        return dynamicLookupRoutine.property(extensibleDynamicObject, propertyName);
    }

    @Override
    public Object findProperty(String propertyName) {
        return dynamicLookupRoutine.findProperty(extensibleDynamicObject, propertyName);
    }

    @Override
    public void setProperty(String name, Object value) {
        dynamicLookupRoutine.setProperty(extensibleDynamicObject, name, value);
    }

    @Override
    public boolean hasProperty(String propertyName) {
        return dynamicLookupRoutine.hasProperty(extensibleDynamicObject, propertyName);
    }

    @Override
    public Map<String, ?> getProperties() {
        return dynamicLookupRoutine.getProperties(extensibleDynamicObject);
    }

    @Override
    public WorkResult copy(Closure closure) {
        return copy(configureUsing(closure));
    }

    @Override
    public WorkResult copy(Action<? super CopySpec> action) {
        return getFileOperations().copy(action);
    }

    @Override
    public WorkResult sync(Action<? super SyncSpec> action) {
        return getFileOperations().sync(action);
    }

    @Override
    public CopySpec copySpec(Closure closure) {
        return ConfigureUtil.configure(closure, copySpec());
    }

    @Override
    public CopySpec copySpec(Action<? super CopySpec> action) {
        return Actions.with(copySpec(), action);
    }

    @Override
    public CopySpec copySpec() {
        return getFileOperations().copySpec();
    }

    @Inject
    @Override
    public abstract ProcessOperations getProcessOperations();

    @Override
    public ExecResult javaexec(Closure closure) {
        return javaexec(configureUsing(closure));
    }

    @Override
    public ExecResult javaexec(Action<? super JavaExecSpec> action) {
        return getProcessOperations().javaexec(action);
    }

    @Override
    public ExecResult exec(Closure closure) {
        return exec(configureUsing(closure));
    }

    @Override
    public ExecResult exec(Action<? super ExecSpec> action) {
        return getProcessOperations().exec(action);
    }

    @Override
    public ServiceRegistry getServices() {
        return services;
    }

    @Override
    public ServiceRegistryFactory getServiceRegistryFactory() {
        return services.get(ServiceRegistryFactory.class);
    }

    @Override
    public AntBuilder ant(Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, getAnt());
    }

    @Override
    public AntBuilder ant(Action<? super AntBuilder> configureAction) {
        AntBuilder ant = getAnt();
        configureAction.execute(ant);
        return ant;
    }

    @Override
    public Project project(String path, Closure configureClosure) {
        return project(this, path, ConfigureUtil.configureUsing(configureClosure));
    }

    @Override
    public Project project(String path, Action<? super Project> configureAction) {
        return project(this, path, configureAction);
    }

    @Override
    public ProjectInternal project(ProjectInternal referrer, String path, Action<? super Project> configureAction) {
        ProjectInternal project = project(referrer, path);
        getProjectConfigurator().project(project, configureAction);
        return project;
    }

    @Override
    public Object configure(Object object, Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, object);
    }

    @Override
    public Iterable<?> configure(Iterable<?> objects, Closure configureClosure) {
        for (Object object : objects) {
            configure(object, configureClosure);
        }
        return objects;
    }

    @Override
    public void configurations(Closure configureClosure) {
        ((Configurable<?>) getConfigurations()).configure(configureClosure);
    }

    @Override
    public void repositories(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getRepositories());
    }

    @Override
    public void dependencies(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getDependencies());
    }

    @Override
    public void artifacts(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getArtifacts());
    }

    @Override
    public void artifacts(Action<? super ArtifactHandler> configureAction) {
        configureAction.execute(getArtifacts());
    }

    @Override
    public void buildscript(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getBuildscript());
    }

    @Override
    public Task task(String task) {
        return taskContainer.create(task);
    }

    public Task task(Object task) {
        return taskContainer.create(task.toString());
    }

    @Override
    public Task task(String task, Action<? super Task> configureAction) {
        return taskContainer.create(task, configureAction);
    }

    @Override
    public Task task(String task, Closure configureClosure) {
        return taskContainer.create(task).configure(configureClosure);
    }

    public Task task(Object task, Closure configureClosure) {
        return task(task.toString(), configureClosure);
    }

    @Override
    public Task task(Map options, String task) {
        return taskContainer.create(addMaps(Cast.uncheckedNonnullCast(options), singletonMap(Task.TASK_NAME, task)));
    }

    public Task task(Map options, Object task) {
        return task(options, task.toString());
    }

    @Override
    public Task task(Map options, String task, Closure configureClosure) {
        return taskContainer.create(addMaps(Cast.uncheckedNonnullCast(options), singletonMap(Task.TASK_NAME, task))).configure(configureClosure);
    }

    public Task task(Map options, Object task, Closure configureClosure) {
        return task(options, task.toString(), configureClosure);
    }

    @Inject
    @Override
    public abstract ProjectConfigurationActionContainer getConfigurationActions();

    @Inject
    @Override
    public abstract ModelRegistry getModelRegistry();

    @Override
    protected DefaultObjectConfigurationAction createObjectConfigurationAction() {
        TextUriResourceLoader.Factory textUriResourceLoaderFactory = services.get(TextUriResourceLoader.Factory.class);
        return new DefaultObjectConfigurationAction(getFileResolver(), getScriptPluginFactory(), getScriptHandlerFactory(), getBaseClassLoaderScope(), textUriResourceLoaderFactory, this);
    }

    @Inject
    @Override
    public abstract PluginManagerInternal getPluginManager();

    @Inject
    protected abstract ScriptPluginFactory getScriptPluginFactory();

    @Inject
    protected abstract ScriptHandlerFactory getScriptHandlerFactory();

    @Override
    public ClassLoaderScope getClassLoaderScope() {
        return classLoaderScope;
    }

    @Override
    public ClassLoaderScope getBaseClassLoaderScope() {
        return baseClassLoaderScope;
    }

    /**
     * This is called by the task creation DSL. Need to find a cleaner way to do this...
     */
    public Object passThrough(Object object) {
        return object;
    }

    @Override
    public <T> NamedDomainObjectContainer<T> container(Class<T> type) {
        return getServices().get(DomainObjectCollectionFactory.class).newNamedDomainObjectContainerUndecorated(type);
    }

    @Override
    public <T> NamedDomainObjectContainer<T> container(Class<T> type, NamedDomainObjectFactory<T> factory) {
        return getServices().get(DomainObjectCollectionFactory.class).newNamedDomainObjectContainer(type, factory);
    }

    @Override
    public <T> NamedDomainObjectContainer<T> container(Class<T> type, Closure factoryClosure) {
        return getServices().get(DomainObjectCollectionFactory.class).newNamedDomainObjectContainer(type, factoryClosure);
    }

    @Override
    public ExtensionContainerInternal getExtensions() {
        return (ExtensionContainerInternal) DeprecationLogger.whileDisabled(this::getConvention);
    }

    // Not part of the public API
    public void model(Closure<?> modelRules) {
        prepareForRuleBasedPlugins();
        ModelRegistry modelRegistry = getModelRegistry();
        if (TransformedModelDslBacking.isTransformedBlock(modelRules)) {
            ClosureBackedAction.execute(new TransformedModelDslBacking(modelRegistry, this.getRootProject().getFileResolver()), modelRules);
        } else {
            new NonTransformedModelDslBacking(modelRegistry).configure(modelRules);
        }
    }

    @Inject
    protected abstract DeferredProjectConfiguration getDeferredProjectConfiguration();

    @Inject
    protected abstract CrossProjectConfigurator getProjectConfigurator();

    @Inject
    protected abstract ListenerBuildOperationDecorator getListenerBuildOperationDecorator();

    @Override
    public void addDeferredConfiguration(Runnable configuration) {
        getDeferredProjectConfiguration().add(configuration);
    }

    @Override
    public void fireDeferredConfiguration() {
        getDeferredProjectConfiguration().fire();
    }


    @Override
    public void addRuleBasedPluginListener(RuleBasedPluginListener listener) {
        if (preparedForRuleBasedPlugins) {
            listener.prepareForRuleBasedPlugins(this);
        } else {
            ruleBasedPluginListenerBroadcast.add(listener);
        }
    }

    @Override
    public void prepareForRuleBasedPlugins() {
        if (!preparedForRuleBasedPlugins) {
            preparedForRuleBasedPlugins = true;
            ruleBasedPluginListenerBroadcast.getSource().prepareForRuleBasedPlugins(this);
        }
    }

    @Inject
    @Override
    public abstract InputNormalizationHandlerInternal getNormalization();

    @Override
    public void normalization(Action<? super InputNormalizationHandler> configuration) {
        configuration.execute(getNormalization());
    }

    @Inject
    @Override
    public abstract DependencyLockingHandler getDependencyLocking();

    @Override
    public void dependencyLocking(Action<? super DependencyLockingHandler> configuration) {
        configuration.execute(getDependencyLocking());
    }

    @Override
    public ProjectEvaluationListener stepEvaluationListener(ProjectEvaluationListener listener, Action<ProjectEvaluationListener> step) {
        ListenerBroadcast<ProjectEvaluationListener> original = this.evaluationListener;
        ListenerBroadcast<ProjectEvaluationListener> nextBatch = newProjectEvaluationListenerBroadcast();
        this.evaluationListener = nextBatch;
        try {
            step.execute(listener);
        } finally {
            this.evaluationListener = original;
        }
        return nextBatch.isEmpty()
            ? null
            : nextBatch.getSource();
    }

    /**
     * Assert that the current thread is not running a lazy action on a domain object within this project.
     *  This method should be called by methods that must not be called in lazy actions.
     */
    private void assertEagerContext(String methodName) {
        getProjectConfigurator().getLazyBehaviorGuard().assertEagerContext(methodName, this, Project.class);
    }

    @Override
    public ProjectState getOwner() {
        return owner;
    }

    @Override
    public DetachedResolver newDetachedResolver() {
        DependencyManagementServices dms = getServices().get(DependencyManagementServices.class);
        InstantiatorFactory instantiatorFactory = services.get(InstantiatorFactory.class);
        CloseableServiceRegistry lookup = ServiceRegistryBuilder.builder()
            .displayName("detached resolver services")
            .parent(services)
            .provider(new ServiceRegistrationProvider() {
                @Provides
                public DependencyResolutionServices createDependencyResolutionServices(
                    FileResolver fileResolver,
                    FileCollectionFactory fileCollectionFactory,
                    DependencyMetaDataProvider dependencyMetaDataProvider,
                    DomainObjectContext domainObjectContext
                ) {
                    return dms.create(
                        fileResolver,
                        fileCollectionFactory,
                        dependencyMetaDataProvider,
                        new UnknownProjectFinder("Detached resolvers do not support resolving projects"),
                        new DetachedDependencyResolutionDomainObjectContext(domainObjectContext)
                    );
                }
            })
            .build();

        return instantiatorFactory.decorate(lookup).newInstance(
            LocalDetachedResolver.class
        );
    }

    public static class LocalDetachedResolver implements DetachedResolver {
        private final DependencyResolutionServices resolutionServices;

        @Inject
        public LocalDetachedResolver(DependencyResolutionServices resolutionServices) {
            this.resolutionServices = resolutionServices;
        }

        @Override
        public RepositoryHandler getRepositories() {
            return resolutionServices.getResolveRepositoryHandler();
        }

        @Override
        public DependencyHandler getDependencies() {
            return resolutionServices.getDependencyHandler();
        }

        @Override
        public ConfigurationContainer getConfigurations() {
            return resolutionServices.getConfigurationContainer();
        }
    }

    private static class DetachedDependencyResolutionDomainObjectContext implements DomainObjectContext {
        private final DomainObjectContext delegate;

        private DetachedDependencyResolutionDomainObjectContext(DomainObjectContext delegate) {
            this.delegate = delegate;
        }

        @Override
        public Path identityPath(String name) {
            return delegate.identityPath(name);
        }

        @Override
        public Path projectPath(String name) {
            return delegate.projectPath(name);
        }

        @Override
        @Nullable
        public ProjectInternal getProject() {
            return delegate.getProject();
        }

        @Nullable
        @Override
        public ProjectIdentity getProjectIdentity() {
            return delegate.getProjectIdentity();
        }

        @Override
        public ModelContainer<?> getModel() {
            return delegate.getModel();
        }

        @Override
        public Path getBuildPath() {
            return delegate.getBuildPath();
        }

        @Override
        public boolean isRootScript() {
            return delegate.isRootScript();
        }

        @Override
        public boolean isPluginContext() {
            return delegate.isPluginContext();
        }

        @Override
        public boolean isScript() {
            return delegate.isScript();
        }

        @Override
        public boolean isDetachedState() {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "detached context of " + delegate.getDisplayName();
        }
    }
}
