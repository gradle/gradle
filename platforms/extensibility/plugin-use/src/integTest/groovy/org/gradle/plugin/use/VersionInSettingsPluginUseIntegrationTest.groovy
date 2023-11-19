/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.junit.Rule

class VersionInSettingsPluginUseIntegrationTest extends AbstractIntegrationSpec {

    public static final String PLUGIN_ID = "org.myplugin"
    public static final String GROUP = "my"
    public static final String ARTIFACT = "plugin"

    def pluginBuilder = new PluginBuilder(file(ARTIFACT))

    @Rule
    MavenHttpPluginRepository pluginRepo = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    def setup() {
        // https://github.com/gradle/build-tool-flaky-tests/issues/49
        executer.requireOwnGradleUserHomeDir()
        publishPlugin("1.0")
        publishPlugin("2.0")
        withSettings """
            pluginManagement {
                plugins {
                    id '$PLUGIN_ID' version '1.0'
                }
            }
        """
    }

    def "can define plugin version in settings script"() {
        when:
        buildScript "plugins { id '$PLUGIN_ID' }"

        then:
        verifyPluginApplied('1.0')
    }

    def "can define plugin version in kotlin settings script"() {
        when:
        settingsFile.delete()
        settingsKotlinFile << """
            pluginManagement {
                plugins {
                    id("$PLUGIN_ID") version "2.0"
                }
            }
        """
        buildKotlinFile << """
            plugins { id("$PLUGIN_ID") }
            tasks.register("verify") {
                val pluginVersion: String by project
                val pluginVersionValue = pluginVersion
                doLast {
                    assert(pluginVersionValue == "2.0")
                }
            }
        """

        then:
        succeeds("verify")
    }

    def "can define plugin version with apply false in settings script"() {
        when:
        withSettings """
            pluginManagement {
                plugins {
                    id '$PLUGIN_ID' version '1.0' apply false
                }
            }
        """
        buildScript "plugins { id '$PLUGIN_ID' }"

        then:
        verifyPluginApplied('1.0')
    }

    def "can define plugin version in settings script using gradle properties"() {
        when:
        file("gradle.properties") << "myPluginVersion=2.0"
        withSettings """
            pluginManagement {
                plugins {
                    id '$PLUGIN_ID' version "\${myPluginVersion}"
                }
            }
        """
        buildScript "plugins { id '$PLUGIN_ID' }"

        then:
        verifyPluginApplied('2.0')
    }

    def "can override plugin version in settings script"() {
        when:
        buildScript "plugins { id '$PLUGIN_ID' version '2.0' }"

        then:
        verifyPluginApplied('2.0')
    }

    def "can use plugin version from settings script in one of sibling projects"() {
        when:
        settingsFile << "include 'p1', 'p2'"

        file("p1/build.gradle") << """
            plugins {
                id '$PLUGIN_ID'
            }
            ${verifyPluginTask('1.0')}
        """
        file("p2/build.gradle") << """
            plugins {
                id '$PLUGIN_ID' version '2.0'
            }
            ${verifyPluginTask('2.0')}
        """

        then:
        succeeds "verify"
    }

    def "ignores plugin version from settings script when plugin loaded in parent project"() {
        when:
        settingsFile << "include 'p1'"

        buildFile << """
            plugins {
                id '$PLUGIN_ID' version '2.0'
            }
            ${verifyPluginTask('2.0')}
        """
        file("p1/build.gradle") << """
            plugins {
                id '$PLUGIN_ID'
            }
            ${verifyPluginTask('2.0')}
        """

        then:
        succeeds "verify"
    }

    def "ignores plugin version from settings script when plugin added as buildscript dependency"() {
        when:
        buildFile << """
            buildscript {
                dependencies {
                    classpath "my:plugin:2.0"
                }
            }
            plugins {
                id '$PLUGIN_ID'
            }
        """

        then:
        verifyPluginApplied('2.0')
    }

    def "ignores plugin version from settings script when plugin added as buildSrc"() {
        when:
        file('buildSrc/build.gradle') << """
            repositories {
                gradlePluginPortal()
            }
            dependencies {
                implementation "my:plugin:2.0"
            }
        """
        buildFile << """
            plugins {
                id '$PLUGIN_ID'
            }
        """

        then:
        verifyPluginApplied('2.0')
    }

    def "cannot request that plugin be applied in settings script"() {
        when:
        withSettings """
            pluginManagement {
                plugins {
                    id '$PLUGIN_ID' version '1.0' apply true
                }
            }
        """

        then:
        fails "help"
        failure.assertHasCause "Cannot apply a plugin from within a pluginManagement block."
    }

    def "cannot specify plugin version twice in settings script"() {
        when:
        withSettings """
            pluginManagement {
                plugins {
                    id '$PLUGIN_ID' version '1.0'
                    id '$PLUGIN_ID' version '2.0'
                }
            }
        """

        then:
        fails "help"

        when:
        withSettings """
            pluginManagement {
                plugins {
                    id('$PLUGIN_ID').version('1.0').version('2.0')
                }
            }
        """

        then:
        fails "help"
        failure.assertHasCause "Cannot provide multiple default versions for the same plugin."
    }

    private void withSettings(String settings) {
        settingsFile.text = settings.stripIndent()
        settingsFile << "\nrootProject.name = 'root'\n"
    }

    def verifyPluginApplied(String version) {
        buildFile << verifyPluginTask(version)
        succeeds "verify"
    }

    def verifyPluginTask(String version) {
        """
            task verify {
                String pluginVersion = project.pluginVersion
                doLast {
                    assert pluginVersion == "$version"
                }
            }
        """
    }

    void publishPlugin(String version) {
        publishPlugin("project.ext.pluginVersion = '$version'", version)
    }

    void publishPlugin(String impl, String version) {
        pluginBuilder.addPlugin(impl, PLUGIN_ID, "TestPlugin${version.replace('.', '_')}")
        pluginBuilder.publishAs(GROUP, ARTIFACT, version, pluginRepo, executer).allowAll()
    }
}
