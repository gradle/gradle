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
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;
import org.gradle.api.Action;
import org.gradle.api.AntBuilder;
import org.gradle.api.InvalidUserDataException;
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
import org.gradle.api.build.ProjectBuild;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DeleteSpec;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.SyncSpec;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.ProcessOperations;
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
import org.gradle.internal.Cast;
import org.gradle.internal.accesscontrol.AllowUsingApiForExternalUse;
import org.gradle.internal.logging.StandardOutputCapture;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.model.ModelContainer;
import org.gradle.internal.model.RuleBasedPluginListener;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.normalization.InputNormalizationHandler;
import org.gradle.normalization.internal.InputNormalizationHandlerInternal;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.gradle.process.JavaExecSpec;
import org.gradle.util.Path;
import org.gradle.util.internal.ConfigureUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * Wrapper for {@link ProjectInternal}, that declares some API methods as access to a mutable state of the project.
 * <p>
 * This class enables dynamic property and method dispatch on the `this` bean rather than on the {@link #delegate}.
 * If the dispatch on `this` fails, the control flow is delegated to {@link #propertyMissing(String)}, {@link #propertyMissing(String, Object)},
 * {@link #methodMissing(String, Object)} and {@link #hasPropertyMissing(String)} methods.
 * <p>
 * Instances of this class should be created via {@link org.gradle.internal.reflect.Instantiator} to ensure proper runtime decoration.
 */
public abstract class MutableStateAccessAwareProject implements ProjectInternal, DynamicObjectAware {

    public static <T extends MutableStateAccessAwareProject> ProjectInternal wrap(
        ProjectInternal target,
        ProjectInternal referrer,
        Function<ProjectInternal, T> wrapper
    ) {
        return target == referrer
            ? target
            : wrapper.apply(target);
    }

    protected final ProjectInternal delegate;
    protected final ProjectInternal referrer;
    private final DynamicObject dynamicObject;

    protected MutableStateAccessAwareProject(ProjectInternal delegate, ProjectInternal referrer) {
        this.delegate = delegate;
        this.referrer = referrer;
        this.dynamicObject = new HasPropertyMissingDynamicObject(this, Project.class, this::hasPropertyMissing);
    }

    protected abstract void onMutableStateAccess(String what);

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    @Nullable
    @SuppressWarnings("unused") // used by Groovy dynamic dispatch
    protected abstract Object propertyMissing(String name);

    @Nullable
    @SuppressWarnings("unused") // used by Groovy dynamic dispatch
    protected abstract Object methodMissing(String name, Object args);

    // used by Groovy dynamic dispatch
    protected void propertyMissing(String name, Object args) {
        onMutableStateAccess("setProperty");
        delegate.setProperty(name, args);
    }

    // used by Groovy dynamic dispatch
    protected boolean hasPropertyMissing(String name) {
        onMutableStateAccess("hasProperty");
        return delegate.hasProperty(name);
    }

    @Override
    public DynamicObject getAsDynamicObject() {
        return dynamicObject;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    @Override
    public IsolatedProject getIsolated() {
        return delegate.getIsolated();
    }

    @Nullable
    @Override
    public String getDescription() {
        onMutableStateAccess("description");
        return delegate.getDescription();
    }

    @Override
    public void setDescription(@Nullable String description) {
        onMutableStateAccess("description");
        delegate.setDescription(description);
    }

    @Override
    public Object getGroup() {
        onMutableStateAccess("group");
        return delegate.getGroup();
    }

    @Override
    public void setGroup(Object group) {
        onMutableStateAccess("group");
        delegate.setGroup(group);
    }

    @Override
    public Object getVersion() {
        onMutableStateAccess("version");
        return delegate.getVersion();
    }

    @Override
    public void setVersion(Object version) {
        onMutableStateAccess("version");
        delegate.setVersion(version);
    }

    @Override
    public Object getStatus() {
        onMutableStateAccess("status");
        return delegate.getStatus();
    }

    @Override
    public void setStatus(Object status) {
        onMutableStateAccess("status");
        delegate.setStatus(status);
    }

    @Nullable
    @Override
    public ProjectInternal getParent() {
        return delegate.getParent(referrer);
    }

    @Nullable
    @Override
    public ProjectInternal getParent(ProjectInternal referrer) {
        return delegate.getParent(referrer);
    }

    @Override
    public File getRootDir() {
        return delegate.getRootDir();
    }

    @Override
    @Nonnull
    public ProjectIdentity getProjectIdentity() {
        return delegate.getProjectIdentity();
    }

    /**
     * @deprecated Use layout.buildDirectory instead
     */
    @Override
    @Deprecated
    public File getBuildDir() {
        onMutableStateAccess("buildDir");
        return delegate.getBuildDir();
    }

    /**
     * @deprecated Use layout.buildDirectory instead
     */
    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
    public void setBuildDir(File path) {
        onMutableStateAccess("buildDir");
        delegate.setBuildDir(path);
    }

    /**
     * @deprecated Use layout.buildDirectory instead
     */
    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
    public void setBuildDir(Object path) {
        onMutableStateAccess("buildDir");
        delegate.setBuildDir(path);
    }

    @Override
    public File getProjectDir() {
        return delegate.getProjectDir();
    }

    @Override
    public File getBuildFile() {
        return delegate.getBuildFile();
    }

    @Override
    public ProjectInternal getRootProject() {
        return delegate.getRootProject(referrer);
    }

    @Override
    public ProjectInternal getRootProject(ProjectInternal referrer) {
        return delegate.getRootProject(referrer);
    }

    @Override
    public Project evaluate() {
        onMutableStateAccess("evaluate");
        return delegate.evaluate();
    }

    @Override
    public ProjectInternal bindAllModelRules() {
        onMutableStateAccess("bindAllModelRules");
        return delegate.bindAllModelRules();
    }

    @Override
    public TaskContainerInternal getTasks() {
        onMutableStateAccess("tasks");
        return delegate.getTasks();
    }

    @Override
    public ProjectInternal getProject() {
        return this;
    }

    @Override
    public void subprojects(ProjectInternal referrer, Action<? super Project> configureAction) {
        delegate.subprojects(referrer, configureAction);
    }

    @Override
    public void subprojects(Action<? super Project> action) {
        delegate.subprojects(referrer, action);
    }

    @Override
    public void subprojects(Closure configureClosure) {
        delegate.subprojects(referrer, ConfigureUtil.configureUsing(configureClosure));
    }

    @Override
    public void allprojects(Action<? super Project> action) {
        delegate.allprojects(referrer, action);
    }

    @Override
    public void allprojects(Closure configureClosure) {
        delegate.allprojects(referrer, ConfigureUtil.configureUsing(configureClosure));
    }

    @Override
    public void allprojects(ProjectInternal referrer, Action<? super Project> configureAction) {
        delegate.allprojects(referrer, configureAction);
    }

    @Override
    public Project project(String path, Closure configureClosure) {
        return delegate.project(referrer, path, ConfigureUtil.configureUsing(configureClosure));
    }

    @Override
    public ProjectInternal project(String path) throws UnknownProjectException {
        return delegate.project(referrer, path);
    }

    @Override
    public Project project(String path, Action<? super Project> configureAction) {
        return delegate.project(referrer, path, configureAction);
    }

    @Override
    public ProjectInternal project(ProjectInternal referrer, String path, Action<? super Project> configureAction) {
        return delegate.project(referrer, path, configureAction);
    }

    @Override
    public ProjectInternal project(ProjectInternal referrer, String path) throws UnknownProjectException {
        return delegate.project(referrer, path);
    }

    @Nullable
    @Override
    public ProjectInternal findProject(String path) {
        return delegate.findProject(referrer, path);
    }

    @Override
    public Set<? extends ProjectInternal> getSubprojects(ProjectInternal referrer) {
        return delegate.getSubprojects(referrer);
    }

    @Override
    @AllowUsingApiForExternalUse
    public Map<String, Project> getChildProjects() {
        return getChildProjects(referrer);
    }

    @Override
    public Map<String, Project> getChildProjects(ProjectInternal referrer) {
        return delegate.getChildProjects(referrer);
    }

    @Override
    public Set<Project> getAllprojects() {
        return Cast.uncheckedCast(delegate.getAllprojects(referrer));
    }

    @Override
    public Set<? extends ProjectInternal> getAllprojects(ProjectInternal referrer) {
        return delegate.getAllprojects(referrer);
    }

    @Override
    public Set<Project> getSubprojects() {
        return Cast.uncheckedCast(delegate.getSubprojects(referrer));
    }

    @Override
    public Map<String, Project> getChildProjectsUnchecked() {
        return delegate.getChildProjectsUnchecked();
    }

    @Nullable
    @Override
    public ProjectInternal findProject(ProjectInternal referrer, String path) {
        return delegate.findProject(referrer, path);
    }

    @Override
    public void beforeEvaluate(Action<? super Project> action) {
        onMutableStateAccess("beforeEvaluate");
        delegate.beforeEvaluate(action);
    }

    @Override
    public void beforeEvaluate(Closure closure) {
        onMutableStateAccess("beforeEvaluate");
        delegate.beforeEvaluate(closure);
    }

    @Override
    public void afterEvaluate(Action<? super Project> action) {
        onMutableStateAccess("afterEvaluate");
        delegate.afterEvaluate(action);
    }

    @Override
    public void afterEvaluate(Closure closure) {
        onMutableStateAccess("afterEvaluate");
        delegate.afterEvaluate(closure);
    }

    @Override
    public boolean hasProperty(String propertyName) {
        onMutableStateAccess("hasProperty");
        return delegate.hasProperty(propertyName);
    }

    @Override
    public Map<String, ?> getProperties() {
        onMutableStateAccess("properties");
        return delegate.getProperties();
    }

    @Nullable
    @Override
    public Object property(String propertyName) throws MissingPropertyException {
        onMutableStateAccess("property");
        return delegate.property(propertyName);
    }

    @Nullable
    @Override
    public Object findProperty(String propertyName) {
        onMutableStateAccess("findProperty");
        return delegate.findProperty(propertyName);
    }

    @Override
    public void setProperty(String name, @Nullable Object value) throws MissingPropertyException {
        onMutableStateAccess("setProperty");
        delegate.setProperty(name, value);
    }

    @Override
    public Logger getLogger() {
        return delegate.getLogger();
    }

    @Override
    public ScriptSource getBuildScriptSource() {
        return delegate.getBuildScriptSource();
    }

    @Override
    public Map<Project, Set<Task>> getAllTasks(boolean recursive) {
        onMutableStateAccess("allTasks");
        return delegate.getAllTasks(recursive);
    }

    @Override
    public Set<Task> getTasksByName(String name, boolean recursive) {
        onMutableStateAccess("tasksByName");
        return delegate.getTasksByName(name, recursive);
    }

    @Override
    public Task task(String name) throws InvalidUserDataException {
        onMutableStateAccess("task");
        return delegate.task(name);
    }

    @Override
    public Task task(Map<String, ?> args, String name) throws InvalidUserDataException {
        onMutableStateAccess("task");
        return delegate.task(args, name);
    }

    @Override
    public Task task(Map<String, ?> args, String name, Closure configureClosure) {
        onMutableStateAccess("task");
        return delegate.task(args, name, configureClosure);
    }

    @Override
    public Task task(String name, Closure configureClosure) {
        onMutableStateAccess("task");
        return delegate.task(name, configureClosure);
    }

    @Override
    public Task task(String name, Action<? super Task> configureAction) {
        onMutableStateAccess("task");
        return delegate.task(name, configureAction);
    }

    @Override
    public URI uri(Object path) {
        return delegate.uri(path);
    }

    @Override
    public File mkdir(Object path) {
        return delegate.mkdir(path);
    }

    @Override
    public File file(Object path) {
        return delegate.file(path);
    }

    @Override
    public File file(Object path, PathValidation validation) throws InvalidUserDataException {
        return delegate.file(path, validation);
    }

    @Override
    public ConfigurableFileCollection files(Object... paths) {
        return delegate.files(paths);
    }

    @Override
    public ConfigurableFileCollection files(Object paths, Closure configureClosure) {
        return delegate.files(paths, configureClosure);
    }

    @Override
    public ConfigurableFileCollection files(Object paths, Action<? super ConfigurableFileCollection> configureAction) {
        return delegate.files(paths, configureAction);
    }

    @Override
    public ConfigurableFileTree fileTree(Object baseDir) {
        return delegate.fileTree(baseDir);
    }

    @Override
    public ConfigurableFileTree fileTree(Object baseDir, Closure configureClosure) {
        return delegate.fileTree(baseDir, configureClosure);
    }

    @Override
    public ConfigurableFileTree fileTree(Object baseDir, Action<? super ConfigurableFileTree> configureAction) {
        return delegate.fileTree(baseDir, configureAction);
    }

    @Override
    public ConfigurableFileTree fileTree(Map<String, ?> args) {
        return delegate.fileTree(args);
    }

    @Override
    public FileTree zipTree(Object zipPath) {
        return delegate.zipTree(zipPath);
    }

    @Override
    public FileTree tarTree(Object tarPath) {
        return delegate.tarTree(tarPath);
    }

    @Override
    public boolean delete(Object... paths) {
        return delegate.delete(paths);
    }

    @Override
    public WorkResult delete(Action<? super DeleteSpec> action) {
        return delegate.delete(action);
    }

    @Override
    public <T> Provider<T> provider(Callable<? extends T> value) {
        return delegate.provider(value);
    }

    @Override
    public ProviderFactory getProviders() {
        return delegate.getProviders();
    }

    @Override
    public ObjectFactory getObjects() {
        return delegate.getObjects();
    }

    @Override
    public ProjectLayout getLayout() {
        onMutableStateAccess("layout");
        return delegate.getLayout();
    }

    @Override
    public ExecResult javaexec(Closure closure) {
        return delegate.javaexec(closure);
    }

    @Override
    public ExecResult javaexec(Action<? super JavaExecSpec> action) {
        return delegate.javaexec(action);
    }

    @Override
    public ExecResult exec(Closure closure) {
        return delegate.exec(closure);
    }

    @Override
    public ExecResult exec(Action<? super ExecSpec> action) {
        return delegate.exec(action);
    }

    @Override
    public String relativePath(Object path) {
        return delegate.relativePath(path);
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
    public String getPath() {
        return delegate.getPath();
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
    public Path getProjectPath() {
        return delegate.getProjectPath();
    }

    @Override
    public AntBuilder getAnt() {
        onMutableStateAccess("ant");
        return delegate.getAnt();
    }

    @Override
    public AntBuilder createAntBuilder() {
        onMutableStateAccess("antBuilder");
        return delegate.createAntBuilder();
    }

    @Override
    public AntBuilder ant(Closure configureClosure) {
        onMutableStateAccess("ant");
        return delegate.ant(configureClosure);
    }

    @Override
    public AntBuilder ant(Action<? super AntBuilder> configureAction) {
        onMutableStateAccess("ant");
        return delegate.ant(configureAction);
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
        onMutableStateAccess("defaultTasks");
        return delegate.getDefaultTasks();
    }

    @Override
    public void setDefaultTasks(List<String> defaultTasks) {
        onMutableStateAccess("defaultTasks");
        delegate.setDefaultTasks(defaultTasks);
    }

    @Override
    public void defaultTasks(String... defaultTasks) {
        onMutableStateAccess("defaultTasks");
        delegate.defaultTasks(defaultTasks);
    }

    @Override
    public Project evaluationDependsOn(String path) throws UnknownProjectException {
        onMutableStateAccess("evaluationDependsOn");
        return delegate.evaluationDependsOn(path);
    }

    @Override
    public void evaluationDependsOnChildren() {
        onMutableStateAccess("evaluationDependsOnChildren");
        delegate.evaluationDependsOnChildren();
    }

    @Override
    public DynamicObject getInheritedScope() {
        return delegate.getInheritedScope();
    }

    @Override
    public GradleInternal getGradle() {
        return delegate.getGradle();
    }

    @Override
    public ProjectBuild getEnclosingBuild() {
        return delegate.getEnclosingBuild();
    }

    @Override
    public LoggingManager getLogging() {
        return delegate.getLogging();
    }

    @Override
    public Object configure(Object object, Closure configureClosure) {
        return delegate.configure(object, configureClosure);
    }

    @Override
    public Iterable<?> configure(Iterable<?> objects, Closure configureClosure) {
        return delegate.configure(objects, configureClosure);
    }

    @Override
    public <T> Iterable<T> configure(Iterable<T> objects, Action<? super T> configureAction) {
        return delegate.configure(objects, configureAction);
    }

    @Override
    public RepositoryHandler getRepositories() {
        onMutableStateAccess("repositories");
        return delegate.getRepositories();
    }

    @Override
    public void repositories(Closure configureClosure) {
        onMutableStateAccess("repositories");
        delegate.repositories(configureClosure);
    }

    @Override
    public DependencyHandler getDependencies() {
        onMutableStateAccess("dependencies");
        return delegate.getDependencies();
    }

    @Override
    public void dependencies(Closure configureClosure) {
        onMutableStateAccess("dependencies");
        delegate.dependencies(configureClosure);
    }

    @Override
    public DependencyFactory getDependencyFactory() {
        return delegate.getDependencyFactory();
    }

    @Override
    public TaskDependencyFactory getTaskDependencyFactory() {
        return delegate.getTaskDependencyFactory();
    }

    @Override
    public ProjectEvaluationListener getProjectEvaluationBroadcaster() {
        return delegate.getProjectEvaluationBroadcaster();
    }

    @Override
    public void addRuleBasedPluginListener(RuleBasedPluginListener listener) {
        onMutableStateAccess("ruleBasedPluginListener");
        delegate.addRuleBasedPluginListener(listener);
    }

    @Override
    public void prepareForRuleBasedPlugins() {
        onMutableStateAccess("ruleBasedPlugins");
        delegate.prepareForRuleBasedPlugins();
    }

    @Override
    public FileResolver getFileResolver() {
        return delegate.getFileResolver();
    }

    @Override
    public ServiceRegistry getServices() {
        onMutableStateAccess("services");
        return delegate.getServices();
    }

    @Override
    public ServiceRegistryFactory getServiceRegistryFactory() {
        onMutableStateAccess("serviceRegistryFactory");
        return delegate.getServiceRegistryFactory();
    }

    @Override
    public StandardOutputCapture getStandardOutputCapture() {
        return delegate.getStandardOutputCapture();
    }

    @Override
    public ProjectStateInternal getState() {
        onMutableStateAccess("state");
        return delegate.getState();
    }

    @Override
    public <T> NamedDomainObjectContainer<T> container(Class<T> type) {
        return delegate.container(type);
    }

    @Override
    public <T> NamedDomainObjectContainer<T> container(Class<T> type, NamedDomainObjectFactory<T> factory) {
        return delegate.container(type, factory);
    }

    @Override
    public <T> NamedDomainObjectContainer<T> container(Class<T> type, Closure factoryClosure) {
        return delegate.container(type, factoryClosure);
    }

    @Override
    public ExtensionContainerInternal getExtensions() {
        onMutableStateAccess("extensions");
        return delegate.getExtensions();
    }

    @Override
    public ResourceHandler getResources() {
        return delegate.getResources();
    }

    @Override
    public SoftwareComponentContainer getComponents() {
        onMutableStateAccess("components");
        return delegate.getComponents();
    }

    @Override
    public void components(Action<? super SoftwareComponentContainer> configuration) {
        onMutableStateAccess("components");
        delegate.components(configuration);
    }

    @Override
    public ProjectConfigurationActionContainer getConfigurationActions() {
        onMutableStateAccess("configurationActions");
        return delegate.getConfigurationActions();
    }

    @Override
    public ModelRegistry getModelRegistry() {
        onMutableStateAccess("modelRegistry");
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
        onMutableStateAccess("script");
        delegate.setScript(script);
    }

    @Override
    public boolean isScript() {
        return delegate.isScript();
    }

    @Override
    public void addDeferredConfiguration(Runnable configuration) {
        onMutableStateAccess("deferredConfiguration");
        delegate.addDeferredConfiguration(configuration);
    }

    @Override
    public void fireDeferredConfiguration() {
        onMutableStateAccess("deferredConfiguration");
        delegate.fireDeferredConfiguration();
    }

    @Override
    public ModelContainer<?> getModel() {
        onMutableStateAccess("model");
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
        // TODO: this is a mutable state
        return delegate.getOwner();
    }

    @Override
    public InputNormalizationHandlerInternal getNormalization() {
        onMutableStateAccess("normalization");
        return delegate.getNormalization();
    }

    @Override
    public void normalization(Action<? super InputNormalizationHandler> configuration) {
        onMutableStateAccess("normalization");
        delegate.normalization(configuration);
    }

    @Override
    public void dependencyLocking(Action<? super DependencyLockingHandler> configuration) {
        onMutableStateAccess("dependencyLocking");
        delegate.dependencyLocking(configuration);
    }

    @Override
    public DependencyLockingHandler getDependencyLocking() {
        onMutableStateAccess("dependencyLocking");
        return delegate.getDependencyLocking();
    }

    @Override
    public ScriptHandlerInternal getBuildscript() {
        onMutableStateAccess("buildscript");
        return delegate.getBuildscript();
    }

    @Override
    public void buildscript(Closure configureClosure) {
        onMutableStateAccess("buildscript");
        delegate.buildscript(configureClosure);
    }

    @Override
    public WorkResult copy(Closure closure) {
        return delegate.copy(closure);
    }

    @Override
    public WorkResult copy(Action<? super CopySpec> action) {
        return delegate.copy(action);
    }

    @Override
    public CopySpec copySpec(Closure closure) {
        return delegate.copySpec(closure);
    }

    @Override
    public CopySpec copySpec(Action<? super CopySpec> action) {
        return delegate.copySpec(action);
    }

    @Override
    public CopySpec copySpec() {
        return delegate.copySpec();
    }

    @Override
    public WorkResult sync(Action<? super SyncSpec> action) {
        return delegate.sync(action);
    }

    @Override
    public DetachedResolver newDetachedResolver() {
        return delegate.newDetachedResolver();
    }

    @Override
    public Property<Object> getInternalStatus() {
        onMutableStateAccess("internalStatus");
        return delegate.getInternalStatus();
    }

    @Override
    public RoleBasedConfigurationContainerInternal getConfigurations() {
        onMutableStateAccess("configurations");
        return delegate.getConfigurations();
    }

    @Override
    public void configurations(Closure configureClosure) {
        onMutableStateAccess("configurations");
        delegate.configurations(configureClosure);
    }

    @Override
    public ArtifactHandler getArtifacts() {
        onMutableStateAccess("artifacts");
        return delegate.getArtifacts();
    }

    @Override
    public void artifacts(Closure configureClosure) {
        onMutableStateAccess("artifacts");
        delegate.artifacts(configureClosure);
    }

    @Override
    public void artifacts(Action<? super ArtifactHandler> configureAction) {
        onMutableStateAccess("artifacts");
        delegate.artifacts(configureAction);
    }

    /**
     * @deprecated the concept of conventions is deprecated. Use extensions instead
     */
    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
    public org.gradle.api.plugins.Convention getConvention() {
        onMutableStateAccess("convention");
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
    public int compareTo(Project o) {
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
        onMutableStateAccess("plugins");
        return delegate.getPlugins();
    }

    @Override
    public PluginManagerInternal getPluginManager() {
        onMutableStateAccess("pluginManager");
        return delegate.getPluginManager();
    }

    @Override
    public void apply(Closure closure) {
        onMutableStateAccess("apply");
        delegate.apply(closure);
    }

    @Override
    public void apply(Action<? super ObjectConfigurationAction> action) {
        onMutableStateAccess("apply");
        delegate.apply(action);
    }

    @Override
    public void apply(Map<String, ?> options) {
        onMutableStateAccess("apply");
        delegate.apply(options);
    }

    @Override
    public ConfigurationTargetIdentifier getConfigurationTargetIdentifier() {
        return delegate.getConfigurationTargetIdentifier();
    }
}
