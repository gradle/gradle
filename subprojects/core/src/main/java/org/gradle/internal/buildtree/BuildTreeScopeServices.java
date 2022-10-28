/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.api.internal.project.DefaultProjectStateRegistry;
import org.gradle.api.internal.provider.DefaultConfigurationTimeBarrier;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.execution.DefaultTaskSelector;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.execution.TaskNameResolver;
import org.gradle.execution.TaskPathProjectEvaluator;
import org.gradle.execution.TaskSelector;
import org.gradle.execution.selection.DefaultBuildTaskSelector;
import org.gradle.initialization.BuildOptionBuildOperationProgressEventsEmitter;
import org.gradle.initialization.exception.DefaultExceptionAnalyser;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.initialization.exception.MultipleBuildFailuresExceptionAnalyser;
import org.gradle.initialization.exception.StackTraceSanitizingExceptionAnalyser;
import org.gradle.internal.build.DefaultBuildLifecycleControllerFactory;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager;
import org.gradle.internal.event.DefaultListenerManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.internal.service.scopes.Scopes;

import java.util.List;

/**
 * Contains the singleton services for a single build tree which consists of one or more builds.
 */
public class BuildTreeScopeServices {
    private final BuildTreeState buildTree;
    private final BuildTreeModelControllerServices.Supplier modelServices;

    public BuildTreeScopeServices(BuildTreeState buildTree, BuildTreeModelControllerServices.Supplier modelServices) {
        this.buildTree = buildTree;
        this.modelServices = modelServices;
    }

    protected void configure(ServiceRegistration registration, List<PluginServiceRegistry> pluginServiceRegistries) {
        for (PluginServiceRegistry pluginServiceRegistry : pluginServiceRegistries) {
            pluginServiceRegistry.registerBuildTreeServices(registration);
        }
        registration.add(BuildTreeState.class, buildTree);
        registration.add(GradleEnterprisePluginManager.class);
        registration.add(DefaultBuildLifecycleControllerFactory.class);
        registration.add(BuildOptionBuildOperationProgressEventsEmitter.class);
        registration.add(BuildInclusionCoordinator.class);
        registration.add(DefaultBuildTaskSelector.class);
        registration.add(DefaultProjectStateRegistry.class);
        registration.add(DefaultConfigurationTimeBarrier.class);
        registration.add(DeprecationsReporter.class);
        registration.add(TaskPathProjectEvaluator.class);
        modelServices.applyServicesTo(registration);
    }

    protected TaskSelector createTaskSelector(ProjectConfigurer projectConfigurer) {
        return new DefaultTaskSelector(new TaskNameResolver(), projectConfigurer);
    }

    protected DefaultListenerManager createListenerManager(DefaultListenerManager parent) {
        return parent.createChild(Scopes.BuildTree.class);
    }

    protected ExceptionAnalyser createExceptionAnalyser(ListenerManager listenerManager, LoggingConfiguration loggingConfiguration) {
        ExceptionAnalyser exceptionAnalyser = new MultipleBuildFailuresExceptionAnalyser(new DefaultExceptionAnalyser(listenerManager));
        if (loggingConfiguration.getShowStacktrace() != ShowStacktrace.ALWAYS_FULL) {
            exceptionAnalyser = new StackTraceSanitizingExceptionAnalyser(exceptionAnalyser);
        }
        return exceptionAnalyser;
    }
}
