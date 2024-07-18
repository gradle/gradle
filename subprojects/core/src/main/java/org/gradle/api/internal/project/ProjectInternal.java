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

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.HasScriptServices;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.internal.plugins.ExtensionContainerInternal;
import org.gradle.api.internal.plugins.PluginAwareInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.provider.Property;
import org.gradle.configuration.project.ProjectConfigurationActionContainer;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.logging.StandardOutputCapture;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.model.RuleBasedPluginListener;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.registry.ModelRegistryScope;
import org.gradle.normalization.internal.InputNormalizationHandlerInternal;
import org.gradle.util.Path;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

@UsedByScanPlugin("scan, test-retry")
public interface ProjectInternal extends Project, ProjectIdentifier, HasScriptServices, DomainObjectContext, ModelRegistryScope, PluginAwareInternal {

    // These constants are defined here and not with the rest of their kind in HelpTasksPlugin because they are referenced
    // in the ‘core’ modules, which don't depend on ‘plugins’ where HelpTasksPlugin is defined.
    String HELP_TASK = "help";
    String TASKS_TASK = "tasks";
    String PROJECTS_TASK = "projects";

    Attribute<String> STATUS_ATTRIBUTE = Attribute.of("org.gradle.status", String.class);

    @Nullable
    @Override
    ProjectInternal getParent();

    @Nullable
    ProjectInternal getParent(ProjectInternal referrer);

    @Override
    ProjectInternal getRootProject();

    ProjectInternal getRootProject(ProjectInternal referrer);

    Project evaluate();

    ProjectInternal bindAllModelRules();

    @Override
    TaskContainerInternal getTasks();

    ScriptSource getBuildScriptSource();

    @Override
    ProjectInternal project(String path) throws UnknownProjectException;

    ProjectInternal project(ProjectInternal referrer, String path) throws UnknownProjectException;

    ProjectInternal project(ProjectInternal referrer, String path, Action<? super Project> configureAction);

    @Override
    @Nullable
    ProjectInternal findProject(String path);

    @Nullable
    ProjectInternal findProject(ProjectInternal referrer, String path);

    Set<? extends ProjectInternal> getSubprojects(ProjectInternal referrer);

    void subprojects(ProjectInternal referrer, Action<? super Project> configureAction);

    /**
     * Do not use this method to access the child projects in the Gradle codebase!
     * The implementations may add checks that enforce correct usage of the public API, such as
     * cross-project model access checks, which are meant to report warnings on incorrect API usages
     * from third-party code. The internal usages won't pass these checks and will break.
     *
     * @see ProjectInternal#getChildProjectsUnchecked()
     * @see ProjectHierarchyUtils#getChildProjectsForInternalUse(Project)
     */
    @Override
    Map<String, Project> getChildProjects();

    Map<String, Project> getChildProjects(ProjectInternal referrer);

    /**
     * Returns a mapping of the direct child project names to the child project instances.
     *
     * Compared to {@link Project#getChildProjects()}, this method does not add any checks
     * to the returned projects:
     *
     * <ul>
     *     <li> With project isolation enabled, it does not add checks for cross-project model
     *     access to the returned project instances. The returned project models can be accessed
     *     without any limitations.
     * </ul>
     *
     * This method is suitable for internal usages in the Gradle codebase.
     * @return A map where the keys are the project names and the values are the child projects
     */
    Map<String, Project> getChildProjectsUnchecked();

    Set<? extends ProjectInternal> getAllprojects(ProjectInternal referrer);

    void allprojects(ProjectInternal referrer, Action<? super Project> configureAction);

    DynamicObject getInheritedScope();

    @Override
    @UsedByScanPlugin("test-retry")
    GradleInternal getGradle();

    ProjectEvaluationListener getProjectEvaluationBroadcaster();

    void addRuleBasedPluginListener(RuleBasedPluginListener listener);

    void prepareForRuleBasedPlugins();

    FileResolver getFileResolver();

    TaskDependencyFactory getTaskDependencyFactory();

    @UsedByScanPlugin("scan, test-retry")
    ServiceRegistry getServices();

    ServiceRegistryFactory getServiceRegistryFactory();

    StandardOutputCapture getStandardOutputCapture();

    @Override
    ProjectStateInternal getState();

    @Override
    ExtensionContainerInternal getExtensions();

    ProjectConfigurationActionContainer getConfigurationActions();

    @Override
    ModelRegistry getModelRegistry();

    ClassLoaderScope getClassLoaderScope();

    ClassLoaderScope getBaseClassLoaderScope();

    void setScript(groovy.lang.Script script);

    void addDeferredConfiguration(Runnable configuration);

    void fireDeferredConfiguration();

    @Override
    @Nonnull
    ProjectIdentity getProjectIdentity();

    /**
     * Returns a unique path for this project within its containing build.
     */
    Path getProjectPath();

    /**
     * Returns a unique path for this project within the current build tree.
     */
    Path getIdentityPath();

    /**
     * Executes the given action against the given listener collecting any new listener registrations in a separate
     * {@link ProjectEvaluationListener} instance which is returned at the end if not empty.
     *
     * @param listener the current listener
     * @param action the listener action
     * @return null if no listeners were added during evaluation or the {@link ProjectEvaluationListener} instance representing the new batch of registered listeners
     */
    @Nullable
    ProjectEvaluationListener stepEvaluationListener(ProjectEvaluationListener listener, Action<ProjectEvaluationListener> action);

    /**
     * Returns the {@link ProjectState} that manages the state of this instance.
     */
    ProjectState getOwner();

    @Override
    InputNormalizationHandlerInternal getNormalization();

    @Override
    ScriptHandlerInternal getBuildscript();

    /**
     * Returns a dependency resolver which can be used to resolve
     * dependencies in isolation from the project itself. This is
     * particularly useful if the repositories or configurations
     * needed for resolution shouldn't leak to the project state.
     *
     * @return a detached resolver
     */
    DetachedResolver newDetachedResolver();

    /**
     * Returns the property that stored {@link Project#getStatus()}.
     * <p>
     * By exposing this property, the {@code base} plugin can override the default value without overriding the build configuration.
     * <p>
     * See: https://github.com/gradle/gradle/issues/16946
     */
    Property<Object> getInternalStatus();

    /**
     * When we get the {@link ConfigurationContainer} from internal locations, we'll override
     * this getter to promise to return a {@link RoleBasedConfigurationContainerInternal} instance, to avoid
     * the need to cast the result to create role-based configurations.
     *
     * @return the configuration container as a {@link RoleBasedConfigurationContainerInternal}
     */
    @Override
    RoleBasedConfigurationContainerInternal getConfigurations();

    interface DetachedResolver {
        RepositoryHandler getRepositories();

        DependencyHandler getDependencies();

        ConfigurationContainer getConfigurations();
    }
}
