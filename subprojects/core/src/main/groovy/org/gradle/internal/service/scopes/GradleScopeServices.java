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
package org.gradle.internal.service.scopes;

import org.gradle.StartParameter;
import org.gradle.api.internal.DependencyInjectingInstantiator;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.plugins.DefaultPluginContainer;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.options.OptionReader;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.execution.*;
import org.gradle.execution.commandline.CommandLineTaskConfigurer;
import org.gradle.execution.commandline.CommandLineTaskParser;
import org.gradle.execution.taskgraph.DefaultTaskGraphExecuter;
import org.gradle.execution.taskgraph.TaskPlanExecutor;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.listener.ListenerManager;

import java.util.LinkedList;
import java.util.List;

import static java.util.Arrays.asList;

/**
 * Contains the services for a given {@link GradleInternal} instance.
 */
public class GradleScopeServices extends DefaultServiceRegistry {

    private final CompositeStoppable registries = new CompositeStoppable();

    public GradleScopeServices(ServiceRegistry parent, GradleInternal gradle) {
        super(parent);
        add(GradleInternal.class, gradle);
        addProvider(new TaskExecutionServices());
    }

    TaskSelector createTaskSelector(GradleInternal gradle) {
        return new TaskSelector(gradle);
    }

    OptionReader createOptionReader() {
        return new OptionReader();
    }

    CommandLineTaskParser createCommandLineTaskParser(OptionReader optionReader) {
        return new CommandLineTaskParser(new CommandLineTaskConfigurer(optionReader));
    }

    BuildExecuter createBuildExecuter(CommandLineTaskParser commandLineTaskParser, TaskSelector taskSelector) {
        List<BuildConfigurationAction> configs = new LinkedList<BuildConfigurationAction>();
        if (get(StartParameter.class).isConfigureOnDemand()) {
            configs.add(new ProjectEvaluatingAction());
        }
        configs.add(new DefaultTasksBuildExecutionAction());
        configs.add(new ExcludedTaskFilteringBuildConfigurationAction());
        configs.add(new TaskNameResolvingBuildConfigurationAction(commandLineTaskParser, taskSelector));

        return new DefaultBuildExecuter(
                configs,
                asList(new DryRunBuildExecutionAction(),
                        new SelectedTaskExecutionAction()));
    }

    ProjectFinder createProjectFinder(final GradleInternal gradle) {
        return new ProjectFinder() {
            public ProjectInternal getProject(String path) {
                return gradle.getRootProject().project(path);
            }
        };
    }

    TaskGraphExecuter createTaskGraphExecuter(ListenerManager listenerManager, TaskPlanExecutor taskPlanExecutor) {
        return new DefaultTaskGraphExecuter(listenerManager, taskPlanExecutor);
    }

    ServiceRegistryFactory createServiceRegistryFactory(final ServiceRegistry services) {
        return new ServiceRegistryFactory() {
            public ServiceRegistry createFor(Object domainObject) {
                if (domainObject instanceof ProjectInternal) {
                    ProjectScopeServices projectScopeServices = new ProjectScopeServices(services, (ProjectInternal) domainObject);
                    registries.add(projectScopeServices);
                    return projectScopeServices;
                }
                throw new UnsupportedOperationException();
            }
        };
    }

    PluginRegistry createPluginRegistry(PluginRegistry parentRegistry) {
        return parentRegistry.createChild(get(GradleInternal.class).getClassLoaderScope(), new DependencyInjectingInstantiator(this));
    }

    PluginContainer createPluginContainer(GradleInternal gradle, PluginRegistry pluginRegistry) {
        return new DefaultPluginContainer<GradleInternal>(pluginRegistry, gradle);
    }

    @Override
    public void close() {
        registries.stop();
        super.close();
    }
}
