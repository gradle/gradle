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
import spock.lang.Unroll


class ObjectExtensionServiceInjectionIntegrationTest extends AbstractIntegrationSpec {
    // Document current behaviour
    def "can inject service and configuration as constructor args when constructor not annotated with @Inject"() {
        buildFile """
            class Thing {
                Thing(String a, ObjectFactory objects, int b) {
                    assert a == "a"
                    assert b == 12
                    assert objects != null
                }
            }

            extensions.create("thing", Thing, "a", 12)
        """

        expect:
        succeeds()
    }

    def "can inject service using getter"() {
        buildFile """
            class Thing {
                Thing(String a) {
                }

                @Inject
                ObjectFactory getObjects() { }
            }

            extensions.create("thing", Thing, "a")
            assert thing.objects != null
        """

        expect:
        succeeds()
    }

    def "can inject service using abstract getter"() {
        buildFile """
            abstract class Thing {
                Thing(String a) {
                }

                @Inject
                abstract ObjectFactory getObjects()
            }

            extensions.create("thing", Thing, "a")
            assert thing.objects != null
        """

        expect:
        succeeds()
    }

    def "can use getter injected services from constructor"() {
        buildFile """
            class Thing {
                Thing(String a) {
                    objects.property(String).set(a)
                }

                @Inject
                ObjectFactory getObjects() { }
            }

            extensions.create("thing", Thing, "a")
            assert thing.objects != null
        """

        expect:
        succeeds()
    }

    def "can inject service using getter on interface"() {
        buildFile """
           interface Thing {
                @Inject
                ObjectFactory getObjects()
            }

            extensions.create("thing", Thing)
            assert thing.objects != null
        """

        expect:
        succeeds()
    }

    @Unroll
    def "service of type #serviceType is available for injection into project extension"() {
        buildFile << """
            class Thing {
                ${serviceType} service

                Thing(${serviceType} service) {
                    this.service = service
                }
            }

            extensions.create("thing", Thing)
            assert thing.service != null
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

    @Unroll
    def "service of type #serviceType is available for injection into settings extension"() {
        settingsFile << """
            class Thing {
                ${serviceType} service

                Thing(${serviceType} service) {
                    this.service = service
                }
            }

            extensions.create("thing", Thing)
            assert thing.service != null
        """

        expect:
        succeeds()

        where:
        serviceType << [
            ObjectFactory,
            ProviderFactory,
            FileSystemOperations,
            ExecOperations,
        ].collect { it.name }
    }

    @Unroll
    def "service of type #serviceType is available for injection into gradle object extension"() {
        settingsFile << """
            class Thing {
                ${serviceType} service

                Thing(${serviceType} service) {
                    this.service = service
                }
            }

            gradle.extensions.create("thing", Thing)
            assert gradle.thing.service != null
        """

        expect:
        succeeds()

        where:
        serviceType << [
            ObjectFactory,
            ProviderFactory,
            FileSystemOperations,
            ExecOperations,
        ].collect { it.name }
    }
}
