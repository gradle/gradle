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

import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.ProcessOperations;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.plugins.ExtensionContainerInternal;
import org.gradle.api.internal.plugins.PluginAwareInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.configuration.project.ProjectConfigurationActionContainer;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.logging.StandardOutputCapture;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.registry.ModelRegistryScope;
import org.gradle.util.Path;

public interface ProjectInternal extends Project, ProjectIdentifier, FileOperations, ProcessOperations, DomainObjectContext, DependencyMetaDataProvider, ModelRegistryScope, PluginAwareInternal {

    // These constants are defined here and not with the rest of their kind in HelpTasksPlugin because they are referenced
    // in the ‘core’ and ‘ui’ modules, which don't depend on ‘plugins’ where HelpTasksPlugin is defined.
    String HELP_TASK = "help";
    String TASKS_TASK = "tasks";
    String PROJECTS_TASK = "projects";

    ProjectInternal getParent();

    ProjectInternal getRootProject();

    Project evaluate();

    ProjectInternal bindAllModelRules();

    TaskContainerInternal getTasks();

    ScriptSource getBuildScriptSource();

    void addChildProject(ProjectInternal childProject);

    ProjectInternal project(String path) throws UnknownProjectException;

    ProjectInternal findProject(String path);

    ProjectRegistry<ProjectInternal> getProjectRegistry();

    DynamicObject getInheritedScope();

    GradleInternal getGradle();

    ProjectEvaluationListener getProjectEvaluationBroadcaster();

    FileResolver getFileResolver();

    ServiceRegistry getServices();

    ServiceRegistryFactory getServiceRegistryFactory();

    StandardOutputCapture getStandardOutputCapture();

    ProjectStateInternal getState();

    ExtensionContainerInternal getExtensions();

    ProjectConfigurationActionContainer getConfigurationActions();

    ModelRegistry getModelRegistry();

    ClassLoaderScope getClassLoaderScope();

    ClassLoaderScope getBaseClassLoaderScope();

    void setScript(groovy.lang.Script script);

    void addDeferredConfiguration(Runnable configuration);

    void fireDeferredConfiguration();

    /**
     * Returns a unique path for this project within its containing build.
     */
    Path getProjectPath();

    /**
     * Returns a unique path for this project within the current Gradle invocation.
     */
    Path getIdentityPath();


}
