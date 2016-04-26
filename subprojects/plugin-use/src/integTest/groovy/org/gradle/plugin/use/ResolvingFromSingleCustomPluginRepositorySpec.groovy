/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.use

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@LeaksFileHandles
class ResolvingFromSingleCustomPluginRepositorySpec extends AbstractDependencyResolutionTest {

    private publishTestPlugin() {
        def pluginBuilder = new PluginBuilder(testDirectory.file("plugin"))

        // The module which holds the plugin implementation.
        def module = mavenRepo.module("org.example.plugin", "plugin", "1.0")
        def artifactFile = module.artifact([:]).artifactFile
        module.publish()

        // The marker module which depends on the plugin implementation module.
        def marker = mavenRepo.module("org.example.plugin", "org.example.plugin", "1.1")
        marker.dependsOn(module)
        marker.publish()

        def message = "from plugin"
        def taskName = "pluginTask"
        pluginBuilder.addPluginWithPrintlnTask(taskName, message, "org.example.plugin")
        pluginBuilder.publishTo(executer, artifactFile)
    }

    def setup() {
        publishTestPlugin()
    }

    def useAbsoluteCustomRepository() {
        settingsFile << """
          pluginRepositories {
              maven {
                  url "${mavenRepo.uri}"
              }
          }
        """
    }

    def useRelativeCustomRepository() {
        settingsFile << """
          pluginRepositories {
              maven {
                  url "${mavenRepo.rootDir.name}"
              }
          }
        """
    }

    def "can resolve plugin from absolute maven-repo"() {
        given:
        buildScript """
          plugins {
              id "org.example.plugin" version "1.1"
          }
        """

        and:
        useAbsoluteCustomRepository()

        when:
        succeeds("pluginTask")

        then:
        output.contains("from plugin")
    }

    def "can resolve plugins even if buildscript block contains wrong repo with same name"() {
        given:
        buildScript """
          buildscript {
            repositories {
                maven {
                    url '${new MavenFileRepository(file("other-repo")).uri}'
                }
            }
          }
          plugins {
              id "org.example.plugin" version "1.1"
          }
        """

        and:
        useAbsoluteCustomRepository()

        when:
        succeeds("pluginTask")

        then:
        output.contains("from plugin")
    }

    def "can resolve plugin from relative maven-repo"() {
        given:
        buildScript """
          plugins {
              id "org.example.plugin" version "1.1"
          }
        """

        and:
        useRelativeCustomRepository()

        when:
        succeeds("pluginTask")

        then:
        output.contains("from plugin")
    }

    def "can access classes from plugin from maven-repo"() {
        given:
        buildScript """
          plugins {
              id "org.example.plugin" version "1.1"
          }
          plugins.withType(org.gradle.test.TestPlugin) {
            println "I'm here"
          }
        """

        and:
        useAbsoluteCustomRepository()

        when:
        succeeds("pluginTask")

        then:
        output.contains("I'm here")
    }

    def "custom repository is not mentioned in plugin resolution errors if none is defined"() {
        given:
        buildScript """
          plugins {
              id "org.example.plugin"
          }
        """

        when:
        fails("pluginTask")

        then:
        !failure.output.contains("maven")
    }

    def "Fails gracefully if a plugin is not found"() {
        given:
        buildScript """
          plugins {
              id "org.example.foo" version "1.1"
          }
        """

        and:
        useAbsoluteCustomRepository()

        when:
        fails("pluginTask")

        then:
        failure.assertHasDescription("""Plugin [id: 'org.example.foo', version: '1.1'] was not found in any of the following sources:

- Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
- maven (Could not resolve plugin artifact 'org.example.foo:org.example.foo:1.1')
- Gradle Central Plugin Repository (no 'org.example.foo' plugin available - see https://plugins.gradle.org for available plugins)"""
        )
    }

    def "Works with subprojects and relative repo specification."() {
        given:
        def subprojectScript = file("subproject/build.gradle")
        subprojectScript << """
          plugins {
              id "org.example.plugin" version "1.1"
          }
        """

        and:
        useRelativeCustomRepository()

        and:
        settingsFile << """
          include 'subproject'
        """

        expect:
        succeeds("subproject:pluginTask")
    }

    @NotYetImplemented
    def "Can specify repo in init script."() {
        given:
        buildScript """
           plugins {
             id "org.example.plugin" version "1.1"
           }
        """

        and:
        def initScript = file('definePluginRepo.gradle')
        initScript << """
          pluginRepositories {
            maven {
              url "${mavenRepo.uri}"
            }
          }
        """
        args('-I', initScript.absolutePath)

        when:
        succeeds('pluginTask')

        then:
        output.contains('from plugin')
    }

    @Requires(TestPrecondition.ONLINE)
    def "Falls through to Plugin Portal if not found in custom repository"() {
        given:
        requireOwnGradleUserHomeDir()
        buildScript """
            plugins {
                id "org.gradle.hello-world" version "0.2"
            }
        """

        and:
        useAbsoluteCustomRepository()

        expect:
        succeeds("helloWorld")
    }
}
