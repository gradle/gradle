/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.hamcrest.CoreMatchers.startsWith

//These tests depend on https://plugins.gradle.org
@Requires(UnitTestPreconditions.Online)
@LeaksFileHandles
class DeployedPortalIntegrationSpec extends AbstractIntegrationSpec {

    private final static String HELLO_WORLD_PLUGIN_ID = "org.gradle.hello-world"
    private final static String HELLO_WORLD_PLUGIN_VERSION = "0.2"

    def setup() {
        requireOwnGradleUserHomeDir()
    }

    def "Can access plugin classes when resolved but not applied"() {
        when:
        buildScript """
            plugins {
                id "$HELLO_WORLD_PLUGIN_ID" version "$HELLO_WORLD_PLUGIN_VERSION" apply false
            }

            task customHello(type: org.gradle.plugin.HelloWorldTask)
        """

        then:
        succeeds("customHello")

        and:
        output.contains("Hello World!")

        and:
        fails("helloWorld")
    }

    def "Can apply plugins to subprojects"() {
        when:
        settingsFile << """
            include 'sub'
        """
        buildScript """
            plugins {
                id "$HELLO_WORLD_PLUGIN_ID" version "$HELLO_WORLD_PLUGIN_VERSION" apply false
            }

            subprojects {
                apply plugin: "$HELLO_WORLD_PLUGIN_ID"
            }
        """

        then:
        succeeds("sub:helloWorld")

        and:
        output.contains("Hello World!")

    }

    def "can resolve and apply a plugin from portal"() {
        when:
        buildScript """
            plugins {
                id "$HELLO_WORLD_PLUGIN_ID" version "$HELLO_WORLD_PLUGIN_VERSION"
            }
        """

        then:
        succeeds("helloWorld")

        and:
        output.contains("Hello World!")
    }

    def "resolving a non-existing plugin results in an informative error message"() {
        when:
        buildScript """
            plugins {
                id "org.gradle.non-existing" version "1.0"
            }
        """

        then:
        fails("dependencies")

        and:
        failureDescriptionStartsWith("Plugin [id: 'org.gradle.non-existing', version: '1.0'] was not found in any of the following sources:")
        failureDescriptionContains("""
            - Plugin Repositories (could not resolve plugin artifact 'org.gradle.non-existing:org.gradle.non-existing.gradle.plugin:1.0')
              Searched in the following repositories:
                Gradle Central Plugin Repository
            """.stripIndent().trim())
    }

    def "can resolve and plugin from portal with buildscript notation"() {
        given:
        def helloWorldGroup = 'org.gradle'
        def helloWorldName = 'gradle-hello-world-plugin'

        when:
        buildScript """
            buildscript {
                repositories {
                    gradlePluginPortal()
                }
                dependencies {
                    classpath "$helloWorldGroup:$helloWorldName:$HELLO_WORLD_PLUGIN_VERSION"
                }
            }

            apply plugin: "$HELLO_WORLD_PLUGIN_ID"
        """

        then:
        succeeds("helloWorld")

        and:
        output.contains("Hello World!")
    }

    def "resolution fails if Gradle is in offline mode"() {
        given:
        buildScript """
            plugins {
                id "$HELLO_WORLD_PLUGIN_ID" version "$HELLO_WORLD_PLUGIN_VERSION"
            }
        """

        when:
        args "--offline"
        fails "help"

        then:
        failure.assertThatDescription(startsWith("Plugin [id: '$HELLO_WORLD_PLUGIN_ID', version: '$HELLO_WORLD_PLUGIN_VERSION'] was not found"))
    }

    def "can resolve plugin from portal with repository filters present"() {
        given:
        mavenRepo.module('com.android.tools', 'r8', '1.5.70').publish()

        when:
        buildScript """
            buildscript {
                repositories {
                    exclusiveContent {
                      forRepository {
                        maven {
                          url = "${mavenRepo.uri}"
                          metadataSources {
                            artifact()
                          }
                        }
                      }
                      filter {
                        includeModule("com.android.tools", "r8")
                      }
                    }
                }
            }

            plugins {
                id "$HELLO_WORLD_PLUGIN_ID" version "$HELLO_WORLD_PLUGIN_VERSION"
            }
        """

        then:
        succeeds("helloWorld")

        and:
        output.contains("Hello World!")
    }

    def "resolution fails from portal with repository filters present"() {
        given:
        mavenRepo.module('com.android.tools', 'r8', '1.5.70').publish()

        buildScript """
            buildscript {
                repositories {
                    exclusiveContent {
                        forRepository {
                            ${mavenCentralRepository()}
                        }
                        filter {
                            includeModule("com.android.tools", "r8") // force resolving r8 from wrong repo
                        }
                    }
                    maven {
                        url = "${mavenRepo.uri}"
                        metadataSources {
                            artifact()
                        }
                    }
                }

                dependencies {
                    classpath group: 'com.android.tools', name: 'r8', version: '1.5.70'
                }

            }

            plugins {
                id "$HELLO_WORLD_PLUGIN_ID" version "$HELLO_WORLD_PLUGIN_VERSION"
            }
        """

        expect:
        fails("helloWorld")
        failureCauseContains("Could not find com.android.tools:r8")
    }

    def "can resolve plugin from portal with repository filters and settings plugins"() {
        given:
        mavenRepo.module('com.android.tools', 'r8', '1.5.70').publish()
        when:
        buildFile << """
            buildscript {
              repositories {
                exclusiveContent {
                  forRepository {
                    // For R8/D8 releases
                    maven {
                        url = "${mavenRepo.uri}"
                    }
                  }
                  filter {
                    includeModule("com.android.tools", "r8")
                  }
                }
              }
            }

            plugins {
              id("$HELLO_WORLD_PLUGIN_ID") version "$HELLO_WORLD_PLUGIN_VERSION"
            }
        """
        then:
        succeeds("helloWorld")
        and:
        output.contains("Hello World!")
    }
}
