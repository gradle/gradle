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

package org.gradle.plugin.repository

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.plugin.PluginBuilder

@LeaksFileHandles
class ResolvingWithPluginManagementSpec extends AbstractDependencyResolutionTest {
    private static final String MAVEN = 'maven'
    private static final String IVY = 'ivy'

    private enum PathType {
        ABSOLUTE, RELATIVE
    }

    private publishTestPlugin(String repoType) {
        def pluginBuilder = new PluginBuilder(testDirectory.file("plugin"))

        def message = "from plugin"
        def taskName = "pluginTask"

        pluginBuilder.addPluginWithPrintlnTask(taskName, message, "org.example.plugin")

        if (repoType == IVY) {
            pluginBuilder.publishAs("org.example.plugin:plugin:1.0", ivyRepo, executer)
        } else if (repoType == MAVEN) {
            pluginBuilder.publishAs("org.example.plugin:plugin:1.0", mavenRepo, executer)
        }
    }

    private String useCustomRepository(String repoType, PathType pathType, String resolutionStrategy = "") {
        def repoUrl = buildRepoPath(repoType, pathType)
        settingsFile << """
          pluginManagement {
            $resolutionStrategy
            repositories {
                ${repoType} {
                    url "${repoUrl}"
                }
            }
          }
        """
        return repoUrl
    }

    private String buildRepoPath(String repoType, PathType pathType) {
        def repoUrl = 'Nothing'
        if (repoType == MAVEN) {
            repoUrl = PathType.ABSOLUTE.equals(pathType) ? mavenRepo.uri : mavenRepo.getRootDir().name
        } else if (repoType == IVY) {
            repoUrl = PathType.ABSOLUTE.equals(pathType) ? ivyRepo.uri : ivyRepo.getRootDir().name
        }
        return repoUrl
    }

    def 'setting different version in resolutionStrategy will affect plugin choice'() {
        given:
        publishTestPlugin(MAVEN)
        buildScript """
          plugins {
              id "org.example.plugin" version '1000'
          }
          plugins.withType(org.gradle.test.TestPlugin) {
            println "I'm here"
          }
        """

        and:
        useCustomRepository(MAVEN, PathType.ABSOLUTE, """
            resolutionStrategy.eachPlugin {
                if(requested.id.name == 'plugin') {
                    useTarget {
                        version = '1.0'
                    }
                }
            }
        """)

        when:
        succeeds("pluginTask")

        then:
        output.contains("I'm here")
    }

    def 'when no version is specified, resolution fails'() {
        given:
        publishTestPlugin(MAVEN)
        buildScript """
          plugins {
              id "org.example.plugin" version '1.2'
          }
          plugins.withType(org.gradle.test.TestPlugin) {
            println "I'm here"
          }
        """

        and:
        useCustomRepository(MAVEN, PathType.ABSOLUTE, """
            resolutionStrategy.eachPlugin {
                if(requested.id.name == 'plugin') {
                    useTarget {
                        version = null
                    }
                }
            }
        """)

        when:
        fails("pluginTask")

        then:
        errorOutput.contains("Plugin [id: 'org.example.plugin']")
    }

    def 'when invalid version is specified, resolution fails'() {
        given:
        publishTestPlugin(MAVEN)
        buildScript """
          plugins {
              id "org.example.plugin" version '1.2'
          }
          plugins.withType(org.gradle.test.TestPlugin) {
            println "I'm here"
          }
        """

        and:
        useCustomRepository(MAVEN, PathType.ABSOLUTE, """
            resolutionStrategy.eachPlugin {
                if(requested.id.name == 'plugin') {
                    useTarget {
                        version = "+"
                    }
                }
            }
        """)

        when:
        fails("pluginTask")

        then:
        errorOutput.contains("Plugin [id: 'org.example.plugin', version: '+']")
    }

    def 'when invalid artifact version is specified, resolution fails'() {
        given:
        publishTestPlugin(MAVEN)
        buildScript """
          plugins {
              id "org.example.plugin" version '1.2'
          }
          plugins.withType(org.gradle.test.TestPlugin) {
            println "I'm here"
          }
        """

        and:
        useCustomRepository(MAVEN, PathType.ABSOLUTE, """
            resolutionStrategy.eachPlugin {
                if(requested.id.name == 'plugin') {
                    useTarget {
                        artifact = "org.example.plugin:plugin:+"
                    }
                }
            }
        """)

        when:
        fails("pluginTask")

        then:
        errorOutput.contains("Plugin [id: 'org.example.plugin', version: '1.2', artifact: 'org.example.plugin:plugin:+']")
    }

    def 'can specify an artifact to use'() {
        given:
        publishTestPlugin(MAVEN)
        buildScript """
          plugins {
              id "org.example.plugin"
          }
          plugins.withType(org.gradle.test.TestPlugin) {
            println "I'm here"
          }
        """

        and:
        useCustomRepository(MAVEN, PathType.ABSOLUTE, """
            resolutionStrategy.eachPlugin {
                if(requested.id.name == 'plugin') {
                    useTarget {
                        artifact = 'org.example.plugin:plugin:1.0'
                    }
                }
            }
        """)

        when:
        succeeds "pluginTask"

        then:
        output.contains("I'm here")
    }

    def "Can specify repo in init script."() {
        given:
        publishTestPlugin(MAVEN)
        buildScript """
           plugins {
             id "org.example.plugin" version "1.0"
           }
        """

        and:
        def initScript = file('definePluginRepo.gradle')
        initScript << """
          settingsEvaluated { settings ->
              settings.pluginManagement {
                repositories {
                    maven {
                      url "${mavenRepo.uri}"
                    }
                }
              }
          }
        """
        args('-I', initScript.absolutePath)

        when:
        succeeds('pluginTask')

        then:
        output.contains('from plugin')
    }

    def "Plugin portal resolver does not support custom artifacts"() {
        given:
        buildScript """
            plugins {
                id "org.gradle.hello-world" version "0.2" //exists in the plugin portal
            }
        """

        when:
        settingsFile << """
            pluginManagement {
                resolutionStrategy {
                    eachPlugin {
                        useTarget {
                            artifact = "foo:bar:1.0"
                        }
                    }
                }
                repositories {
                    gradlePluginPortal()
                }
            }
        """

        then:
        fails("helloWorld")

        then:
        errorOutput.contains("Plugin [id: 'org.gradle.hello-world', version: '0.2', artifact: 'foo:bar:1.0']")
        errorOutput.contains("explicit artifact coordinates are not supported")
    }
}
