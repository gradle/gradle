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

package org.gradle.plugin.repository

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.plugin.PluginBuilder

@LeaksFileHandles
class ResolvingSnapshotFromPluginRepositorySpec extends AbstractDependencyResolutionTest {

    private publishTestPlugin(String version = "1.0-SNAPSHOT", String message = "from plugin") {
        def pluginBuilder = new PluginBuilder(testDirectory.file("plugin-" + version))

        def taskName = "pluginTask"

        pluginBuilder.addPluginWithPrintlnTask(taskName, message, "org.example.plugin")
        pluginBuilder.publishAs("org.example.plugin:plugin:${version}", mavenRepo, executer)
    }

    private void useCustomRepository(String resolutionStrategy = "") {
        settingsFile << """
          pluginManagement {
            $resolutionStrategy
            repositories {
                maven {
                    url = "${mavenRepo.uri}"
                }
            }
          }
        """
    }

    def 'Can specify snapshot version'() {
        given:
        publishTestPlugin()
        buildFile """
          plugins {
              id "org.example.plugin" version '1.0-SNAPSHOT'
          }
          plugins.withType(org.gradle.test.TestPlugin) {
            println "I'm here"
          }
        """

        and:
        useCustomRepository()

        when:
        succeeds("pluginTask")

        then:
        output.contains("I'm here")
    }

    def 'setting different snapshot version in resolutionStrategy will affect plugin choice'() {
        given:
        publishTestPlugin()
        buildFile """
          plugins {
              id "org.example.plugin" version '1000'
          }
          plugins.withType(org.gradle.test.TestPlugin) {
            println "I'm here"
          }
        """

        and:
        useCustomRepository("""
            resolutionStrategy.eachPlugin {
                if(requested.id.name == 'plugin') {
                    useVersion('1.0-SNAPSHOT')
                }
            }
        """)

        when:
        succeeds("pluginTask")

        then:
        output.contains("I'm here")
    }

    def 'can specify a snapshot artifact to use'() {
        given:
        publishTestPlugin()
        buildFile """
          plugins {
              id "org.example.plugin"
          }
          plugins.withType(org.gradle.test.TestPlugin) {
            println "I'm here"
          }
        """

        and:
        useCustomRepository("""
            resolutionStrategy.eachPlugin {
                if(requested.id.name == 'plugin') {
                    useModule('org.example.plugin:plugin:1.0-SNAPSHOT')
                }
            }
        """)

        when:
        succeeds "pluginTask"

        then:
        output.contains("I'm here")
    }
    def "can use dynamic versions and status to depend on snapshot version"() {
        given:
        publishTestPlugin("1.1-SNAPSHOT", "from 1.1")
        publishTestPlugin("1.2", "from 1.2")
        useCustomRepository()

        buildFile << """
            buildscript {
                configurations.classpath {
                    withDependencies { dependencies ->
                       dependencies.configureEach { dep ->
                            dep.attributes {
                                attribute(Attribute.of("org.gradle.status", String.class), "integration")
                            }
                        }
                    }
                }
            }

            plugins {
                id 'org.example.plugin' version '1.+'
            }

            plugins.withType(org.gradle.test.TestPlugin) {
              println "I'm here"
            }
        """

        when:
        succeeds "pluginTask"

        then:
        output.contains("from 1.1")
        output.contains("I'm here")
    }

}
