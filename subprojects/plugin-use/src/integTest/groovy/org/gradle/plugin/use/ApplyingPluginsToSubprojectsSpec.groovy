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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.plugin.PluginBuilder

@LeaksFileHandles
class ApplyingPluginsToSubprojectsSpec extends AbstractIntegrationSpec {

    private publishTestPlugin(String version) {
        def pluginBuilder = new PluginBuilder(testDirectory.file("plugin"))

        pluginBuilder.addPluginWithPrintlnTask("baseTask", "from base plugin version $version", "org.example.plugin.base", "TestPluginBase")
        pluginBuilder.addPluginWithPrintlnTask("pluginTask", "from plugin version $version", "org.example.plugin", "TestPlugin")
        pluginBuilder.publishAs("org.example.plugins:plugins:$version", mavenRepo, executer)
    }

    void setup() {
        publishTestPlugin('1.0.0')
        settingsFile << """
            pluginRepositories {
                maven {
                    url '${mavenRepo.uri}'
                }
            }
            include 'client'
            include 'server'
        """
    }

    void "Can apply plugins to subprojects"() {
        when:
        buildScript """
            plugins {
                subprojects {
                    id 'org.example.plugin' version '1.0.0'
                }
            }
        """

        then:
        succeeds "client:pluginTask"
        succeeds "server:pluginTask"
    }


    void "Can apply plugins to allprojects"() {
        when:
        buildScript """
            plugins {
                allprojects {
                    id 'org.example.plugin' version '1.0.0'
                }
            }
        """

        then:
        succeeds ":pluginTask"
        succeeds "client:pluginTask"
        succeeds "server:pluginTask"
    }

    void "Applying plugins to subprojects does not apply them to the parent"() {
        when:
        buildScript """
            plugins {
                subprojects {
                    id 'org.example.plugin' version '1.0.0'
                }
            }
        """

        then:
        fails ":pluginTask"
    }

    void "Can cross-configure plugins applied to subprojects"() {
        when:
        buildScript """
            plugins {
                subprojects {
                    id 'org.example.plugin' version '1.0.0'
                }
            }
            subprojects {
                if (!plugins.hasPlugin(org.gradle.test.TestPlugin)) {
                    throw new GradleException()
                }
            }
        """

        then:
        succeeds "help"
    }

    void "Applying a plugin to subprojects gives those subprojects access to the plugin classes"() {
        when:
        buildScript """
            plugins {
                subprojects {
                    id 'org.example.plugin' version '1.0.0'
                }
            }
        """
        file("client", "build.gradle") << """
            if (!plugins.hasPlugin(org.gradle.test.TestPlugin)) {
                throw new GradleException()
            }
        """

        then:
        succeeds "help"
    }

    void "Can apply another plugin from the same jar in a subproject"() {
        when:
        buildScript """
            plugins {
                subprojects {
                    id 'org.example.plugin.base' version '1.0.0'
                }
            }
        """
        file("client", "build.gradle") << """
            plugins {
                id 'org.example.plugin' version '1.0.0'
            }
        """

        then:
        succeeds "client:pluginTask"
    }

    @NotYetImplemented
    void "Fails when trying to resolve an incompatible version in a child project"() {
        when:
        publishTestPlugin('2.0.0')
        buildScript """
            plugins {
                subprojects {
                    id 'org.example.plugin.base' version '1.0.0'
                }
            }
        """
        file("client", "build.gradle") << """
            plugins {
                id 'org.example.plugin' version '2.0.0'
            }
        """

        then:
        fails "client:pluginTask"
    }
}
