/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class ToolingApiResolveIntegrationTest extends AbstractIntegrationSpec {

    def "can resolve tooling API via #configuration"() {
        given:
        def tapiVersion = distribution.getVersion().baseVersion.version
        buildFile << """
            plugins {
                id 'java-library'
            }
            repositories {
                maven { url = '${buildContext.localRepository.toURI()}' }
                ${mavenCentralRepository()}
            }

            configurations {
                customConf {
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, "library"))
                    }
                }
            }

            dependencies {
                implementation 'org.gradle:gradle-tooling-api:${tapiVersion}'
                customConf 'org.gradle:gradle-tooling-api:${tapiVersion}'
            }

            tasks.register('resolve') {
                def configuration = configurations.${configuration}
                doLast {
                    println configuration.files.collect { it.name }
                }
            }
        """

        when:
        succeeds 'resolve'

        then:
        outputContains("[gradle-tooling-api-${tapiVersion}.jar, slf4j-api-2.0.17.jar]")

        where:
        configuration << ['compileClasspath', 'runtimeClasspath', 'customConf']
    }

    @Issue("https://github.com/gradle/gradle/issues/37947")
    def "tooling API slf4j-api dependency does not strictly pin the version"() {
        given:
        def tapiVersion = distribution.getVersion().baseVersion.version
        buildFile << """
            plugins {
                id 'java-library'
            }
            repositories {
                maven { url = '${buildContext.localRepository.toURI()}' }
                ${mavenCentralRepository()}
            }

            dependencies {
                implementation 'org.gradle:gradle-tooling-api:${tapiVersion}'
                implementation('org.slf4j:slf4j-api') {
                    version { strictly '2.0.18' }
                }
            }

            tasks.register('resolve') {
                def runtimeClasspath = configurations.runtimeClasspath
                doLast {
                    println runtimeClasspath.files.collect { it.name }
                }
            }
        """

        when:
        succeeds 'resolve'

        then:
        outputContains("[gradle-tooling-api-${tapiVersion}.jar, slf4j-api-2.0.18.jar]")
    }

    def "can resolve sources variant of tooling API"() {
        given:
        def tapiVersion = distribution.getVersion().baseVersion.version
        buildFile << """
            plugins {
                id 'java-library'
            }
            repositories {
                maven { url = '${buildContext.localRepository.toURI()}' }
                ${mavenCentralRepository()}
            }

            configurations {
                sources {
                    extendsFrom runtimeClasspath
                    canBeConsumed = false
                    visible = false
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
                        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.SOURCES))
                    }
                }
            }

            dependencies {
                implementation "org.gradle:gradle-tooling-api:${tapiVersion}"
            }

            tasks.register('resolve') {
                def sources = configurations.sources
                doLast {
                    println sources.files.collect { it.name }
                }
            }
        """

        when:
        succeeds 'resolve'

        then:
        outputContains("[gradle-tooling-api-${tapiVersion}-sources.jar]")
    }
}
