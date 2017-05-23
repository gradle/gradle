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
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Unroll

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
                            "maven { url '${it.uri}' }"
                        } else {
                            "ivy { url '${it.uri}' }"
                        }
                      }.join('\n')}
                }
            }
        """
    }

    @Unroll
    def "#repoType repositories are queried in declaration order"() {
        given:
        publishPlugins(repoType)
        buildScript """
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

    @Unroll
    def "Tries next #repoType repository if first didn't match"() {
        given:
        publishPlugins(repoType)
        buildScript """
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

    @Unroll
    def "Order of plugin requests does not affect order of #repoType repositories queried"() {
        given:
        publishPlugins(repoType)
        buildScript """
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

    @Unroll
    def "Resolution failures for #repoType are reported in declaration order"() {
        given:
        publishPlugins(repoType)
        buildScript """
          plugins {
              id "org.example.foo" version "1.1"
          }
        """

        and:
        use(repoA, repoB)

        when:
        fails("pluginTask")

        then:
        failure.assertHasDescription("""Plugin [id 'org.example.foo' version '1.1'] was not found in any of the following sources:

- Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
- Script Plugins (only script plugin requests are supported by this source)
- ${repoType}(${repoA.uri}) (Could not resolve plugin artifact 'org.example.foo:org.example.foo.gradle.plugin:1.1')
- ${repoType}(${repoB.uri}) (Could not resolve plugin artifact 'org.example.foo:org.example.foo.gradle.plugin:1.1')"""
        )

        where:
        repoType << [IVY, MAVEN]
    }

    @Unroll
    def "Does not fall through to plugin portal if custom #repoType repos are defined"(String repoType) {
        given:
        publishPlugins(repoType)
        buildScript """
            plugins {
                id "org.gradle.hello-world" version "0.2" //exits in the plugin portal
            }
        """
        use(repoA, repoB)

        when:
        fails("helloWorld")

        then:
        failure.assertThatDescription(containsNormalizedString("""
- ${repoType}(${repoA.uri}) (Could not resolve plugin artifact 'org.gradle.hello-world:org.gradle.hello-world.gradle.plugin:0.2')
- ${repoType}(${repoB.uri}) (Could not resolve plugin artifact 'org.gradle.hello-world:org.gradle.hello-world.gradle.plugin:0.2')"""
        ))

        where:
        repoType << [IVY, MAVEN]
    }

    @Requires(TestPrecondition.ONLINE)
    def "Can opt-in to plugin portal"() {
        given:
        publishPlugins(MAVEN)
        requireOwnGradleUserHomeDir()
        buildScript """
            plugins {
                id "org.gradle.hello-world" version "0.2" //exists in the plugin portal
            }
        """

        when:
        settingsFile << """
            pluginManagement {
                repositories {
                    maven {url '${repoA.uri}' }
                    gradlePluginPortal()
                }
            }
        """

        then:
        succeeds("helloWorld")
    }

    @Requires(TestPrecondition.ONLINE)
    @Issue("GRADLE-3502")
    def "Plugin Portal provides transitive dependencies for other plugins"() {
        given:
        publishPlugins(MAVEN)
        requireOwnGradleUserHomeDir()
        buildScript """
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
                    maven {url '${repoA.uri}' }
                    gradlePluginPortal()
                }
            }
        """

        then:
        succeeds("helloWorld")
    }
}
