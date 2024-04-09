/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.catalog

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder

class VersionCatalogPluginApplicationIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        def pluginBuilder = new PluginBuilder(testDirectory.file("plugin"))

        def message = "from plugin"
        def taskName = "pluginTask"

        pluginBuilder.addPluginWithPrintlnTask(taskName, message, "org.example.plugin")
        pluginBuilder.publishAs("org.example.plugin:plugin:1.0.0", mavenRepo, executer)
    }

    def "can apply a plugin by id and version using version catalog"() {
        given:
        versionCatalogFile """
        [plugins]
        orgExample = "org.example.plugin:1.0.0"
        """
        settingsFile """
            pluginManagement {
                repositories {
                    ${mavenTestRepository()}
                }
            }
            rootProject.name = 'test'
        """
        buildFile """
            plugins {
                alias(libs.plugins.orgExample)
            }
        """

        when:
        succeeds(":pluginTask")

        then:
        output.contains("from plugin")
    }

    def "can apply a plugin by id only using version catalog"() {
        given:
        versionCatalogFile """
        [plugins]
        javaPlugin = { id = "java-library" }
        """
        settingsFile """
            rootProject.name = 'test'
        """
        buildFile """
            plugins {
                alias(libs.plugins.javaPlugin)
            }
        """

        expect:
        succeeds(":compileJava")
    }

    def "can apply a plugin in both root and sub- project using id and version using version catalog"() {
        given:
        versionCatalogFile """
        [plugins]
        orgExample = "org.example.plugin:1.0.0"
        """
        settingsFile """
            pluginManagement {
                repositories {
                    ${mavenTestRepository()}
                }
            }
            rootProject.name = 'test'

            include 'subproject'
        """
        buildFile """
            plugins {
                alias(libs.plugins.orgExample)
            }
        """
        file("subproject/build.gradle") << """
            plugins {
                alias(libs.plugins.orgExample)
            }
        """

        when:
        succeeds(":pluginTask")

        then:
        output.contains("from plugin")

        when:
        succeeds(":subproject:pluginTask")

        then:
        output.contains("from plugin")
    }

    def "can apply a plugin in both root and sub- project using id only using version catalog"() {
        given:
        versionCatalogFile """
        [plugins]
        javaPlugin = { id = "java-library" }
        """
        settingsFile """
            rootProject.name = 'test'

            include 'subproject'
        """
        buildFile """
            plugins {
                alias(libs.plugins.javaPlugin)
            }
        """
        file("subproject/build.gradle") << """
            plugins {
                alias(libs.plugins.javaPlugin)
            }
        """

        expect:
        succeeds(":compileJava")
        succeeds(":subproject:compileJava")
    }
}
