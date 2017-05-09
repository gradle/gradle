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
package org.gradle.internal.service.scopes

import org.gradle.StartParameter
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.DependencyManagementServices
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory
import org.gradle.api.internal.plugins.PluginRegistry
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.options.OptionReader
import org.gradle.cache.CacheRepository
import org.gradle.execution.BuildConfigurationActionExecuter
import org.gradle.execution.BuildExecuter
import org.gradle.execution.DefaultBuildExecuter
import org.gradle.execution.ProjectConfigurer
import org.gradle.execution.TaskGraphExecuter
import org.gradle.execution.TaskSelector
import org.gradle.execution.taskgraph.DefaultTaskGraphExecuter
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.internal.time.TimeProvider
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.environment.GradleBuildEnvironment
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.resources.ResourceLockCoordinationService
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.model.internal.inspect.ModelRuleSourceDetector
import spock.lang.Specification

import static org.hamcrest.Matchers.sameInstance

public class GradleScopeServicesTest extends Specification {
    private GradleInternal gradle = Stub()
    private ServiceRegistry parent = Stub()
    private CacheRepository cacheRepository = Stub()
    private GradleScopeServices registry = new GradleScopeServices(parent, gradle)
    private StartParameter startParameter = new StartParameter()
    private PluginRegistry pluginRegistryParent = Stub()
    private PluginRegistry pluginRegistryChild = Stub()

    public void setup() {
        parent.get(StartParameter) >> Stub(StartParameter) { _ * getMaxWorkerCount() >> 1 }
        parent.get(GradleBuildEnvironment) >> Stub(GradleBuildEnvironment)
        parent.get(InMemoryCacheDecoratorFactory) >> Stub(InMemoryCacheDecoratorFactory)
        parent.get(ListenerManager) >> new DefaultListenerManager()
        parent.get(CacheRepository) >> cacheRepository
        parent.get(PluginRegistry) >> pluginRegistryParent
        parent.get(DependencyManagementServices) >> Stub(DependencyManagementServices)
        parent.get(ExecutorFactory) >> Stub(ExecutorFactory)
        parent.get(BuildCancellationToken) >> Stub(BuildCancellationToken)
        parent.get(ProjectConfigurer) >> Stub(ProjectConfigurer)
        parent.get(ModelRuleSourceDetector) >> Stub(ModelRuleSourceDetector)
        parent.get(TimeProvider) >> Stub(TimeProvider)
        parent.get(BuildOperationExecutor) >> Stub(BuildOperationExecutor)
        parent.get(WorkerLeaseService) >> Stub(WorkerLeaseService)
        parent.get(ResourceLockCoordinationService) >> Stub(ResourceLockCoordinationService)
        parent.get(Instantiator) >> Stub(Instantiator)
        parent.get(WorkerLeaseRegistry) >> Stub(WorkerLeaseRegistry)
        gradle.getStartParameter() >> startParameter
        pluginRegistryParent.createChild(_, _, _) >> pluginRegistryChild
    }

    def "can create services for a project instance"() {
        ProjectInternal project = Mock()

        when:
        def serviceRegistry = registry.get(ServiceRegistryFactory).createFor(project)

        then:
        serviceRegistry instanceof ProjectScopeServices
    }

    def "created project registries are closed on close"() {
        ProjectInternal project1 = Mock()
        ProjectInternal project2 = Mock()

        when:
        def serviceRegistry1 = registry.get(ServiceRegistryFactory).createFor(project1)
        def serviceRegistry2 = registry.get(ServiceRegistryFactory).createFor(project2)

        then:
        !serviceRegistry1.closed
        !serviceRegistry2.closed

        when:
        registry.close()

        then:
        serviceRegistry1.closed
        serviceRegistry2.closed
    }

    def "provides a build executer"() {
        when:
        def buildExecuter = registry.get(BuildExecuter)
        def secondExecuter = registry.get(BuildExecuter)

        then:
        buildExecuter instanceof DefaultBuildExecuter
        buildExecuter sameInstance(secondExecuter)
    }

    def "provides a build configuration action executer"() {
        when:
        def firstExecuter = registry.get(BuildConfigurationActionExecuter)
        def secondExecuter = registry.get(BuildConfigurationActionExecuter)

        then:
        firstExecuter instanceof BuildConfigurationActionExecuter
        firstExecuter sameInstance(secondExecuter)
    }

    def "provides a task graph executer"() {
        when:
        def graphExecuter = registry.get(TaskGraphExecuter)
        def secondExecuter = registry.get(TaskGraphExecuter)

        then:
        graphExecuter instanceof DefaultTaskGraphExecuter
        graphExecuter sameInstance(secondExecuter)
    }

    def "provides a task selector"() {
        when:
        def selector = registry.get(TaskSelector)
        def secondSelector = registry.get(TaskSelector)

        then:
        selector instanceof TaskSelector
        secondSelector sameInstance(selector)
    }

    def "provides an option reader"() {
        when:
        def optionReader = registry.get(OptionReader)
        def secondOptionReader = registry.get(OptionReader)

        then:
        optionReader instanceof OptionReader
        secondOptionReader sameInstance(optionReader)
    }

    def "adds all plugin gradle scope services"() {
        def plugin1 = Mock(PluginServiceRegistry)
        def plugin2 = Mock(PluginServiceRegistry)

        given:
        parent.getAll(PluginServiceRegistry) >> [plugin1, plugin2]

        when:
        new GradleScopeServices(parent, gradle)

        then:
        1 * plugin1.registerGradleServices(_)
        1 * plugin2.registerGradleServices(_)
    }
}
