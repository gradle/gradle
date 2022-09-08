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

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.ClientModule
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler
import org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.attributes.Category
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.VariantTransformRegistry
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.internal.artifacts.query.ArtifactResolutionQueryFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.reflect.TypeOf
import org.gradle.internal.Factory
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.util.concurrent.Callable

class DefaultDependencyHandlerTest extends Specification {

    private static final String TEST_CONF_NAME = "someConf"
    private static final String UNKNOWN_TEST_CONF_NAME = "unknown"

    private ConfigurationContainer configurationContainer = Mock()
    private DependencyFactoryInternal dependencyFactory = Mock()
    private Configuration configuration = Mock()
    private ProjectFinder projectFinder = Mock()
    private DependencySet dependencySet = Mock()

    private DefaultDependencyHandler dependencyHandler = TestUtil.instantiatorFactory().decorateLenient().newInstance(DefaultDependencyHandler,
        configurationContainer, dependencyFactory, projectFinder, Stub(DependencyConstraintHandler), Stub(ComponentMetadataHandler), Stub(ComponentModuleMetadataHandler), Stub(ArtifactResolutionQueryFactory),
        Stub(AttributesSchema), Stub(VariantTransformRegistry), Stub(Factory), TestUtil.objectFactory(), DependencyManagementTestUtil.platformSupport())

    void setup() {
        _ * configurationContainer.findByName(TEST_CONF_NAME) >> configuration
        _ * configurationContainer.getByName(TEST_CONF_NAME) >> configuration
        _ * configurationContainer.getByName(UNKNOWN_TEST_CONF_NAME) >> { throw new UnknownDomainObjectException("") }
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
        def result = dependencyHandler.create("someNotation") {
            force = true
            version {
                it.require '1.0'
            }
        }

        then:
        result == dependency

        and:
        1 * dependencyFactory.createDependency("someNotation") >> dependency
        1 * dependency.setForce(true)
        1 * dependency.version(_ as Action<VersionConstraint>)
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

    void "dynamic method fails for unknown configuration"() {
        when:
        dependencyHandler.unknown("someDep")

        then:
        def e = thrown(MissingMethodException)
        e.message.startsWith('Could not find method unknown() for arguments [someDep] on ')
    }

    void "dynamic method fails for no args"() {
        when:
        dependencyHandler.someConf()

        then:
        def e = thrown(MissingMethodException)
        e.message.startsWith('Could not find method someConf() for arguments [] on ')
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
        1 * dependencyFactory.createDependency(DependencyFactoryInternal.ClassPathNotation.GRADLE_API) >> dependency
    }

    void "creates Gradle test-kit dependency"() {
        Dependency dependency = Mock()

        when:
        def result = dependencyHandler.gradleTestKit()

        then:
        result == dependency

        and:
        1 * dependencyFactory.createDependency(DependencyFactoryInternal.ClassPathNotation.GRADLE_TEST_KIT) >> dependency
    }

    void "creates local groovy dependency"() {
        Dependency dependency = Mock()

        when:
        def result = dependencyHandler.localGroovy()

        then:
        result == dependency

        and:
        1 * dependencyFactory.createDependency(DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY) >> dependency
    }

    void "cannot add dependency to unknown configuration"() {
        when:
        dependencyHandler.add(UNKNOWN_TEST_CONF_NAME, "someNotation")

        then:
        thrown(UnknownDomainObjectException)
    }

    void "reasonable error when supplying null as a dependency notation"() {
        when:
        dependencyHandler."$TEST_CONF_NAME"(null)

        then:
        1 * dependencyFactory.createDependency(null)
    }

    void "platform dependencies are endorsing"() {
        ModuleDependency dep1 = new DefaultExternalModuleDependency("org", "platform", "")
        dep1.attributesFactory = AttributeTestUtil.attributesFactory()
        ModuleDependency dep2 = new DefaultExternalModuleDependency("org", "platform", "")
        dep2.attributesFactory = AttributeTestUtil.attributesFactory()

        when:
        dependencyHandler.platform("org:platform")

        then:
        1 * dependencyFactory.createDependency("org:platform") >> dep1
        dep1.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE).name == 'platform'
        dep1.isEndorsingStrictVersions()
        dep1.version == null

        when:
        dependencyHandler.platform("org:platform") { it.version { it.require('1.0') } }

        then:
        1 * dependencyFactory.createDependency("org:platform") >> dep2
        dep2.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE).name == 'platform'
        dep2.isEndorsingStrictVersions()
        dep2.version == '1.0'
    }

    void "local platform dependencies are endorsing"() {
        ModuleDependency dep1 = new DefaultProjectDependency(null, null, false)
        dep1.attributesFactory = AttributeTestUtil.attributesFactory()
        ModuleDependency dep2 = new DefaultProjectDependency(null, null, false)
        dep2.attributesFactory = AttributeTestUtil.attributesFactory()

        when:
        dependencyHandler.platform(dep1)

        then:
        1 * dependencyFactory.createDependency(dep1) >> dep1
        dep1.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE).name == 'platform'
        dep1.isEndorsingStrictVersions()

        when:
        dependencyHandler.platform(dep2) { }

        then:
        1 * dependencyFactory.createDependency(dep2) >> dep2
        dep2.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE).name == 'platform'
        dep2.isEndorsingStrictVersions()
    }

    void "platform dependency can be made non-endorsing"() {
        ModuleDependency dep1 = new DefaultExternalModuleDependency("org", "platform", "")
        dep1.attributesFactory = AttributeTestUtil.attributesFactory()

        when:
        dependencyHandler.platform("org:platform") { it.doNotEndorseStrictVersions() }

        then:
        1 * dependencyFactory.createDependency("org:platform") >> dep1
        dep1.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE).name == 'platform'
        !dep1.isEndorsingStrictVersions()
    }

    void "local platform dependency can be made non-endorsing"() {
        ModuleDependency dep1 = new DefaultProjectDependency(null, null, false)
        dep1.attributesFactory = AttributeTestUtil.attributesFactory()

        when:
        dependencyHandler.platform(dep1) { it.doNotEndorseStrictVersions() }

        then:
        1 * dependencyFactory.createDependency(dep1) >> dep1
        dep1.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE).name == 'platform'
        !dep1.isEndorsingStrictVersions()
    }

    @CompileStatic
    void "can configure ExtensionAware statically"() {
        String dependency = "some:random-dependency:0.1.1"
        when:

        Callable<List<String>> exampleDependencies = {
            [dependency]
        }

        def callableType = new TypeOf<Callable<List<String>>>() {}
        dependencyHandler.extensions.add(callableType, "example", exampleDependencies)

        ExtensionContainer extension = dependencyHandler.extensions
        Callable<List<String>> backOut = (extension.getByName("example") as Callable<List<String>>)
        String backOutValue = backOut().first()
        then:
        backOutValue == dependency
    }
}
