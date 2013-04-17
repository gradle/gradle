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


import org.gradle.StartParameter
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.IdentityFileResolver
import org.gradle.api.internal.plugins.DefaultPluginContainer
import org.gradle.api.internal.plugins.PluginRegistry
import org.gradle.api.plugins.PluginContainer
import org.gradle.cache.CacheRepository
import org.gradle.execution.BuildExecuter
import org.gradle.execution.DefaultBuildExecuter
import org.gradle.execution.TaskGraphExecuter
import org.gradle.execution.taskgraph.DefaultTaskGraphExecuter
import org.gradle.internal.service.ServiceRegistry
import org.gradle.listener.ListenerManager
import org.gradle.util.MultiParentClassLoader
import spock.lang.Specification

import static org.hamcrest.Matchers.sameInstance

public class GradleInternalServiceRegistryTest extends Specification {
    private GradleInternal gradle = Mock()
    private ServiceRegistry parent = Mock()
    private ListenerManager listenerManager = Mock()
    private CacheRepository cacheRepository = Mock()
    private GradleInternalServiceRegistry registry = new GradleInternalServiceRegistry(parent, gradle)
    private StartParameter startParameter = new StartParameter()
    private PluginRegistry pluginRegistryParent = Mock()
    private PluginRegistry pluginRegistryChild = Mock()

    public void setup() {
        parent.get(StartParameter) >> Mock(StartParameter)
        parent.get(ListenerManager) >> listenerManager
        parent.get(CacheRepository) >> cacheRepository
        parent.get(DocumentationRegistry) >> Mock(DocumentationRegistry)
        parent.get(PluginRegistry) >> pluginRegistryParent
        gradle.getStartParameter() >> startParameter
        gradle.getScriptClassLoader() >> new MultiParentClassLoader()
        pluginRegistryParent.createChild(_, _) >> pluginRegistryChild
    }

    def "can create services for a project instance"() {
        ProjectInternal project = Mock()

        when:
        ServiceRegistryFactory serviceRegistry = registry.createFor(project)

        then:
        serviceRegistry instanceof ProjectInternalServiceRegistry
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

    def "provides a file resolver"() {
        when:
        def fileResolver = registry.get(FileResolver)
        def secondFileResolver = registry.get(FileResolver)

        then:
        fileResolver instanceof IdentityFileResolver
        secondFileResolver sameInstance(fileResolver)
    }

    def "provides a task graph executer"() {
        when:
        def graphExecuter = registry.get(TaskGraphExecuter)
        def secondExecuter = registry.get(TaskGraphExecuter)

        then:
        graphExecuter instanceof DefaultTaskGraphExecuter
        graphExecuter sameInstance(secondExecuter)
    }
}
