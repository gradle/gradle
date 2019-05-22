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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.GradleVersion
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString

class PluginManagementDslSpec extends AbstractIntegrationSpec {

    def "pluginManagement block can be read from settings.gradle"() {
        given:
        settingsFile << """
            pluginManagement {}
        """

        expect:
        succeeds 'help'
    }

    def "pluginManagement block supports defining a maven plugin repository"() {
        given:
        settingsFile << """
            pluginManagement {
                repositories {
                    maven {
                        url "http://repo.internal.net/m2"
                        authentication {
                            basic(BasicAuthentication)
                        }
                        credentials {
                            username = "noob"
                            password = "hunter2"
                        }
                    }
                }
            }
        """

        expect:
        succeeds 'help'
    }

    def "pluginManagement block supports defining a ivy plugin repository"() {
        given:
        settingsFile << """
            pluginManagement {
                repositories {
                    ivy {
                        url "http://repo.internal.net/ivy"
                        authentication {
                            basic(BasicAuthentication)
                        }
                        credentials {
                            username = "noob"
                            password = "hunter2"
                        }
                    }
                }
            }
        """

        expect:
        succeeds 'help'
    }


    def "pluginManagement block supports adding rule based plugin repository"() {
        given:
        settingsFile << """
            pluginManagement {
                resolutionStrategy.eachPlugin {
                    if(requested.id.name == 'noop') {
                        useModule('com.acme:foo:+')
                    }
                }
                repositories { 
                    maven {
                        url "http://repo.internal.net/m2"
                    }
                }
            }
        """
        buildFile << """
            plugins {
                id 'noop'
            }
        """

        expect:
        succeeds 'help'
    }


    def "other blocks can follow the pluginManagement block"() {
        given:
        settingsFile << """
            pluginManagement {}
            rootProject.name = 'rumpelstiltskin'
        """

        expect:
        succeeds 'help'
    }


    def "pluginManagement block is not supported in ProjectScripts"() {
        given:
        buildScript """
            pluginManagement {}
        """

        when:
        fails 'help'

        then:
        failure.assertHasLineNumber(2)
        failure.assertThatCause(containsString("Only Settings scripts can contain a pluginManagement {} block."))
        includesLinkToUserguide()
    }

    def "pluginManagement block is not supported in InitScripts"() {
        given:
        def initScript = file "definePluginRepos.gradle"
        initScript << """
            pluginManagement {}
        """
        args('-I', initScript.absolutePath)

        when:
        fails 'help'

        then:
        failure.assertHasLineNumber(2)
        failure.assertThatCause(containsString("Only Settings scripts can contain a pluginManagement {} block."))
        includesLinkToUserguide()
    }

    def "pluginManagement block must come before imperative blocks in the settings.gradle script"() {
        given:
        settingsFile << """
            rootProject.name = 'rumpelstiltskin'
            pluginManagement {}
        """

        when:
        fails 'help'

        then:
        failure.assertHasLineNumber(3)
        failure.assertThatCause(containsString("The pluginManagement {} block must appear before any other statements in the script."))
        includesLinkToUserguide()
    }

    def "pluginManagement block must come before buildScript blocks in the settings.gradle script"() {
        given:
        settingsFile << """
            buildScript {}
            pluginManagement {}
        """

        when:
        fails 'help'

        then:
        failure.assertHasLineNumber(3)
        failure.assertThatCause(containsString("The pluginManagement {} block must appear before any other statements in the script."))
        includesLinkToUserguide()
    }

    def "Only one pluginManagement block is allowed in each script"() {
        given:
        settingsFile << """
            pluginManagement {}
            pluginManagement {}
        """

        when:
        fails 'help'

        then:
        failure.assertHasLineNumber(3)
        failure.assertThatCause(containsString("At most, one pluginManagement {} block may appear in the script."))
        includesLinkToUserguide()
    }

    def "Can access properties in pluginManagement block"() {
        given:
        settingsFile << """
            pluginManagement {
                repositories {
                    maven {
                        url repoUrl
                    }
                }
            }
        """
        expect:
        succeeds 'help', '-PrepoUrl=some/place'

    }

    def "Can access SettingsScript API in pluginManagement block"() {
        given:
        settingsFile << """
            pluginManagement {
                repositories {
                    maven {
                        url file('bar')
                    }
                }
            }
        """
        expect:
        succeeds 'help'
    }

    def "pluginManagement block supports defining repositories with layouts"() {
        given:
        settingsFile << """
            pluginManagement {
                repositories {
                    ivy {
                        url "http://repo.internal.net/ivy"
                        patternLayout {
                            ivy '[organisation]/[module]/[revision]/[module]-[revision].ivy'
                            artifact '[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]'
                            m2compatible true
                        }
                    }
                    ivy {
                        url "http://repo.internal.net/ivy"
                        layout("maven")
                    }
                    ivy {
                        url "http://repo.internal.net/ivy"
                        layout("ivy")
                    }
                    ivy {
                        url "http://repo.internal.net/ivy"
                        layout("gradle")
                    }
                    ivy {
                        url "http://repo.internal.net/ivy"
                        artifactPattern '[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]'
                        ivyPattern '[organisation]/[module]/[revision]/[module]-[revision].ivy'
                    }
                }
            }
        """

        expect:
        succeeds 'help'
    }

    @Issue("gradle/gradle#3169")
    def "pluginManagement block supports named repositories"() {
        given:
        settingsFile << """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    jcenter()
                    google()
                    mavenCentral()
                    mavenLocal()
                }
            }
        """

        expect:
        succeeds "help"
    }


    void includesLinkToUserguide() {
        failure.assertThatCause(containsString("https://docs.gradle.org/${GradleVersion.current().getVersion()}/userguide/plugins.html#sec:plugin_management"))
    }
}
