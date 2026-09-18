/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.fixtures

import org.gradle.integtests.fixtures.RepoScriptBlockUtil.ExtraRepository
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.plugin.PluginBuilder

class ExtraRepositoriesIntegrationTest extends AbstractIntegrationSpec {

    TestFile emptyRepo

    def setup() {
        emptyRepo = file("empty-repo").createDir()
        def extraRepository = new ExtraRepository("extra", mavenRepo.uri.toString(), [/org\.example(\..+)?/])
        def initScript = file("extra-repositories.init.gradle") << RepoScriptBlockUtil.extraRepositoriesInitScript([extraRepository])
        executer.beforeExecute {
            usingInitScript(initScript)
        }
    }

    def "extra repository is used for plugins, build scripts and dependencies although the build declares other repositories"() {
        given:
        mavenRepo.module("org.example", "lib", "1.0").publish()
        def pluginBuilder = new PluginBuilder(file("plugin"))
        pluginBuilder.addPluginWithPrintlnTask("pluginTask", "from plugin", "org.example.plugin")
        pluginBuilder.publishAs("org.example.plugin:plugin:1.0", mavenRepo, executer)

        settingsFile << """
            pluginManagement {
                repositories {
                    maven { url = uri("${emptyRepo.toURI()}") }
                }
            }
            dependencyResolutionManagement {
                repositories {
                    maven { url = uri("${emptyRepo.toURI()}") }
                }
            }
            include("sub")
        """
        buildFile << """
            buildscript {
                repositories {
                    maven { url = uri("${emptyRepo.toURI()}") }
                }
                dependencies {
                    classpath("org.example:lib:1.0")
                }
            }
            plugins {
                id("org.example.plugin") version "1.0"
            }
            $resolveTask
        """
        file("sub/build.gradle") << """
            repositories {
                maven { url = uri("${emptyRepo.toURI()}") }
            }
            $resolveTask
        """

        when:
        succeeds("pluginTask", "resolve")

        then:
        outputContains("from plugin")
        output.count("resolved: [lib-1.0.jar]") == 2
    }

    def "extra repository is not added to a project that declares no repositories"() {
        given:
        mavenRepo.module("org.example", "lib", "1.0").publish()
        buildFile << resolveTask

        when:
        fails("resolve")

        then:
        failure.assertHasCause("Cannot resolve external dependency org.example:lib:1.0 because no repositories are defined.")
    }

    def "plugin portal is not consulted when the build declares plugin repositories"() {
        given:
        settingsFile << """
            pluginManagement {
                repositories {
                    maven { url = uri("${emptyRepo.toURI()}") }
                }
            }
        """
        buildFile << """
            plugins {
                id("org.example.missing") version "1.0"
            }
        """

        when:
        fails("help")

        then:
        failure.assertHasDescription("Plugin [id: 'org.example.missing', version: '1.0'] was not found in any of the following sources:")
        failure.assertThatDescription(org.hamcrest.CoreMatchers.containsString("maven(${emptyRepo.toURI()})"))
        failure.assertThatDescription(org.hamcrest.CoreMatchers.containsString("extra(${mavenRepo.uri})"))
        failure.assertThatDescription(org.hamcrest.CoreMatchers.not(org.hamcrest.CoreMatchers.containsString("Gradle Central Plugin Repository")))
    }

    private static String getResolveTask() {
        """
            configurations {
                deps
            }
            dependencies {
                deps "org.example:lib:1.0"
            }
            tasks.register("resolve") {
                def files = configurations.deps.incoming.files
                doLast {
                    println("resolved: " + files*.name)
                }
            }
        """
    }
}
