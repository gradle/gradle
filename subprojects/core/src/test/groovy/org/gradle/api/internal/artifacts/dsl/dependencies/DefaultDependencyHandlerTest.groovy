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
package org.gradle.api.internal.artifacts.dsl.dependencies

import spock.lang.Specification
import org.gradle.api.artifacts.*

/**
 * @author Hans Dockter
 */
class DefaultDependencyHandlerTest extends Specification {
    private static final String TEST_CONF_NAME = "someConf"
    private ConfigurationContainer configurationContainer = Mock()
    private DependencyFactory dependencyFactory = Mock()
    private Configuration configuration = Mock()
    private ProjectFinder projectFinder = Mock()
    private DependencySet dependencySet = Mock()

    private DefaultDependencyHandler dependencyHandler = new DefaultDependencyHandler(configurationContainer, dependencyFactory, projectFinder)

    void setup() {
        _ * configurationContainer.findByName(TEST_CONF_NAME) >> configuration
        _ * configurationContainer.getAt(TEST_CONF_NAME) >> configuration
        _ * configuration.dependencies >> dependencySet
    }

    void "creates and adds a dependency from some notation"() {
        Dependency dependency = Mock()

        when:
        def result = dependencyHandler.add(TEST_CONF_NAME, "someNotation")

        then:
        result == dependency

        and:
        1 * dependencyFactory.createDependency("someNotation") >> dependency
        1 * dependencySet.add(dependency)
    }

    void "creates, configures and adds a dependency from some notation"() {
        ExternalDependency dependency = Mock()

        when:
        def result = dependencyHandler.add(TEST_CONF_NAME, "someNotation") {
            force = true
        }

        then:
        result == dependency

        and:
        1 * dependencyFactory.createDependency("someNotation") >> dependency
        1 * dependency.setForce(true)
        1 * dependencySet.add(dependency)
    }

    void "creates a dependency from some notation"() {
        Dependency dependency = Mock()

        when:
        def result = dependencyHandler.create("someNotation")

        then:
        result == dependency

        and:
        1 * dependencyFactory.createDependency("someNotation") >> dependency
    }

    void "creates and configures a dependency from some notation"() {
        ExternalDependency dependency = Mock()

        when:
        def result = dependencyHandler.create("someNotation") { force = true}

        then:
        result == dependency

        and:
        1 * dependencyFactory.createDependency("someNotation") >> dependency
        1 * dependency.setForce(true)
    }

    void "can use dynamic method to add dependency"() {
        Dependency dependency = Mock()

        when:
        def result = dependencyHandler.someConf("someNotation")

        then:
        result == dependency

        and:
        1 * dependencyFactory.createDependency("someNotation") >> dependency
        1 * dependencySet.add(dependency)

    }

    void "can use dynamic method to add and configure dependency"() {
        ExternalDependency dependency = Mock()

        when:
        def result = dependencyHandler.someConf("someNotation") { force = true }

        then:
        result == dependency

        and:
        1 * dependencyFactory.createDependency("someNotation") >> dependency
        1 * dependencySet.add(dependency)
        1 * dependency.setForce(true)
    }

    void "can use dynamic method to add multiple dependencies"() {
        Dependency dependency1 = Mock()
        Dependency dependency2 = Mock()

        when:
        def result = dependencyHandler.someConf("someNotation", "someOther")

        then:
        result == null

        and:
        1 * dependencyFactory.createDependency("someNotation") >> dependency1
        1 * dependencyFactory.createDependency("someOther") >> dependency2
        1 * dependencySet.add(dependency1)
        1 * dependencySet.add(dependency2)
    }

    void "can use dynamic method to add and config multiple dependencies"() {
        ExternalDependency dependency1 = Mock()
        ExternalDependency dependency2 = Mock()

        when:
        def result = dependencyHandler.someConf("someNotation", "someOther") { force = false }

        then:
        result == null

        and:
        1 * dependencyFactory.createDependency("someNotation") >> dependency1
        1 * dependencyFactory.createDependency("someOther") >> dependency2
        1 * dependencySet.add(dependency1)
        1 * dependencySet.add(dependency2)
        1 * dependency1.setForce(false)
        1 * dependency2.setForce(false)
    }

   void "can use dynamic method to add multiple dependencies from nested lists"() {
        Dependency dependency1 = Mock()
        Dependency dependency2 = Mock()

        when:
        def result = dependencyHandler.someConf([["someNotation"], ["someOther"]])

        then:
        result == null

        and:
        1 * dependencyFactory.createDependency("someNotation") >> dependency1
        1 * dependencyFactory.createDependency("someOther") >> dependency2
        1 * dependencySet.add(dependency1)
        1 * dependencySet.add(dependency2)
    }

    void "can use dynamic method to add and config multiple dependencies from nested lists"() {
        ExternalDependency dependency1 = Mock()
        ExternalDependency dependency2 = Mock()

        when:
        def result = dependencyHandler.someConf([["someNotation"], ["someOther"]]) { force = false }

        then:
        result == null

        and:
        1 * dependencyFactory.createDependency("someNotation") >> dependency1
        1 * dependencyFactory.createDependency("someOther") >> dependency2
        1 * dependencySet.add(dependency1)
        1 * dependencySet.add(dependency2)
        1 * dependency1.setForce(false)
        1 * dependency2.setForce(false)
    }

    void "creates a project dependency from map"() {
        ProjectDependency projectDependency = Mock()

        when:
        def result = dependencyHandler.project([:])

        then:
        result == projectDependency

        and:
        1 * dependencyFactory.createProjectDependencyFromMap(projectFinder, [:]) >> projectDependency
    }

    void "attaches configuration from same project to target configuration"() {
        Configuration other = Mock()

        given:
        configurationContainer.contains(other) >> true

        when:
        def result = dependencyHandler.add(TEST_CONF_NAME, other)

        then:
        result == null

        and:
        1 * configuration.extendsFrom(other)
    }

    void "cannot create project dependency for configuration from different project"() {
        Configuration other = Mock()

        given:
        configurationContainer.contains(other) >> false

        when:
        dependencyHandler.add(TEST_CONF_NAME, other)

        then:
        UnsupportedOperationException e = thrown()
        e.message == 'Currently you can only declare dependencies on configurations from the same project.'
    }

    void "creates client module dependency"() {
        ClientModule clientModule = Mock()

        when:
        def result = dependencyHandler.module("someNotation")

        then:
        result == clientModule

        and:
        1 * dependencyFactory.createModule("someNotation", null) >> clientModule
    }

    void "creates and configures client module dependency"() {
        ClientModule clientModule = Mock()
        Closure cl = {}

        when:
        def result = dependencyHandler.module("someNotation", cl)

        then:
        result == clientModule

        and:
        1 * dependencyFactory.createModule("someNotation", cl) >> clientModule
    }

    void "creates gradle api dependency"() {
        Dependency dependency = Mock()

        when:
        def result = dependencyHandler.gradleApi()

        then:
        result == dependency

        and:
        1 * dependencyFactory.createDependency(DependencyFactory.ClassPathNotation.GRADLE_API) >> dependency
    }

    void "creates local groovy dependency"() {
        Dependency dependency = Mock()

        when:
        def result = dependencyHandler.localGroovy()

        then:
        result == dependency

        and:
        1 * dependencyFactory.createDependency(DependencyFactory.ClassPathNotation.LOCAL_GROOVY) >> dependency
    }

    void "cannot add dependency to unknown configuration"() {
        when:
        dependencyHandler.unknown("someNotation")

        then:
        thrown(MissingMethodException)
    }
}
