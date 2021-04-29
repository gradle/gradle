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

package org.gradle.api.plugins.quality

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CodeQualityPluginConfigurationAttributesTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << """
            include("producer", "consumer")
            dependencyResolutionManagement {
                ${mavenCentralRepository()}
            }
        """
    }

    def "code quality plugin runtime configuration is deprecated for consumption"() {
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
                implementation(project(path: ":producer", configuration: "$plugin"))
            }
        """

        then:
        executer.expectDocumentedDeprecationWarning("The $plugin configuration has been deprecated for consumption. This will fail with an error in Gradle 8.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#code_quality_plugin_configuration_consumption")
        succeeds("test")

        where:
        plugin << ['codenarc', 'pmd', 'checkstyle']
    }

    def "code quality plugin runtime configuration can be extended and consumed without deprecation"() {
        given:
        file("producer/build.gradle") << """
            plugins {
                id("$plugin")
            }
            configurations {
                $plugin {
                    // because currently this is consumable until Gradle 8.0 and adding attributes to it will make it clash with the configuration below
                    canBeConsumed = false
                }
                ${plugin}Consumable {
                    extendsFrom($plugin)
                    canBeConsumed = true
                    canBeResolved = false
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
                        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.JAR))
                        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, Bundling.EXTERNAL))
                        attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment, TargetJvmEnvironment.STANDARD_JVM));
                    }
                }
            }
        """

        when:
        file("consumer/build.gradle") << """
            plugins {
                id("java-library")
            }
            dependencies {
                implementation(project(":producer"))
            }
        """

        then:
        succeeds("test")

        where:
        plugin << ['codenarc', 'pmd', 'checkstyle']
    }

}
