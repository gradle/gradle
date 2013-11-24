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
import org.gradle.api.internal.changedetection.state.InMemoryTaskArtifactCache
import org.gradle.api.internal.plugins.DefaultPluginContainer
import org.gradle.api.internal.plugins.PluginRegistry
import org.gradle.api.internal.project.DefaultProjectRegistry
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectRegistry
import org.gradle.api.internal.tasks.options.OptionReader
import org.gradle.api.plugins.PluginContainer
import org.gradle.cache.CacheRepository
import org.gradle.execution.BuildExecuter
import org.gradle.execution.DefaultBuildExecuter
import org.gradle.execution.TaskGraphExecuter
import org.gradle.execution.TaskSelector
import org.gradle.execution.taskgraph.DefaultTaskGraphExecuter
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.service.ServiceRegistry
import org.gradle.invocation.BuildClassLoaderRegistry
import org.gradle.listener.ListenerManager
import spock.lang.Specification

import static org.hamcrest.Matchers.sameInstance

public class GradleScopeServicesTest extends Specification {
    private GradleInternal gradle = Stub()
    private ServiceRegistry parent = Stub()
    private ListenerManager listenerManager = Stub()
    private CacheRepository cacheRepository = Stub()
    private GradleScopeServices registry = new GradleScopeServices(parent, gradle)
    private StartParameter startParameter = new StartParameter()
    private PluginRegistry pluginRegistryParent = Stub()
    private PluginRegistry pluginRegistryChild = Stub()

    public void setup() {
        parent.get(StartParameter) >> Stub(StartParameter)
        parent.get(InMemoryTaskArtifactCache) >> Stub(InMemoryTaskArtifactCache)
        parent.get(ListenerManager) >> listenerManager
        parent.get(CacheRepository) >> cacheRepository
        parent.get(PluginRegistry) >> pluginRegistryParent
        parent.get(BuildClassLoaderRegistry) >> Stub(BuildClassLoaderRegistry)
        parent.get(DependencyManagementServices) >> Stub(DependencyManagementServices)
        parent.get(ExecutorFactory) >> Stub(ExecutorFactory)
        gradle.getStartParameter() >> startParameter
        pluginRegistryParent.createChild(_, _) >> pluginRegistryChild
    }

    def "can create services for a project instance"() {
        ProjectInternal project = Mock()

        when:
        ServiceRegistryFactory serviceRegistry = registry.createFor(project)

        then:
        serviceRegistry instanceof ProjectScopeServices
    }

    def "provides a project registry"() {
        when:
        def projectRegistry = registry.get(ProjectRegistry)
        def secondRegistry = registry.get(ProjectRegistry)

        then:
        projectRegistry instanceof DefaultProjectRegistry
        projectRegistry sameInstance(secondRegistry)
    }

    def "provides a plugin registry"() {
        when:
        def pluginRegistry = registry.get(PluginRegistry)
        def secondRegistry = registry.get(PluginRegistry)

        then:
        pluginRegistry == pluginRegistryChild
        secondRegistry sameInstance(pluginRegistry)
    }

    def "provides a build executer"() {
        when:
        def buildExecuter = registry.get(BuildExecuter)
        def secondExecuter = registry.get(BuildExecuter)

        then:
        buildExecuter instanceof DefaultBuildExecuter
        buildExecuter sameInstance(secondExecuter)
    }

    def "provides a plugin container"() {
        when:
        def pluginContainer = registry.get(PluginContainer)
        def secondPluginContainer = registry.get(PluginContainer)

        then:
        pluginContainer instanceof DefaultPluginContainer
        secondPluginContainer sameInstance(pluginContainer)
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
}
