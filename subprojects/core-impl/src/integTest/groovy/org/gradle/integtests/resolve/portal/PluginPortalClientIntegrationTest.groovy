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

package org.gradle.integtests.resolve.portal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.pluginportal.PluginPortalTestServer
import org.hamcrest.Matchers
import org.junit.Rule

public class PluginPortalClientIntegrationTest extends AbstractIntegrationSpec {

    def pluginBuilder = new PluginBuilder(file("plugin"))

    @Rule
    PluginPortalTestServer portal = new PluginPortalTestServer(executer, mavenRepo)

    def setup() {
        portal.start()
    }

    def "plugin declared in plugins {} block gets resolved from portal and applied"() {
        portal.expectPluginQuery("myplugin", "1.0", "my", "plugin", "1.0")
        publishTestPlugin("myplugin", "my", "plugin", "1.0")

        buildScript """
            plugins {
                id "myplugin" version "1.0"
            }

            task verify << {
                assert pluginApplied
            }
        """

        expect:
        succeeds("verify")
    }

    def "404 response from plugin portal fails plugin resolution"() {
        portal.expectMissing("myplugin", "1.0")

        buildScript applyAndVerify("myplugin", "1.0")

        expect:
        fails("verify")
        failure.assertThatDescription(Matchers.startsWith("Plugin 'myplugin:1.0' not found in plugin repositories:"))
    }

    def "portal JSON response with unknown implementation type fails plugin resolution"() {
        portal.expectPluginQuery("myplugin", "1.0", "foo", "foo", "foo") {
            implementationType = "SUPER_GREAT"
        }

        buildScript applyAndVerify("myplugin", "1.0")

        expect:
        fails("verify")
        failure.assertHasDescription("Error resolving plugin 'myplugin:1.0'.")
        failure.assertHasCause("Invalid plugin metadata: Unsupported implementation type: SUPER_GREAT.")
    }

    def "portal JSON response with missing repo fails plugin resolution"() {
        portal.expectPluginQuery("myplugin", "1.0", "foo", "bar", "1.0") {
            implementation.repo = null
        }

        buildScript applyAndVerify("myplugin", "1.0")

        expect:
        fails("verify")
        failure.assertHasDescription("Error resolving plugin 'myplugin:1.0'.")
        failure.assertHasCause("Invalid plugin metadata: No module repository specified.")
    }

    def "portal JSON response with invalid JSON syntax fails plugin resolution"() {
        portal.expectPluginQuery("myplugin", "1.0") {
            contentType = "application/json"
            writer.withWriter { it << "[}" }
        }

        buildScript applyAndVerify("myplugin", "1.0")

        expect:
        fails("verify")
        failure.assertHasDescription("Error resolving plugin 'myplugin:1.0'.")
        failure.assertHasCause("Failed to parse plugin portal JSON response.")
    }

    def "Gradle client tolerates (and neglects) extra information in portal JSON response"() {
        publishTestPlugin("myplugin", "my", "plugin", "1.0")

        portal.expectPluginQuery("myplugin", "1.0") {
            contentType = "application/json"
            writer.withWriter { it << """
                {
                    "id": "myplugin",
                    "version": "1.0",
                    "implementation": {
                        "gav": "my:plugin:1.0",
                        "repo": "$portal.m2repo.uri",
                        "extra1": "info1"
                    },
                    "implementationType": "M2_JAR",
                    "extra2": "info2"
                }
                """
            }
        }

        buildScript applyAndVerify("myplugin", "1.0")

        expect:
        succeeds("verify")
    }

    private void publishTestPlugin(String pluginId, String group, String artifact, String version) {
        def module = portal.m2repo.module(group, artifact, version) // don't know why tests are failing if this module is publish()'ed
        module.allowAll()
        pluginBuilder.addPlugin("project.ext.pluginApplied = true", pluginId)
        pluginBuilder.publishTo(executer, module.artifactFile)
    }

    private String applyAndVerify(String plugin, String version) {
        """
            plugins {
                id "$plugin" version "$version"
            }

            task verify << {
                assert pluginApplied
            }
        """
    }
}
