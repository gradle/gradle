/*
 * Copyright 2024 the original author or authors.
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
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;
import org.gradle.api.Action;
import org.gradle.api.AntBuilder;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.IsolatedAction;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.PathValidation;
import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.Task;
import org.gradle.api.UnknownProjectException;
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
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.ProcessOperations;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.internal.plugins.ExtensionContainerInternal;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.LoggingManager;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ObjectConfigurationAction;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.project.IsolatedProject;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.resources.ResourceHandler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.configuration.project.ProjectConfigurationActionContainer;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.logging.StandardOutputCapture;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.model.ModelContainer;
import org.gradle.internal.model.RuleBasedPluginListener;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.invocation.IsolatedProjectEvaluationListenerProvider;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.normalization.InputNormalizationHandler;
import org.gradle.normalization.internal.InputNormalizationHandlerInternal;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.gradle.process.JavaExecSpec;
import org.gradle.util.Path;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

public class AllprojectsAwareProject extends GroovyObjectSupport implements ProjectInternal {

    private final ProjectInternal delegate;
    private final IsolatedProjectEvaluationListenerProvider isolatedProjectEvaluationListenerProvider;
    private final GradleInternal gradle;

    private boolean isAllprojectsActionInitialized = false;
    private IsolatedAction<? super ProjectInternal> allprojectsAction = null;

    public AllprojectsAwareProject(
        ProjectInternal delegate,
        IsolatedProjectEvaluationListenerProvider isolatedProjectEvaluationListenerProvider,
        GradleInternal gradle
    ) {
        this.delegate = delegate;
        this.isolatedProjectEvaluationListenerProvider = isolatedProjectEvaluationListenerProvider;
        this.gradle = gradle;
    }

    private void applyAllprojects() {
        if (!isAllprojectsActionInitialized) {
            allprojectsAction = isolatedProjectEvaluationListenerProvider.isolateAllprojectsActionFor(gradle);
            isAllprojectsActionInitialized = true;
        }
        if (allprojectsAction != null) {
            allprojectsAction.execute(delegate);
        }
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        AllprojectsAwareProject project = (AllprojectsAwareProject) other;
        return this.delegate == project.delegate;
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    @Nullable
    public Object invokeMethod(String name, Object args) {
        applyAllprojects();
        return ((DefaultProject) delegate).invokeMethod(name, args);
    }

    @Override
    @Nullable
    public Object getProperty(String propertyName) {
        applyAllprojects();
        return ((DefaultProject) delegate).getProperty(propertyName);
    }

    @Nullable
    @Override
    public ProjectInternal getParent() {
        return delegate.getParent();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    @Nullable
    @Override
    public String getDescription() {
        applyAllprojects();
        return delegate.getDescription();
    }

    @Override
    public void setDescription(@Nullable String description) {
        applyAllprojects();
        delegate.setDescription(description);
    }

    @Override
    public Object getGroup() {
        applyAllprojects();
        return delegate.getGroup();
    }

    @Override
    public void setGroup(Object group) {
        applyAllprojects();
        delegate.setGroup(group);
    }

    @Override
    public Object getVersion() {
        applyAllprojects();
        return delegate.getVersion();
    }

    @Override
    public void setVersion(Object version) {
        applyAllprojects();
        delegate.setVersion(version);
    }

    @Override
    public Object getStatus() {
        applyAllprojects();
        return delegate.getStatus();
    }

    @Override
    public void setStatus(Object status) {
        applyAllprojects();
        delegate.setStatus(status);
    }

    @Nullable
    @Override
    public ProjectInternal getParent(ProjectInternal referrer) {
        return delegate.getParent(referrer);
    }

    @Override
    public ProjectInternal getRootProject() {
        return delegate.getRootProject();
    }

    @Override
    public File getRootDir() {
        return delegate.getRootDir();
    }


    @Override
    @SuppressWarnings("deprecation")
    public File getBuildDir() {
        applyAllprojects();
        return delegate.getBuildDir();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setBuildDir(File path) {
        applyAllprojects();
        delegate.setBuildDir(path);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setBuildDir(Object path) {
        applyAllprojects();
        delegate.setBuildDir(path);
    }

    @Override
    public File getBuildFile() {
        return delegate.getBuildFile();
    }

    @Override
    public ProjectInternal getRootProject(ProjectInternal referrer) {
        return delegate.getRootProject(referrer);
    }

    @Override
    public Project evaluate() {
        return delegate.evaluate();
    }

    @Override
    public ProjectInternal bindAllModelRules() {
        return delegate.bindAllModelRules();
    }

    @Override
    public TaskContainerInternal getTasks() {
        applyAllprojects();
        return delegate.getTasks();
    }

    @Override
    public void subprojects(Action<? super Project> action) {
        delegate.subprojects(action);
    }

    @Override
    public void subprojects(Closure configureClosure) {
        delegate.subprojects(configureClosure);
    }

    @Override
    public void allprojects(Action<? super Project> action) {
        delegate.allprojects(action);
    }

    @Override
    public void allprojects(Closure configureClosure) {
        delegate.allprojects(configureClosure);
    }

    @Override
    public void beforeEvaluate(Action<? super Project> action) {
        delegate.beforeEvaluate(action);
    }

    @Override
    public void afterEvaluate(Action<? super Project> action) {
        delegate.afterEvaluate(action);
    }

    @Override
    public void beforeEvaluate(Closure closure) {
        delegate.beforeEvaluate(closure);
    }

    @Override
    public void afterEvaluate(Closure closure) {
        delegate.afterEvaluate(closure);
    }

    @Override
    public boolean hasProperty(String propertyName) {
        applyAllprojects();
        return delegate.hasProperty(propertyName);
    }

    @Override
    public Map<String, ?> getProperties() {
        applyAllprojects();
        return delegate.getProperties();
    }

    @Nullable
    @Override
    public Object property(String propertyName) throws MissingPropertyException {
        applyAllprojects();
        return delegate.property(propertyName);
    }

    @Nullable
    @Override
    public Object findProperty(String propertyName) {
        applyAllprojects();
        return delegate.findProperty(propertyName);
    }

    @Override
    public Logger getLogger() {
        applyAllprojects();
        return delegate.getLogger();
    }

    @Override
    public ScriptSource getBuildScriptSource() {
        return delegate.getBuildScriptSource();
    }

    @Override
    public ProjectInternal project(String path) throws UnknownProjectException {
        return delegate.project(path);
    }

    @Override
    public Project project(String path, Closure configureClosure) {
        return delegate.project(path, configureClosure);
    }

    @Override
    public Project project(String path, Action<? super Project> configureAction) {
        return delegate.project(path, configureAction);
    }

    @Override
    public Map<Project, Set<Task>> getAllTasks(boolean recursive) {
        applyAllprojects();
        return delegate.getAllTasks(recursive);
    }

    @Override
    public Set<Task> getTasksByName(String name, boolean recursive) {
        applyAllprojects();
        return delegate.getTasksByName(name, recursive);
    }

    @Override
    public File getProjectDir() {
        return delegate.getProjectDir();
    }

    @Override
    public File file(Object path) {
        applyAllprojects();
        return delegate.file(path);
    }

    @Override
    public File file(Object path, PathValidation validation) throws InvalidUserDataException {
        applyAllprojects();
        return delegate.file(path, validation);
    }

    @Override
    public URI uri(Object path) {
        applyAllprojects();
        return delegate.uri(path);
    }

    @Override
    public String relativePath(Object path) {
        applyAllprojects();
        return delegate.relativePath(path);
    }

    @Override
    public ConfigurableFileCollection files(Object... paths) {
        applyAllprojects();
        return delegate.files(paths);
    }

    @Override
    public ConfigurableFileCollection files(Object paths, Closure configureClosure) {
        applyAllprojects();
        return delegate.files(paths, configureClosure);
    }

    @Override
    public ConfigurableFileCollection files(Object paths, Action<? super ConfigurableFileCollection> configureAction) {
        applyAllprojects();
        return delegate.files(paths, configureAction);
    }

    @Override
    public ConfigurableFileTree fileTree(Object baseDir) {
        applyAllprojects();
        return delegate.fileTree(baseDir);
    }

    @Override
    public ConfigurableFileTree fileTree(Object baseDir, Closure configureClosure) {
        applyAllprojects();
        return delegate.fileTree(baseDir, configureClosure);
    }

    @Override
    public ConfigurableFileTree fileTree(Object baseDir, Action<? super ConfigurableFileTree> configureAction) {
        applyAllprojects();
        return delegate.fileTree(baseDir, configureAction);
    }

    @Override
    public ConfigurableFileTree fileTree(Map<String, ?> args) {
        applyAllprojects();
        return delegate.fileTree(args);
    }

    @Override
    public FileTree zipTree(Object zipPath) {
        applyAllprojects();
        return delegate.zipTree(zipPath);
    }

    @Override
    public FileTree tarTree(Object tarPath) {
        applyAllprojects();
        return delegate.tarTree(tarPath);
    }

    @Override
    public <T> Provider<T> provider(Callable<? extends T> value) {
        applyAllprojects();
        return delegate.provider(value);
    }

    @Override
    public ProviderFactory getProviders() {
        applyAllprojects();
        return delegate.getProviders();
    }

    @Override
    public ObjectFactory getObjects() {
        applyAllprojects();
        return delegate.getObjects();
    }

    @Override
    public ProjectLayout getLayout() {
        applyAllprojects();
        return delegate.getLayout();
    }

    @Override
    public File mkdir(Object path) {
        applyAllprojects();
        return delegate.mkdir(path);
    }

    @Override
    public boolean delete(Object... paths) {
        applyAllprojects();
        return delegate.delete(paths);
    }

    @Override
    public WorkResult delete(Action<? super DeleteSpec> action) {
        applyAllprojects();
        return delegate.delete(action);
    }

    @Override
    public ExecResult javaexec(Closure closure) {
        applyAllprojects();
        return delegate.javaexec(closure);
    }

    @Override
    public ExecResult javaexec(Action<? super JavaExecSpec> action) {
        applyAllprojects();
        return delegate.javaexec(action);
    }

    @Override
    public ExecResult exec(Closure closure) {
        applyAllprojects();
        return delegate.exec(closure);
    }

    @Override
    public ExecResult exec(Action<? super ExecSpec> action) {
        applyAllprojects();
        return delegate.exec(action);
    }

    @Override
    public String absoluteProjectPath(String path) {
        return delegate.absoluteProjectPath(path);
    }

    @Override
    public String relativeProjectPath(String path) {
        return delegate.relativeProjectPath(path);
    }

    @Override
    public AntBuilder getAnt() {
        applyAllprojects();
        return delegate.getAnt();
    }

    @Override
    public AntBuilder createAntBuilder() {
        applyAllprojects();
        return delegate.createAntBuilder();
    }

    @Override
    public AntBuilder ant(Closure configureClosure) {
        applyAllprojects();
        return delegate.ant(configureClosure);
    }

    @Override
    public AntBuilder ant(Action<? super AntBuilder> configureAction) {
        applyAllprojects();
        return delegate.ant(configureAction);
    }

    @Override
    public ProjectInternal project(ProjectInternal referrer, String path) throws UnknownProjectException {
        return delegate.project(referrer, path);
    }

    @Override
    public ProjectInternal project(ProjectInternal referrer, String path, Action<? super Project> configureAction) {
        return delegate.project(referrer, path, configureAction);
    }

    @Nullable
    @Override
    public ProjectInternal findProject(String path) {
        return delegate.findProject(path);
    }

    @Nullable
    @Override
    public ProjectInternal findProject(ProjectInternal referrer, String path) {
        return delegate.findProject(referrer, path);
    }

    @Override
    public Set<? extends ProjectInternal> getSubprojects(ProjectInternal referrer) {
        return delegate.getSubprojects(referrer);
    }

    @Override
    public void subprojects(ProjectInternal referrer, Action<? super Project> configureAction) {
        delegate.subprojects(referrer, configureAction);
    }

    @Override
    public Map<String, Project> getChildProjects() {
        return delegate.getChildProjects();
    }

    @Override
    public void setProperty(String name, @Nullable Object value) throws MissingPropertyException {
        applyAllprojects();
        delegate.setProperty(name, value);
    }

    @Override
    public IsolatedProject getIsolated() {
        return delegate.getIsolated();
    }

    @Override
    public Set<Project> getAllprojects() {
        return delegate.getAllprojects();
    }

    @Override
    public Set<Project> getSubprojects() {
        return delegate.getSubprojects();
    }

    @Override
    public Task task(String name) throws InvalidUserDataException {
        applyAllprojects();
        return delegate.task(name);
    }

    @Override
    public Task task(Map<String, ?> args, String name) throws InvalidUserDataException {
        applyAllprojects();
        return delegate.task(args, name);
    }

    @Override
    public Task task(Map<String, ?> args, String name, Closure configureClosure) {
        applyAllprojects();
        return delegate.task(args, name, configureClosure);
    }

    @Override
    public Task task(String name, Closure configureClosure) {
        applyAllprojects();
        return delegate.task(name, configureClosure);
    }

    @Override
    public Task task(String name, Action<? super Task> configureAction) {
        applyAllprojects();
        return delegate.task(name, configureAction);
    }

    @Override
    public String getPath() {
        return delegate.getPath();
    }

    @Nullable
    @Override
    public ProjectIdentifier getParentIdentifier() {
        return delegate.getParentIdentifier();
    }

    @Override
    public String getBuildTreePath() {
        return delegate.getBuildTreePath();
    }

    @Override
    public List<String> getDefaultTasks() {
        applyAllprojects();
        return delegate.getDefaultTasks();
    }

    @Override
    public void setDefaultTasks(List<String> defaultTasks) {
        applyAllprojects();
        delegate.setDefaultTasks(defaultTasks);
    }

    @Override
    public void defaultTasks(String... defaultTasks) {
        applyAllprojects();
        delegate.defaultTasks(defaultTasks);
    }

    @Override
    public Project evaluationDependsOn(String path) throws UnknownProjectException {
        applyAllprojects();
        return delegate.evaluationDependsOn(path);
    }

    @Override
    public void evaluationDependsOnChildren() {
        applyAllprojects();
        delegate.evaluationDependsOnChildren();
    }

    @Override
    public Map<String, Project> getChildProjectsUnchecked() {
        return delegate.getChildProjectsUnchecked();
    }

    @Override
    public Set<? extends ProjectInternal> getAllprojects(ProjectInternal referrer) {
        return delegate.getAllprojects(referrer);
    }

    @Override
    public void allprojects(ProjectInternal referrer, Action<? super Project> configureAction) {
        delegate.allprojects(referrer, configureAction);
    }

    @Override
    public DynamicObject getInheritedScope() {
        return delegate.getInheritedScope();
    }

    @Override
    public GradleInternal getGradle() {
        applyAllprojects();
        return delegate.getGradle();
    }

    @Override
    public LoggingManager getLogging() {
        applyAllprojects();
        return delegate.getLogging();
    }

    @Override
    public Object configure(Object object, Closure configureClosure) {
        applyAllprojects();
        return delegate.configure(object, configureClosure);
    }

    @Override
    public Iterable<?> configure(Iterable<?> objects, Closure configureClosure) {
        applyAllprojects();
        return delegate.configure(objects, configureClosure);
    }

    @Override
    public <T> Iterable<T> configure(Iterable<T> objects, Action<? super T> configureAction) {
        applyAllprojects();
        return delegate.configure(objects, configureAction);
    }

    @Override
    public RepositoryHandler getRepositories() {
        applyAllprojects();
        return delegate.getRepositories();
    }

    @Override
    public void repositories(Closure configureClosure) {
        applyAllprojects();
        delegate.repositories(configureClosure);
    }

    @Override
    public DependencyHandler getDependencies() {
        applyAllprojects();
        return delegate.getDependencies();
    }

    @Override
    public void dependencies(Closure configureClosure) {
        applyAllprojects();
        delegate.dependencies(configureClosure);
    }

    @Override
    public DependencyFactory getDependencyFactory() {
        applyAllprojects();
        return delegate.getDependencyFactory();
    }

    @Override
    public ProjectEvaluationListener getProjectEvaluationBroadcaster() {
        return delegate.getProjectEvaluationBroadcaster();
    }

    @Override
    public void addRuleBasedPluginListener(RuleBasedPluginListener listener) {
        delegate.addRuleBasedPluginListener(listener);
    }

    @Override
    public void prepareForRuleBasedPlugins() {
        delegate.prepareForRuleBasedPlugins();
    }

    @Override
    public FileResolver getFileResolver() {
        return delegate.getFileResolver();
    }

    @Override
    public TaskDependencyFactory getTaskDependencyFactory() {
        return delegate.getTaskDependencyFactory();
    }

    @Override
    public ServiceRegistry getServices() {
        return delegate.getServices();
    }

    @Override
    public ServiceRegistryFactory getServiceRegistryFactory() {
        return delegate.getServiceRegistryFactory();
    }

    @Override
    public StandardOutputCapture getStandardOutputCapture() {
        return delegate.getStandardOutputCapture();
    }

    @Override
    public ProjectStateInternal getState() {
        applyAllprojects();
        return delegate.getState();
    }

    @Override
    public <T> NamedDomainObjectContainer<T> container(Class<T> type) {
        applyAllprojects();
        return delegate.container(type);
    }

    @Override
    public <T> NamedDomainObjectContainer<T> container(Class<T> type, NamedDomainObjectFactory<T> factory) {
        applyAllprojects();
        return delegate.container(type, factory);
    }

    @Override
    public <T> NamedDomainObjectContainer<T> container(Class<T> type, Closure factoryClosure) {
        applyAllprojects();
        return delegate.container(type, factoryClosure);
    }

    @Override
    public ExtensionContainerInternal getExtensions() {
        applyAllprojects();
        return delegate.getExtensions();
    }

    @Override
    public ResourceHandler getResources() {
        applyAllprojects();
        return delegate.getResources();
    }

    @Override
    public SoftwareComponentContainer getComponents() {
        applyAllprojects();
        return delegate.getComponents();
    }

    @Override
    public void components(Action<? super SoftwareComponentContainer> configuration) {
        applyAllprojects();
        delegate.components(configuration);
    }

    @Override
    public ProjectConfigurationActionContainer getConfigurationActions() {
        return delegate.getConfigurationActions();
    }

    @Override
    public ModelRegistry getModelRegistry() {
        return delegate.getModelRegistry();
    }

    @Override
    public ClassLoaderScope getClassLoaderScope() {
        return delegate.getClassLoaderScope();
    }

    @Override
    public ClassLoaderScope getBaseClassLoaderScope() {
        return delegate.getBaseClassLoaderScope();
    }

    @Override
    public void setScript(Script script) {
        delegate.setScript(script);
    }

    @Override
    public void addDeferredConfiguration(Runnable configuration) {
        delegate.addDeferredConfiguration(configuration);
    }

    @Override
    public void fireDeferredConfiguration() {
        delegate.fireDeferredConfiguration();
    }

    @Override
    public Path identityPath(String name) {
        return delegate.identityPath(name);
    }

    @Override
    public Path projectPath(String name) {
        return delegate.projectPath(name);
    }

    @Nonnull
    @Override
    public Path getProjectPath() {
        return delegate.getProjectPath();
    }

    @Nullable
    @Override
    public ProjectInternal getProject() {
        return this;
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
    public boolean isScript() {
        return delegate.isScript();
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
    public Path getIdentityPath() {
        return delegate.getIdentityPath();
    }

    @Nullable
    @Override
    public ProjectEvaluationListener stepEvaluationListener(ProjectEvaluationListener listener, Action<ProjectEvaluationListener> action) {
        return delegate.stepEvaluationListener(listener, action);
    }

    @Override
    public ProjectState getOwner() {
        return delegate.getOwner();
    }

    @Override
    public InputNormalizationHandlerInternal getNormalization() {
        applyAllprojects();
        return delegate.getNormalization();
    }

    @Override
    public void normalization(Action<? super InputNormalizationHandler> configuration) {
        applyAllprojects();
        delegate.normalization(configuration);
    }

    @Override
    public void dependencyLocking(Action<? super DependencyLockingHandler> configuration) {
        applyAllprojects();
        delegate.dependencyLocking(configuration);
    }

    @Override
    public DependencyLockingHandler getDependencyLocking() {
        applyAllprojects();
        return delegate.getDependencyLocking();
    }

    @Override
    public ScriptHandlerInternal getBuildscript() {
        applyAllprojects();
        return delegate.getBuildscript();
    }

    @Override
    public void buildscript(Closure configureClosure) {
        applyAllprojects();
        delegate.buildscript(configureClosure);
    }

    @Override
    public WorkResult copy(Closure closure) {
        applyAllprojects();
        return delegate.copy(closure);
    }

    @Override
    public WorkResult copy(Action<? super CopySpec> action) {
        applyAllprojects();
        return delegate.copy(action);
    }

    @Override
    public CopySpec copySpec(Closure closure) {
        applyAllprojects();
        return delegate.copySpec(closure);
    }

    @Override
    public CopySpec copySpec(Action<? super CopySpec> action) {
        applyAllprojects();
        return delegate.copySpec(action);
    }

    @Override
    public CopySpec copySpec() {
        applyAllprojects();
        return delegate.copySpec();
    }

    @Override
    public WorkResult sync(Action<? super SyncSpec> action) {
        applyAllprojects();
        return delegate.sync(action);
    }

    @Override
    public DetachedResolver newDetachedResolver() {
        return delegate.newDetachedResolver();
    }

    @Override
    public Property<Object> getInternalStatus() {
        applyAllprojects();
        return delegate.getInternalStatus();
    }

    @Override
    public DependencyMetaDataProvider getDependencyMetaDataProvider() {
        return delegate.getDependencyMetaDataProvider();
    }

    @Override
    public RoleBasedConfigurationContainerInternal getConfigurations() {
        applyAllprojects();
        return delegate.getConfigurations();
    }

    @Override
    public void configurations(Closure configureClosure) {
        applyAllprojects();
        delegate.configurations(configureClosure);
    }

    @Override
    public ArtifactHandler getArtifacts() {
        applyAllprojects();
        return delegate.getArtifacts();
    }

    @Override
    public void artifacts(Closure configureClosure) {
        applyAllprojects();
        delegate.artifacts(configureClosure);
    }

    @Override
    public void artifacts(Action<? super ArtifactHandler> configureAction) {
        applyAllprojects();
        delegate.artifacts(configureAction);
    }

    @SuppressWarnings("deprecation")
    @Override
    public org.gradle.api.plugins.Convention getConvention() {
        applyAllprojects();
        return delegate.getConvention();
    }

    @Override
    public int depthCompare(Project otherProject) {
        return delegate.depthCompare(otherProject);
    }

    @Override
    public int getDepth() {
        return delegate.getDepth();
    }

    @Override
    public int compareTo(@Nonnull Project o) {
        return delegate.compareTo(o);
    }

    @Override
    public FileOperations getFileOperations() {
        return delegate.getFileOperations();
    }

    @Override
    public ProcessOperations getProcessOperations() {
        return delegate.getProcessOperations();
    }

    @Override
    public PluginContainer getPlugins() {
        applyAllprojects();
        return delegate.getPlugins();
    }

    @Override
    public void apply(Closure closure) {
        applyAllprojects();
        delegate.apply(closure);
    }

    @Override
    public void apply(Action<? super ObjectConfigurationAction> action) {
        applyAllprojects();
        delegate.apply(action);
    }

    @Override
    public void apply(Map<String, ?> options) {
        applyAllprojects();
        delegate.apply(options);
    }

    @Override
    public PluginManagerInternal getPluginManager() {
        applyAllprojects();
        return delegate.getPluginManager();
    }

    @Override
    public ConfigurationTargetIdentifier getConfigurationTargetIdentifier() {
        return delegate.getConfigurationTargetIdentifier();
    }
}
