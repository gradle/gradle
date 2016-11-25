/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.invocation

import org.gradle.StartParameter
import org.gradle.api.internal.AsmBackedClassGenerator
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.initialization.ClassLoaderScopeRegistry
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.util.Path
import spock.lang.Specification

class DefaultGradleSpec extends Specification {
    private serviceRegistryFactory = Stub(ServiceRegistryFactory)
    private classGenerator = new AsmBackedClassGenerator();

    def setup() {
        def serviceRegistry = Stub(ServiceRegistry)
        _ * serviceRegistryFactory.createFor(_) >> serviceRegistry
        _ * serviceRegistry.get(ClassLoaderScopeRegistry) >> Stub(ClassLoaderScopeRegistry)
        _ * serviceRegistry.get(ListenerManager) >> new DefaultListenerManager()
    }

    def hasIdentityPath() {
        def root = classGenerator.newInstance(DefaultGradle, null, Stub(StartParameter), serviceRegistryFactory)

        def child1 = classGenerator.newInstance(DefaultGradle, root, Stub(StartParameter), serviceRegistryFactory)
        child1.rootProject = project('child1')

        def child2 = classGenerator.newInstance(DefaultGradle, child1, Stub(StartParameter), serviceRegistryFactory)
        child2.rootProject = project('child2')

        expect:
        root.identityPath == Path.ROOT
        child1.identityPath == Path.path(":child1")
        child2.identityPath == Path.path(":child1:child2")
    }

    private ProjectInternal project(String name) {
        def project = Stub(ProjectInternal)
        _ * project.name >> name
        return project
    }
}
