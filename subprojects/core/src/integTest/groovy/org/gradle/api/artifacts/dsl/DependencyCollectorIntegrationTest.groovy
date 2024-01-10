/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.artifacts.dsl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Tests {@link org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyCollector}.
 */
class DependencyCollectorIntegrationTest extends AbstractIntegrationSpec {
    def "cannot add dependencies after dependency set has been observed"() {
        given:
        buildFile << """
            ${createDependenciesAndConfiguration()}

            dependencies.implementation 'com:foo:1.0'

            assert conf.dependencies*.name == ["foo"]

            dependencies.implementation 'com:bar:1.0'
        """

        expect:
        fails("help")
        failure.assertHasErrorOutput("The value for property 'implementation' property 'dependencies' is final and cannot be changed any further.")
    }

    def "cannot mutate dependencies after dependency set has been observed"() {
        given:
        mavenRepo.module("com", "foo").publish()
        buildFile << """
            ${createDependenciesAndConfiguration()}

            dependencies.implementation 'com:foo:1.0'

            configurations.conf.dependencies.each {
                assert it.name == "foo"
                it.attributes {
                    attribute(org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE, objects.named(Usage, "usage"))
                }
            }
        """

        expect:
        fails("help")
        failure.assertHasErrorOutput("Cannot mutate 'DefaultExternalModuleDependency{group='com', name='foo', version='1.0', configuration='default'}' after it has been finalized.")
    }

    def createDependenciesAndConfiguration() {
        """
            interface MyDependencies extends Dependencies {
                DependencyCollector getImplementation()
            }

            def dependencies = objects.newInstance(MyDependencies)

            def conf = configurations.dependencyScope("conf").get()
            conf.dependencies.addAllLater(dependencies.implementation.dependencies)
        """
    }
}
