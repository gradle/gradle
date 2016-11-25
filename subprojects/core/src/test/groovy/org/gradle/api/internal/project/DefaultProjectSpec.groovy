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

package org.gradle.api.internal.project

import org.gradle.api.AttributesSchema
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.util.Path
import spock.lang.Specification

class DefaultProjectSpec extends Specification {
    def "has useful toString and displayName and paths"() {
        def rootBuild = Stub(GradleInternal)
        rootBuild.parent >> null
        rootBuild.identityPath >> Path.ROOT

        def nestedBuild = Stub(GradleInternal)
        nestedBuild.parent >> rootBuild
        nestedBuild.identityPath >> Path.path(":nested")

        def rootProject = project("root", null, rootBuild)
        def child1 = project("child1", rootProject, rootBuild)
        def child2 = project("child2", child1, rootBuild)

        def nestedRootProject = project("root", null, nestedBuild)
        def nestedChild1 = project("child1", nestedRootProject, nestedBuild)
        def nestedChild2 = project("child2", nestedChild1, nestedBuild)

        expect:
        rootProject.toString() == "root project 'root'"
        rootProject.displayName == "root project 'root'"
        rootProject.path == ":"
        rootProject.identityPath == Path.ROOT

        child1.toString() == "project ':child1'"
        child1.displayName == "project ':child1'"
        child1.path == ":child1"
        child1.identityPath == Path.path(":child1")

        child2.toString() == "project ':child1:child2'"
        child2.displayName == "project ':child1:child2'"
        child2.path == ":child1:child2"
        child2.identityPath == Path.path(":child1:child2")

        nestedRootProject.toString() == "project ':nested'"
        nestedRootProject.displayName == "project ':nested'"
        nestedRootProject.path == ":"
        nestedRootProject.identityPath == Path.path(":nested")

        nestedChild1.toString() == "project ':nested:child1'"
        nestedChild1.displayName == "project ':nested:child1'"
        nestedChild1.path == ":child1"
        nestedChild1.identityPath == Path.path(":nested:child1")

        nestedChild2.toString() == "project ':nested:child1:child2'"
        nestedChild2.displayName == "project ':nested:child1:child2'"
        nestedChild2.path == ":child1:child2"
        nestedChild2.identityPath == Path.path(":nested:child1:child2")
    }

    def project(String name, ProjectInternal parent, GradleInternal build) {
        def serviceRegistryFactory = Stub(ServiceRegistryFactory)
        def serviceRegistry = Stub(ServiceRegistry)

        _ * serviceRegistryFactory.createFor(_) >> serviceRegistry
        _ * serviceRegistry.newInstance(TaskContainerInternal) >> Stub(TaskContainerInternal)
        _ * serviceRegistry.get(Instantiator) >> DirectInstantiator.INSTANCE
        _ * serviceRegistry.get(AttributesSchema) >> Stub(AttributesSchema)
        _ * serviceRegistry.get(ModelRegistry) >> Stub(ModelRegistry)

        new DefaultProject(name, parent, new File("project"), Stub(ScriptSource), build, serviceRegistryFactory, Stub(ClassLoaderScope), Stub(ClassLoaderScope))
    }
}
