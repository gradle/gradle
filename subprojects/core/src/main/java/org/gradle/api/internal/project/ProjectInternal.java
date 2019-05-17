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
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.HasScriptServices;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.internal.plugins.ExtensionContainerInternal;
import org.gradle.api.internal.plugins.PluginAwareInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
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
import org.gradle.util.Path;

import javax.annotation.Nullable;

@UsedByScanPlugin
public interface ProjectInternal extends Project, ProjectIdentifier, HasScriptServices, DomainObjectContext, DependencyMetaDataProvider, ModelRegistryScope, PluginAwareInternal {

    // These constants are defined here and not with the rest of their kind in HelpTasksPlugin because they are referenced
    // in the ‘core’ modules, which don't depend on ‘plugins’ where HelpTasksPlugin is defined.
    String HELP_TASK = "help";
    String TASKS_TASK = "tasks";
    String PROJECTS_TASK = "projects";

    Attribute<String> STATUS_ATTRIBUTE = Attribute.of("org.gradle.status", String.class);

    @Override
    ProjectInternal getParent();

    @Override
    ProjectInternal getRootProject();

    Project evaluate();

    ProjectInternal bindAllModelRules();

    @Override
    TaskContainerInternal getTasks();

    ScriptSource getBuildScriptSource();

    void addChildProject(ProjectInternal childProject);

    @Override
    ProjectInternal project(String path) throws UnknownProjectException;

    @Override
    ProjectInternal findProject(String path);

    ProjectRegistry<ProjectInternal> getProjectRegistry();

    DynamicObject getInheritedScope();

    @Override
    GradleInternal getGradle();

    ProjectEvaluationListener getProjectEvaluationBroadcaster();

    void addRuleBasedPluginListener(RuleBasedPluginListener listener);

    void prepareForRuleBasedPlugins();

    FileResolver getFileResolver();

    @UsedByScanPlugin
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

    /**
     * Returns a unique path for this project within its containing build.
     */
    @Override
    Path getProjectPath();

    /**
     * Returns a unique path for this project within the current Gradle invocation.
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

    ProjectState getMutationState();

    @Override
    ScriptHandlerInternal getBuildscript();
}
