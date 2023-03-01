/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.DependencyConstraintSet
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.provider.Provider
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultDependencyConstraintHandlerTest extends Specification {

    private static final String TEST_CONF_NAME = "someConf"
    private static final String UNKNOWN_TEST_CONF_NAME = "unknown"

    private def configurationContainer = Mock(ConfigurationContainer)
    private def dependencyFactory = Mock(DependencyFactoryInternal)
    private def configuration = Mock(Configuration)
    private def dependencyConstraintSet = Mock(DependencyConstraintSet)

    private DefaultDependencyConstraintHandler dependencyConstraintHandler = TestUtil.instantiatorFactory().decorateLenient().newInstance(DefaultDependencyConstraintHandler, configurationContainer, dependencyFactory, TestUtil.objectFactory(), DependencyManagementTestUtil.platformSupport())

    void setup() {
        _ * configurationContainer.findByName(TEST_CONF_NAME) >> configuration
        _ * configurationContainer.getByName(TEST_CONF_NAME) >> configuration
        _ * configurationContainer.getByName(UNKNOWN_TEST_CONF_NAME) >> { throw new UnknownDomainObjectException("") }
        _ * configuration.dependencyConstraints >> dependencyConstraintSet
    }

    void "creates and adds a dependency constraint from some notation"() {
        def dependencyConstraint = Mock(DependencyConstraint)

        when:
        def result = dependencyConstraintHandler.add(TEST_CONF_NAME, "someNotation")

        then:
        result == dependencyConstraint

        and:
        1 * dependencyFactory.createDependencyConstraint("someNotation") >> dependencyConstraint
        1 * dependencyConstraintSet.add(dependencyConstraint)
    }

    void "creates, configures and adds a dependency constraint from some notation"() {
        def dependencyConstraint = Mock(DependencyConstraint)

        when:
        def result = dependencyConstraintHandler.add(TEST_CONF_NAME, "someNotation") {
            version { }
        }

        then:
        result == dependencyConstraint

        and:
        1 * dependencyFactory.createDependencyConstraint("someNotation") >> dependencyConstraint
        1 * dependencyConstraint.version(_ as Action<VersionConstraint>)
        1 * dependencyConstraintSet.add(dependencyConstraint)
    }

    void "creates a dependency constraint from some notation"() {
        def dependencyConstraint = Mock(DependencyConstraint)

        when:
        def result = dependencyConstraintHandler.create("someNotation")

        then:
        result == dependencyConstraint

        and:
        1 * dependencyFactory.createDependencyConstraint("someNotation") >> dependencyConstraint
    }

    void "creates and configures a dependency constraint from some notation"() {
        def dependencyConstraint = Mock(DependencyConstraint)

        when:
        def result = dependencyConstraintHandler.add(TEST_CONF_NAME, "someNotation") {
            version { }
        }

        then:
        result == dependencyConstraint

        and:
        1 * dependencyFactory.createDependencyConstraint("someNotation") >> dependencyConstraint
        1 * dependencyConstraint.version(_ as Action<VersionConstraint>)
    }

    void "can use dynamic method to add dependency constraint"() {
        def dependencyConstraint = Mock(DependencyConstraint)

        when:
        def result = dependencyConstraintHandler.someConf("someNotation")

        then:
        result == dependencyConstraint

        and:
        1 * dependencyFactory.createDependencyConstraint("someNotation") >> dependencyConstraint
        1 * dependencyConstraintSet.add(dependencyConstraint)

    }

    void "can use dynamic method to add and configure dependency constraint"() {
        def dependencyConstraint = Mock(DependencyConstraint)

        when:
        def result = dependencyConstraintHandler.someConf("someNotation") { version { } }

        then:
        result == dependencyConstraint

        and:
        1 * dependencyFactory.createDependencyConstraint("someNotation") >> dependencyConstraint
        1 * dependencyConstraintSet.add(dependencyConstraint)
        1 * dependencyConstraint.version(_ as Action<VersionConstraint>)
    }

    void "can use dynamic method to add multiple dependency constraint"() {
        def constraint1 = Mock(DependencyConstraint)
        def constraint2 = Mock(DependencyConstraint)

        when:
        def result = dependencyConstraintHandler.someConf("someNotation", "someOther")

        then:
        result == null

        and:
        1 * dependencyFactory.createDependencyConstraint("someNotation") >> constraint1
        1 * dependencyFactory.createDependencyConstraint("someOther") >> constraint2
        1 * dependencyConstraintSet.add(constraint1)
        1 * dependencyConstraintSet.add(constraint2)
    }

    void "can use dynamic method to add multiple dependency constraint from nested lists"() {
        def constraint1 = Mock(DependencyConstraint)
        def constraint2 = Mock(DependencyConstraint)

        when:
        def result = dependencyConstraintHandler.someConf([["someNotation"], ["someOther"]])

        then:
        result == null

        and:
        1 * dependencyFactory.createDependencyConstraint("someNotation") >> constraint1
        1 * dependencyFactory.createDependencyConstraint("someOther") >> constraint2
        1 * dependencyConstraintSet.add(constraint1)
        1 * dependencyConstraintSet.add(constraint2)
    }

    void "dynamic method fails for unknown configuration"() {
        when:
        dependencyConstraintHandler.unknown("someDep")

        then:
        def e = thrown(MissingMethodException)
        e.message.startsWith('Could not find method unknown() for arguments [someDep] on ')
    }

    void "dynamic method fails for no args"() {
        when:
        dependencyConstraintHandler.someConf()

        then:
        def e = thrown(MissingMethodException)
        e.message.startsWith('Could not find method someConf() for arguments [] on ')
    }

    void "cannot add dependency constraint to unknown configuration"() {
        when:
        dependencyConstraintHandler.add(UNKNOWN_TEST_CONF_NAME, "someNotation")

        then:
        thrown(UnknownDomainObjectException)
    }

    void "reasonable error when supplying null as a dependency notation"() {
        when:
        dependencyConstraintHandler."$TEST_CONF_NAME"(null)

        then:
        1 * dependencyFactory.createDependencyConstraint(null)
    }

    void "creates and adds a dependency constraint using a provider"() {
        when:
        dependencyConstraintHandler.add(TEST_CONF_NAME, TestUtil.providerFactory().provider { "someNotation" })

        then:
        1 * configurationContainer.getByName(TEST_CONF_NAME) >> configuration
        1 * configuration.getDependencyConstraints() >> dependencyConstraintSet
        1 * dependencyConstraintSet.addLater(_ as Provider)
        0 * _
    }
}
