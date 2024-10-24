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

package org.gradle.plugin.repository

import com.google.common.base.Splitter
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.Repository
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue
import spock.lang.Shared

@LeaksFileHandles
class ResolvingFromMultipleCustomPluginRepositorySpec extends AbstractDependencyResolutionTest {
    public static final String MAVEN = 'maven'
    public static final String IVY = 'ivy'

    @Shared
    String pluginA = "org.example.pluginA"
    @Shared
    String pluginAB = "org.example.pluginAB"

    Repository repoA
    Repository repoB

    private def publishPlugins(String repoType) {
        if (repoType == IVY) {
            repoA = ivyRepo("ivyA")
            repoB = ivyRepo("ivyB")

            publishPlugin(pluginA, repoA)
            publishPlugin(pluginAB, repoA)
            publishPlugin(pluginAB, repoB)
        } else if (repoType == MAVEN) {
            repoA = mavenRepo("repoA")
            repoB = mavenRepo("repoB")

            publishPlugin(pluginA, repoA)
            publishPlugin(pluginAB, repoA)
            publishPlugin(pluginAB, repoB)
        }
    }

    private def publishPlugin(String pluginId, Repository repository) {
        def pluginBuilder = new PluginBuilder(testDirectory.file(pluginId + repository.hashCode()))
        def idSegments = Splitter.on('.').split(pluginId)
        def coordinates = [idSegments.dropRight(1).join('.'), idSegments.last(), "1.0"].join(':')

        def message = "from ${idSegments.last()} fetched from ${repository.uri}/"
        def taskName = idSegments.last()
        pluginBuilder.addPluginWithPrintlnTask(taskName, message, pluginId, idSegments.last().capitalize())
        pluginBuilder.publishAs(coordinates, repository, executer)
    }

    private def use(Repository... repositories) {
        settingsFile << """
            pluginManagement {
                repositories {
                    ${repositories.collect {
            if (it instanceof MavenFileRepository) {
                "maven { url = '${it.uri}' }"
            } else {
                "ivy { url = '${it.uri}' }"
            }
        }.join('\n')}
                }
            }
        """
    }

    def "#repoType repositories are queried in declaration order"() {
        given:
        publishPlugins(repoType)
        buildFile """
          plugins {
              id "$pluginAB" version "1.0"
          }
        """

        when:
        use(repoA, repoB)

        then:
        succeeds("pluginAB")
        output.contains("fetched from $repoA.uri")

        where:
        repoType << [IVY, MAVEN]
    }

    def "Tries next #repoType repository if first didn't match"() {
        given:
        publishPlugins(repoType)
        buildFile """
          plugins {
              id "$pluginA" version "1.0"
          }
        """

        when:
        use(repoB, repoA)

        then:
        succeeds("pluginA")
        output.contains("fetched from $repoA.uri")

        where:
        repoType << [IVY, MAVEN]
    }

    def "Order of plugin requests does not affect order of #repoType repositories queried"() {
        given:
        publishPlugins(repoType)
        buildFile """
          plugins {
              id "$pluginA" version "1.0"
              id "$pluginAB" version "1.0"
          }
        """

        when:
        use(repoB, repoA)

        then:
        succeeds("pluginAB")
        output.contains("fetched from $repoB.uri")

        where:
        repoType << [IVY, MAVEN]
    }

    def "Resolution failures for #repoType are reported in declaration order"() {
        given:
        publishPlugins(repoType)
        buildFile """
          plugins {
              id "org.example.foo" version "1.1"
          }
        """

        and:
        use(repoA, repoB)

        when:
        fails("pluginTask")

        then:
        failure.assertHasDescription("""
            Plugin [id: 'org.example.foo', version: '1.1'] was not found in any of the following sources:

            - Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
            - Included Builds (No included builds contain this plugin)
            - Plugin Repositories (could not resolve plugin artifact 'org.example.foo:org.example.foo.gradle.plugin:1.1')
              Searched in the following repositories:
                ${repoType}(${repoA.uri})
                ${repoType}2(${repoB.uri})
        """.stripIndent().trim())

        where:
        repoType << [IVY, MAVEN]
    }

    def "Does not fall through to plugin portal if custom #repoType repos are defined"(String repoType) {
        given:
        publishPlugins(repoType)
        buildFile """
            plugins {
                id "org.gradle.hello-world" version "0.2" //exits in the plugin portal
            }
        """
        use(repoA, repoB)

        when:
        fails("helloWorld")

        then:
        failure.assertThatDescription(containsNormalizedString("""
            - Plugin Repositories (could not resolve plugin artifact 'org.gradle.hello-world:org.gradle.hello-world.gradle.plugin:0.2')
              Searched in the following repositories:
                ${repoType}(${repoA.uri})
                ${repoType}2(${repoB.uri})
        """.stripIndent().trim()))

        where:
        repoType << [IVY, MAVEN]
    }

    @Requires(UnitTestPreconditions.Online)
    def "Can opt-in to plugin portal"() {
        given:
        publishPlugins(MAVEN)
        requireOwnGradleUserHomeDir()
        buildFile """
            plugins {
                id "org.gradle.hello-world" version "0.2" //exists in the plugin portal
            }
        """

        when:
        settingsFile << """
            pluginManagement {
                repositories {
                    maven {url = "${repoA.uri}" }
                    gradlePluginPortal()
                }
            }
        """

        then:
        succeeds("helloWorld")
    }

    @Issue("GRADLE-3502")
    @Requires(UnitTestPreconditions.Online)
    def "Plugin Portal provides transitive dependencies for other plugins"() {
        given:
        publishPlugins(MAVEN)
        requireOwnGradleUserHomeDir()
        buildFile """
            //this simulates pluginA having a dependency on the hello world plugin
            buildscript {
                dependencies {
                    classpath "org.gradle:gradle-hello-world-plugin:0.2"
                }
            }
            plugins {
              id "$pluginA" version "1.0"
            }
            apply plugin: "org.gradle.hello-world"
        """

        when:
        settingsFile << """
            pluginManagement {
                repositories {
                    maven {url = "${repoA.uri}" }
                    gradlePluginPortal()
                }
            }
        """

        then:
        succeeds("helloWorld")
    }

    @Issue("gradle/gradle#3210")
    def "all plugin repositories are considered when resolving plugins transitive dependencies"() {
        given:
        requireOwnGradleUserHomeDir()

        and:
        repoA = mavenRepo("maven-repo")
        repoB = ivyRepo("ivy-repo")

        and:
        def abModule = publishPlugin(pluginAB, repoB).pluginModule
        (publishPlugin(pluginA, repoA).pluginModule as MavenModule)
            .dependsOn(abModule.group, abModule.module, abModule.version)
            .publishPom()

        and:
        use(repoB, repoA)

        and:
        buildFile << """
            plugins {
                id "$pluginA" version "1.0"
            }
        """

        when:
        succeeds "buildEnvironment"

        then:
        output.contains("org.example:pluginA:1.0")
        output.contains("org.example:pluginAB:1.0")
    }
}
