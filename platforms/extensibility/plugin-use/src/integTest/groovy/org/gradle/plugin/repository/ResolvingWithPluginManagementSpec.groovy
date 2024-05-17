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
import org.gradle.test.fixtures.Repository
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.ivy.IvyFileRepository
import org.gradle.test.fixtures.ivy.IvyRepository
import org.gradle.test.fixtures.maven.MavenRepository
import org.gradle.test.fixtures.plugin.PluginBuilder

@LeaksFileHandles
class ResolvingWithPluginManagementSpec extends AbstractDependencyResolutionTest {

    private publishTestPlugin() {
        publishTestPlugin(mavenRepo)
    }

    private publishTestPlugin(Repository repository) {
        def pluginBuilder = new PluginBuilder(testDirectory.file("plugin"))

        def message = "from plugin"
        def taskName = "pluginTask"

        pluginBuilder.addPluginWithPrintlnTask(taskName, message, "org.example.plugin")
        if (repository instanceof MavenRepository) {
            pluginBuilder.publishAs("org.example.plugin:plugin:1.0", repository, executer)
        } else if (repository instanceof IvyRepository) {
            pluginBuilder.publishAs("org.example.plugin:plugin:1.0", repository, executer)
        }
    }

    private void useCustomRepository(String resolutionStrategy = "") {
        settingsFile << """
          pluginManagement {
            $resolutionStrategy
            repositories {
                maven {
                    url "${mavenRepo.uri}"
                }
            }
          }
        """
    }

    def 'setting different version in resolutionStrategy will affect plugin choice'() {
        given:
        publishTestPlugin()
        buildScript """
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
                    useVersion('1.0')
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
        publishTestPlugin()
        buildScript """
          plugins {
              id "org.example.plugin" version '1.2'
          }
          plugins.withType(org.gradle.test.TestPlugin) {
            println "I'm here"
          }
        """

        and:
        useCustomRepository("""
            resolutionStrategy.eachPlugin {
                if(requested.id.name == 'plugin') {
                    useVersion(null)
                }
            }
        """)

        when:
        fails("pluginTask")

        then:
        failure.assertHasDescription("Plugin [id: 'org.example.plugin'] was not found")
    }

    def 'when invalid version is specified, resolution fails'() {
        given:
        publishTestPlugin()
        buildScript """
          plugins {
              id "org.example.plugin" version '1.2'
          }
          plugins.withType(org.gradle.test.TestPlugin) {
            println "I'm here"
          }
        """

        and:
        useCustomRepository("""
            resolutionStrategy.eachPlugin {
                if(requested.id.name == 'plugin') {
                    useVersion("x")
                }
            }
        """)

        when:
        fails("pluginTask")

        then:
        failure.assertHasDescription("Plugin [id: 'org.example.plugin', version: 'x'] was not found")
    }

    def 'when version range is specified, resolution succeeds'() {
        given:
        publishTestPlugin()
        buildScript """
          plugins {
              id "org.example.plugin" version '1.2'
          }
          plugins.withType(org.gradle.test.TestPlugin) {
            println "I'm here"
          }
        """

        and:
        useCustomRepository("""
            resolutionStrategy.eachPlugin {
                if(requested.id.name == 'plugin') {
                    useVersion("1.+")
                }
            }
        """)

        when:
        succeeds("pluginTask")

        then:
        output.contains("I'm here")
    }

    def 'when invalid artifact version is specified, resolution fails'() {
        given:
        publishTestPlugin()
        buildScript """
          plugins {
              id "org.example.plugin" version '1.2'
          }
          plugins.withType(org.gradle.test.TestPlugin) {
            println "I'm here"
          }
        """

        and:
        useCustomRepository("""
            resolutionStrategy.eachPlugin {
                if(requested.id.name == 'plugin') {
                    useModule("org.example.plugin:plugin:x")
                }
            }
        """)

        when:
        fails("pluginTask")

        then:
        failure.assertHasDescription("Plugin [id: 'org.example.plugin', version: '1.2', artifact: 'org.example.plugin:plugin:x'] was not found")
    }

    def 'when artifact version range is specified, resolution succeeds'() {
        given:
        publishTestPlugin()
        buildScript """
          plugins {
              id "org.example.plugin" version '1.2'
          }
          plugins.withType(org.gradle.test.TestPlugin) {
            println "I'm here"
          }
        """

        and:
        useCustomRepository("""
            resolutionStrategy.eachPlugin {
                if(requested.id.name == 'plugin') {
                    useModule("org.example.plugin:plugin:1.+")
                }
            }
        """)

        when:
        succeeds("pluginTask")

        then:
        output.contains("I'm here")
    }

    def 'can specify an artifact to use'() {
        given:
        publishTestPlugin()
        buildScript """
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
                    useModule('org.example.plugin:plugin:1.0')
                }
            }
        """)

        when:
        succeeds "pluginTask"

        then:
        output.contains("I'm here")
    }

    def 'rules are executed in declaration order'() {
        given:
        publishTestPlugin()
        buildScript """
          plugins {
              id "org.example.plugin"
          }
          plugins.withType(org.gradle.test.TestPlugin) {
            println "I'm here"
          }
        """

        and:
        useCustomRepository("""
            resolutionStrategy {
                eachPlugin {
                    useModule('not:here:1.0')
                }
                eachPlugin {
                    if(requested.id.name == 'plugin') {
                        useModule('org.example.plugin:plugin:1.0')
                    }
                }
            }
        """)

        when:
        succeeds "pluginTask"

        then:
        output.contains("I'm here")
    }

    def 'Build fails when a rule throws an exception'() {
        given:
        publishTestPlugin()
        buildScript """
          plugins {
              id "org.example.plugin"
          }
        """

        and:
        useCustomRepository("""
            resolutionStrategy {
                eachPlugin {
                    throw new Exception("Boom")
                }
            }
        """)

        when:
        fails "help"

        then:
        failureCauseContains("Boom")
    }

    def "Can specify repo in init script."() {
        given:
        publishTestPlugin()
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

    def "Can't modify rules after projects have been loaded"() {
        given:
        def initScript = file('definePluginRepo.gradle')
        initScript << """
          Settings mySettings
          settingsEvaluated { settings ->
              mySettings = settings
          }
          projectsLoaded {
            mySettings.pluginManagement.resolutionStrategy.eachPlugin {}
          }
        """
        args('-I', initScript.absolutePath)

        when:
        fails('help')

        then:
        failureDescriptionContains("Cannot change the plugin resolution strategy after projects have been loaded.")
    }

    def "fails build for unresolvable custom artifact"() {
        given:
        buildScript helloWorldPlugin('0.2')

        settingsFile << """
            pluginManagement {
                resolutionStrategy {
                    eachPlugin {
                        useModule("foo:bar:1.0")
                    }
                }
                repositories {
                    gradlePluginPortal()
                }
            }
        """

        when:
        fails("helloWorld")

        then:
        failureDescriptionContains("could not resolve plugin artifact 'foo:bar:1.0'")
    }

    def "succeeds build for resolvable custom artifact"() {
        given:
        buildScript helloWorldPlugin('0.2')

        settingsFile << """
            pluginManagement {
                resolutionStrategy {
                    eachPlugin {
                        useModule("org.gradle:gradle-hello-world-plugin:0.1")
                    }
                }
                repositories {
                    gradlePluginPortal()
                }
            }
        """

        when:
        succeeds("helloWorld")

        then:
        output.contains("Hello World!")
    }

    def "Able to specify ivy resolution patterns"() {
        given:
        def repo = new IvyFileRepository(file("ivy-repo"), true, '[organisation]/[module]/[revision]', '[module]-[revision].ivy', '[artifact]-[revision](-[classifier]).[ext]')
        publishTestPlugin(repo)
        buildScript """
            plugins {
              id "org.example.plugin" version '1.0'
          }
        """

        and:
        settingsFile << """
            pluginManagement {
                repositories {
                    ivy {
                        url "${repo.uri}"
                        patternLayout {
                            ivy '[organisation]/[module]/[revision]/[module]-[revision].ivy'
                            artifact '[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]'
                            m2compatible true
                        }
                    }
                }
            }
        """

        when:
        succeeds('pluginTask')

        then:
        output.contains("from plugin")
    }

    static String helloWorldPlugin(String version) {
        """
            plugins {
                id "org.gradle.hello-world" version "$version" //exists in the plugin portal
            }
        """
    }
}
