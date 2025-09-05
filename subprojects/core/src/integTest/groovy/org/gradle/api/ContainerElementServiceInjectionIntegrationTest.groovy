/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api

import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.process.ExecOperations
import org.gradle.util.internal.ToBeImplemented

class ContainerElementServiceInjectionIntegrationTest extends AbstractIntegrationSpec {
    def "instantiated Named does not interfere with instantiating other objects"() {
        when:
        buildFile """
            def c = project.container(Named)
            c.create("foo")
        """
        then:
        // executer.expectDocumentedDeprecationWarning("The Project.container(Class) method has been deprecated. This is scheduled to be removed in Gradle 10. Please use the objects.domainObjectContainer(Class) method instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#project_container_methods")
        succeeds()
        when:
        buildFile """
            abstract class Element implements Named {
                @Inject
                Element() { }
            }
            def cc = project.objects.domainObjectContainer(Element)
            cc.create("foo")
        """
        then:
        // executer.expectDocumentedDeprecationWarning("The Project.container(Class) method has been deprecated. This is scheduled to be removed in Gradle 10. Please use the objects.domainObjectContainer(Class) method instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#project_container_methods")
        succeeds()
    }

    // Document current behaviour
    def "container element does not require @Inject when created by project.container"() {
        buildFile  """
            class Bean {
                String name

                Bean(String name) {
                    this.name = name

                    // is not generated
                    assert getClass() == Bean
                }
            }

            def container = project.container(Bean)
            container.create("one") {
                assert name == "one"
            }
        """

        expect:
        // https://github.com/gradle/gradle/issues/34693
        // executer.expectDocumentedDeprecationWarning("The Project.container(Class) method has been deprecated. This is scheduled to be removed in Gradle 10. Please use the objects.domainObjectContainer(Class) method instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#project_container_methods")
        succeeds()
    }

    @ToBeImplemented("https://github.com/gradle/gradle/issues/34693")
    def "project.container(Class) is deprecated"() {
        buildFile  """
            def container = project.container(Named)
            container.create("one") {
                assert name == "one"
            }
        """

        expect:
        // executer.expectDocumentedDeprecationWarning("The Project.container(Class) method has been deprecated. This is scheduled to be removed in Gradle 10. Please use the objects.domainObjectContainer(Class) method instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#project_container_methods")
        succeeds()
    }

    def "project.container(Class,Closure) is deprecated"() {
        buildFile  """
            def container = project.container(Named, { name -> objects.named(Named, name) })
            container.create("one") {
                assert name == "one"
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The Project.container(Class, Closure) method has been deprecated. This is scheduled to be removed in Gradle 10. Please use the objects.domainObjectContainer(Class, NamedDomainObjectFactory) method instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#project_container_methods")
        succeeds()
    }

    @ToBeImplemented("https://github.com/gradle/gradle/issues/34693")
    def "project.container(Class,NamedDomainObjectFactory) is deprecated"() {
        buildFile  """
            def container = project.container(Named, new NamedDomainObjectFactory<Named>() {
                @Override
                Named create(String name) {
                    return objects.named(Named, name)
                }
            })
            container.create("one") {
                assert name == "one"
            }
        """

        expect:
        // executer.expectDocumentedDeprecationWarning("The Project.container(Class, NamedDomainObjectFactory) method has been deprecated. This is scheduled to be removed in Gradle 10. Please use the objects.domainObjectContainer(Class, NamedDomainObjectFactory) method instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#project_container_methods")
        succeeds()
    }

    def "fails when container element requests unknown service"() {
        buildFile """
            interface Unknown { }

            class Bean {
                String name

                @Inject
                Bean(String name, Unknown thing) {
                }
            }

            def container = project.objects.domainObjectContainer(Bean)
            container.create("one") {
                assert name == "one"
            }
        """

        expect:
        fails()
        failure.assertHasCause("Could not create an instance of type Bean.")
        failure.assertHasCause("Unable to determine constructor argument #2: missing parameter of type Unknown, or no service of type Unknown")
    }

    def "container element can receive services through getter method"() {
        buildFile """
            class Bean {
                String name

                @Inject
                Bean(String name) {
                    println(factory != null ? "got it" : "NOT IT")
                    this.name = name

                    assert getClass() != Bean
                    assert (this instanceof org.gradle.api.internal.GeneratedSubclass)
                }

                @Inject
                ObjectFactory getFactory() { null }
            }

            def container = project.objects.domainObjectContainer(Bean)
            container.create("one") {
                assert name == "one"
            }
        """

        expect:
        succeeds()
        outputContains("got it")
    }

    def "container element can receive services through abstract getter method"() {
        buildFile """
            abstract class Bean {
                String name

                @Inject
                Bean(String name) {
                    println(factory != null ? "got it" : "NOT IT")
                    this.name = name

                    assert getClass() != Bean
                    assert (this instanceof org.gradle.api.internal.GeneratedSubclass)
                }

                @Inject
                abstract ObjectFactory getFactory()
            }

            def container = project.objects.domainObjectContainer(Bean)
            container.create("one") {
                assert name == "one"
            }
        """

        expect:
        succeeds()
        outputContains("got it")
    }

    def "service of type #serviceType is available for injection into project container element"() {
        buildFile """
            class Bean {
                String name
                ${serviceType} service

                @Inject
                Bean(String name, ${serviceType} service) {
                    this.name = name
                    this.service = service
                }
            }

            def container = project.objects.domainObjectContainer(Bean)
            container.create("one") {
                assert service != null
            }
        """

        expect:
        succeeds()

        where:
        serviceType << [
            ObjectFactory,
            ProjectLayout,
            ProviderFactory,
            ExecutionEngine,
            FileSystemOperations,
            ExecOperations,
        ].collect { it.name }
    }
}
