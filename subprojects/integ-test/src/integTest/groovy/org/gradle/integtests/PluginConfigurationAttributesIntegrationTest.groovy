/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.component.ResolutionFailureHandler

class PluginConfigurationAttributesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << """
            include("producer", "consumer")
            dependencyResolutionManagement {
                ${mavenCentralRepository()}
            }
        """
    }

    def "plugin runtime configuration is not consumable"() {
        given:
        file("producer/build.gradle") << """
            plugins {
                id("$plugin")
            }
        """

        when:
        file("consumer/build.gradle") << """
            plugins {
                id("java-library")
            }
            dependencies {
                implementation(project(path: ":producer", configuration: "$configuration"))
            }
        """
        file("gradle.properties").text = "${ResolutionFailureHandler.FULL_FAILURES_MESSAGE_PROPERTY}=true"

        then:
        fails("test")
        result.assertHasErrorOutput("Selected configuration '$configuration' on 'project :producer' but it can't be used as a project dependency because it isn't intended for consumption by other components")

        where:
        plugin       | configuration
        'antlr'      | 'antlr'
        'codenarc'   | 'codenarc'
        'jacoco'     | 'jacocoAgent'
        'jacoco'     | 'jacocoAnt'
        'pmd'        | 'pmd'
        'checkstyle' | 'checkstyle'
        'scala'      | 'zinc'
        'war'        | 'providedRuntime'
        'war'        | 'providedCompile'
    }

    def "plugin runtime configuration can be extended and consumed without deprecation"() {
        given:
        file("producer/build.gradle") << """
            plugins {
                id("$plugin")
            }
            configurations {
                ${configuration}Consumable {
                    extendsFrom($configuration)
                    assert canBeConsumed
                    canBeResolved = false
                    attributes {
                        attribute(Attribute.of("test", String), "test")
                    }
                }
            }
        """

        when:
        file("consumer/build.gradle") << """
            configurations {
                consumer {
                    canBeConsumed = false
                    assert canBeResolved
                    attributes {
                        attribute(Attribute.of("test", String), "test")
                        ${plugin == 'codenarc' ?
                        """
                        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, Bundling.EXTERNAL)) // to avoid shadowRuntimeElements variant
                        """ : ""
                        }
                    }
                }
            }
            dependencies {
                consumer(project(":producer"))
            }
            tasks.register("resolve") {
                def consumerFiles = configurations.consumer.files
                doLast {
                    consumerFiles.forEach {
                        println(it.name)
                    }
                }
            }
        """

        then:
        succeeds("resolve")

        where:
        plugin       | configuration
        'codenarc'   | 'codenarc'
        'pmd'        | 'pmd'
        'checkstyle' | 'checkstyle'
        'antlr'      | 'antlr'
        'jacoco'     | 'jacocoAgent'
        'jacoco'     | 'jacocoAnt'
        'scala'      | 'zinc'
        'war'        | 'providedRuntime'
        'war'        | 'providedCompile'
    }

}
