/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.StartParameter;
import org.gradle.api.internal.DependencyInjectingInstantiator;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.plugins.DefaultPluginContainer;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.execution.*;
import org.gradle.execution.taskgraph.DefaultTaskGraphExecuter;
import org.gradle.execution.taskgraph.TaskPlanExecutor;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.listener.ListenerManager;

import java.util.LinkedList;
import java.util.List;

import static java.util.Arrays.asList;

/**
 * Contains the services for a given {@link GradleInternal} instance.
 */
public class GradleInternalServiceRegistry extends DefaultServiceRegistry implements ServiceRegistryFactory {
    private final GradleInternal gradle;

    public GradleInternalServiceRegistry(ServiceRegistry parent, final GradleInternal gradle) {
        super(parent);
        this.gradle = gradle;
        add(new TaskExecutionServices(parent, gradle));
    }

    protected BuildExecuter createBuildExecuter() {
        List<BuildConfigurationAction> configs = new LinkedList<BuildConfigurationAction>();
        if (get(StartParameter.class).isConfigureOnDemand()) {
            configs.add(new ProjectEvaluatingAction());
        }
        configs.add(new DefaultTasksBuildExecutionAction());
        configs.add(new ExcludedTaskFilteringBuildConfigurationAction());
        configs.add(new TaskNameResolvingBuildConfigurationAction());

        return new DefaultBuildExecuter(
                configs,
                asList(new DryRunBuildExecutionAction(),
                       new SelectedTaskExecutionAction()));
    }

    protected ProjectFinder createProjectFinder() {
        return new ProjectFinder() {
            public ProjectInternal getProject(String path) {
                return gradle.getRootProject().project(path);
            }
        };
    }

    protected ProjectRegistry createIProjectRegistry() {
        return new DefaultProjectRegistry<ProjectInternal>();
    }

    protected TaskGraphExecuter createTaskGraphExecuter() {
        return new DefaultTaskGraphExecuter(get(ListenerManager.class), get(TaskPlanExecutor.class));
    }

    public ServiceRegistryFactory createFor(Object domainObject) {
        if (domainObject instanceof ProjectInternal) {
            return new ProjectInternalServiceRegistry(this, (ProjectInternal) domainObject);
        }
        throw new UnsupportedOperationException();
    }

    protected FileResolver createFileResolver() {
        return new IdentityFileResolver();
    }

    protected PluginRegistry createPluginRegistry(PluginRegistry parentRegistry) {
        return parentRegistry.createChild(gradle.getScriptClassLoader(), new DependencyInjectingInstantiator(this));
    }

    protected PluginContainer createPluginContainer() {
        return new DefaultPluginContainer(get(PluginRegistry.class), gradle);
    }
}
