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

import static org.hamcrest.Matchers.containsString

class PluginRepositoriesDslSpec extends AbstractIntegrationSpec {

    def "pluginRepositories block can be read from settings.gradle"() {
        given:
        settingsFile << """
            pluginRepositories {}
        """

        expect:
        succeeds 'help'
    }

    def "pluginRepositories block supports defining a maven plugin repository"() {
        given:
        settingsFile << """
            pluginRepositories {
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
        """

        expect:
        succeeds 'help'
    }

    def "pluginRepositories block supports defining a ivy plugin repository"() {
        given:
        settingsFile << """
            pluginRepositories {
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
        """

        expect:
        succeeds 'help'
    }

    def "pluginRepositories block supports adding Gradle Plugin Portal"() {
        given:
        settingsFile << """
            pluginRepositories {
                gradlePluginPortal()
            }
        """

        expect:
        succeeds 'help'
    }

    def "Cannot specify Gradle Plugin Portal twice"() {
        given:
        settingsFile << """
            pluginRepositories {
                gradlePluginPortal()
                gradlePluginPortal()
            }
        """

        expect:
        fails 'help'
        failure.assertThatCause(containsString("Cannot add Gradle Plugin Portal more than once"))
    }

    def "other blocks can follow the pluginRepositories block"() {
        given:
        settingsFile << """
            pluginRepositories {}
            rootProject.name = 'rumpelstiltskin'
        """

        expect:
        succeeds 'help'
    }


    def "pluginRepositories block is not supported in ProjectScripts"() {
        given:
        buildScript """
            pluginRepositories {}
        """

        when:
        fails 'help'

        then:
        failure.assertHasLineNumber(2)
        failure.assertThatCause(containsString("Only Settings scripts can contain a pluginRepositories {} block."))
        includesLinkToUserguide()
    }

    def "pluginRepositories block is not supported in InitScripts"() {
        given:
        def initScript = file "definePluginRepos.gradle"
        initScript << """
            pluginRepositories {}
        """
        args('-I', initScript.absolutePath)

        when:
        fails 'help'

        then:
        failure.assertHasLineNumber(2)
        failure.assertThatCause(containsString("Only Settings scripts can contain a pluginRepositories {} block."))
        includesLinkToUserguide()
    }

    def "pluginRepositories block must come before imperative blocks in the settings.gradle script"() {
        given:
        settingsFile << """
            rootProject.name = 'rumpelstiltskin'
            pluginRepositories {}
        """

        when:
        fails 'help'

        then:
        failure.assertHasLineNumber(3)
        failure.assertThatCause(containsString("The pluginRepositories {} block must appear before any other statements in the script."))
        includesLinkToUserguide()
    }

    def "pluginRepositories block must come before buildScript blocks in the settings.gradle script"() {
        given:
        settingsFile << """
            buildScript {}
            pluginRepositories {}
        """

        when:
        fails 'help'

        then:
        failure.assertHasLineNumber(3)
        failure.assertThatCause(containsString("The pluginRepositories {} block must appear before any other statements in the script."))
        includesLinkToUserguide()
    }

    def "pluginRepositories block must be a top-level block (not nested)"() {
        given:
        settingsFile << """
            if (true) {
                pluginRepositories {}
            }
        """

        when:
        fails 'help'

        then:
        failure.assertThatCause(containsString("Could not find method pluginRepositories()"))
    }

    def "Only one pluginRepositores block is allowed in each script"() {
        given:
        settingsFile << """
            pluginRepositories {}
            pluginRepositories {}
        """

        when:
        fails 'help'

        then:
        failure.assertHasLineNumber(3)
        failure.assertThatCause(containsString("At most, one pluginRepositories {} block may appear in the script."))
        includesLinkToUserguide()
    }

    def "Can access properties in pluginRepositories block"() {
        given:
        settingsFile << """
            pluginRepositories {
                maven {
                    url repoUrl
                }
            }
        """
        expect:
        succeeds 'help', '-PrepoUrl=some/place'

    }

    def "Can access SettingsScript API in pluginRepositories block"() {
        given:
        settingsFile << """
            pluginRepositories {
                maven {
                    url file('bar')
                }
            }
        """
        expect:
        succeeds 'help'
    }

    def "Cannot access Settings API in pluginRepositories block"() {
        given:
        settingsFile << """
            pluginRepositories {
                include 'foo'
            }
        """
        when:
        fails 'help'

        then:
        failure.assertHasLineNumber(3)
        failure.assertThatCause(containsString("Could not find method include()"))
    }

    void includesLinkToUserguide() {
        failure.assertThatCause(containsString("https://docs.gradle.org/${GradleVersion.current().getVersion()}/userguide/plugins.html#sec:plugin_repositories"))
    }
}
