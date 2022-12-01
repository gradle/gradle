/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.plugins.jvm.internal

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyAdder
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.plugins.jvm.JvmComponentDependencies
import org.gradle.util.TestUtil
import org.gradle.util.internal.ConfigureUtil
import spock.lang.Specification

import javax.inject.Inject

class DefaultJvmComponentDependenciesTest extends Specification {
    def currentProject = Mock(Project)
    def dependencyFactory = Mock(DependencyFactory)
    def implementation = Mock(DependencyAdder)

    static abstract class MockingDefaultJvmComponentDependencies extends DefaultJvmComponentDependencies {
        DefaultJvmComponentDependenciesTest mockSource

        @Inject
        MockingDefaultJvmComponentDependencies(DependencyAdder implementation, DependencyAdder compileOnly, DependencyAdder runtimeOnly, DependencyAdder annotationProcessor, DefaultJvmComponentDependenciesTest mockSource) {
            super(implementation, compileOnly, runtimeOnly, annotationProcessor)
            this.mockSource = mockSource
        }

        @Override
        Project getProject() {
            return mockSource.currentProject
        }

        @Override
        DependencyFactory getDependencyFactory() {
            return mockSource.dependencyFactory
        }
    }
    def dependencies = TestUtil.objectFactory().newInstance(MockingDefaultJvmComponentDependencies, implementation, Mock (DependencyAdder), Mock(DependencyAdder), Mock(DependencyAdder), this)

    def "String notation is supported"() {
        when:
        dependencies {
            implementation "com.example:example:1.0"
            implementation("com.example:example:2.0") {
                // configure dependency
            }
        }
        then:
        1 * implementation.add('com.example:example:1.0')
        1 * implementation.add('com.example:example:2.0', _ as Action)
        0 * _._
    }

    def "GAV notation is supported"() {
        def example = Mock(ExternalModuleDependency)
        def example2 = Mock(ExternalModuleDependency)
        when:
        dependencies {
            implementation module(group: "com.example", name: "example", version: "1.0")
            implementation(module(group: "com.example", name: "example", version: "2.0")) {
                // configure dependency
            }
        }
        then:
        1 * dependencyFactory.create("com.example", "example", "1.0") >> example
        1 * dependencyFactory.create("com.example", "example", "2.0") >> example2

        1 * implementation.add(example)
        1 * implementation.add(example2, _ as Action)
        0 * _._
    }

    def "can depend on a project"() {
        def currentProjectDependency = Mock(ProjectDependency)
        def otherProject = Mock(Project)
        def otherProjectDependency = Mock(ProjectDependency)

        when:
        dependencies {
            implementation project()
        }
        then:
        1 * dependencyFactory.create(currentProject) >> currentProjectDependency
        1 * implementation.add(currentProjectDependency)

        0 * _._

        when:
        dependencies {
            implementation(project(":path:to:somewhere")) {
                // configure dependency
            }
        }
        then:
        1 * currentProject.project(":path:to:somewhere") >> otherProject
        1 * dependencyFactory.create(otherProject) >> otherProjectDependency
        1 * implementation.add(otherProjectDependency, _ as Action)

        0 * _._
    }

    def "can depend on self resolving dependencies"() {
        def localGroovyDependency = Mock(Dependency)
        def gradleApiDependency = Mock(Dependency)
        def gradleTestKitDependency = Mock(Dependency)

        when:
        dependencies {
            implementation localGroovy()
            implementation gradleApi()
            implementation gradleTestKit()
        }
        then:
        1 * dependencyFactory.localGroovy() >> localGroovyDependency
        1 * dependencyFactory.gradleApi() >> gradleApiDependency
        1 * dependencyFactory.gradleTestKit() >> gradleTestKitDependency
        1 * implementation.add(localGroovyDependency)
        1 * implementation.add(gradleApiDependency)
        1 * implementation.add(gradleTestKitDependency)

        0 * _._
    }

    private void dependencies(@DelegatesTo(JvmComponentDependencies.class) Closure c) {
        ConfigureUtil.configure(c, dependencies)
    }
}
