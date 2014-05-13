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

package org.gradle.plugin.use.resolve.portal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.util.GradleVersion
import org.hamcrest.Matchers
import org.junit.Rule

public class PluginPortalResolutionIntegrationTest extends AbstractIntegrationSpec {
    def pluginBuilder = new PluginBuilder(file("plugin"))

    @Rule
    PluginPortalTestServer portal = new PluginPortalTestServer(executer, mavenRepo)

    def setup() {
        portal.start()
    }

    def "plugin declared in plugins {} block gets resolved and applied"() {
        portal.expectPluginQuery("myplugin", "1.0", "my", "plugin", "1.0")
        publishPlugin("myplugin", "my", "plugin", "1.0")

        buildScript applyAndVerify("myplugin", "1.0")

        expect:
        succeeds("verify")
    }

    def "404 response from plugin portal fails plugin resolution"() {
        portal.expectMissing("myplugin", "1.0")

        buildScript applyAndVerify("myplugin", "1.0")

        expect:
        fails("verify")
        failure.assertThatDescription(Matchers.startsWith("Plugin [id: 'myplugin', version: '1.0'] not found in plugin repositories:"))
    }

    def "failed module resolution fails plugin resolution"() {
        portal.expectPluginQuery("myplugin", "1.0", "my", "plugin", "1.0")

        buildScript applyAndVerify("myplugin", "1.0")

        expect:
        fails("verify")
        failure.assertHasDescription("Error resolving plugin [id: 'myplugin', version: '1.0'].")
        failure.assertHasCause("Could not resolve all dependencies for configuration 'detachedConfiguration1'.")
        failure.assertHasCause("Could not find my:plugin:1.0.")
    }

    def "portal JSON response with unknown implementation type fails plugin resolution"() {
        portal.expectPluginQuery("myplugin", "1.0", "foo", "foo", "foo") {
            implementationType = "SUPER_GREAT"
        }

        buildScript applyAndVerify("myplugin", "1.0")

        expect:
        fails("verify")
        failure.assertHasDescription("Error resolving plugin [id: 'myplugin', version: '1.0'].")
        failure.assertHasCause("Invalid plugin metadata: Unsupported implementation type: SUPER_GREAT.")
    }

    def "portal JSON response with missing repo fails plugin resolution"() {
        portal.expectPluginQuery("myplugin", "1.0", "foo", "bar", "1.0") {
            implementation.repo = null
        }

        buildScript applyAndVerify("myplugin", "1.0")

        expect:
        fails("verify")
        failure.assertHasDescription("Error resolving plugin [id: 'myplugin', version: '1.0'].")
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
        failure.assertHasDescription("Error resolving plugin [id: 'myplugin', version: '1.0'].")
        failure.assertHasCause("Failed to parse plugin portal JSON response.")
    }

    def "extra information in portal JSON response is tolerated (and neglected)"() {
        publishPlugin("myplugin", "my", "plugin", "1.0")

        portal.expectPluginQuery("myplugin", "1.0") {
            contentType = "application/json"
            writer.withWriter {
                it << """
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

    def "resolution fails if Gradle is in offline mode"() {
        portal.expectPluginQuery("myplugin", "1.0", "my", "plugin", "1.0")
        publishPlugin("myplugin", "my", "plugin", "1.0")

        buildScript applyAndVerify("myplugin", "1.0")
        args("--offline")

        expect:
        fails("verify")
        failure.assertHasDescription("Error resolving plugin [id: 'myplugin', version: '1.0'].")
        failure.assertHasCause("Plugin cannot be resolved from plugin portal because Gradle is running in offline mode.")
    }

    def "cannot resolve plugin with snapshot version"() {
        portal.expectPluginQuery("myplugin", "1.0-SNAPSHOT", "my", "plugin", "1.0")
        publishPlugin("myplugin", "my", "plugin", "1.0")

        buildScript applyAndVerify("myplugin", "1.0-SNAPSHOT")

        expect:
        fails("verify")
        failure.assertHasDescription("Error resolving plugin [id: 'myplugin', version: '1.0-SNAPSHOT'].")
        failure.assertHasCause("Snapshot plugin versions are not supported.")
    }

    def "cannot resolve plugin with dynamic version"() {
        portal.expectPluginQuery("myplugin", pluginVersion, "my", "plugin", "1.0")
        publishPlugin("myplugin", "my", "plugin", "1.0")

        buildScript applyAndVerify("myplugin", pluginVersion)

        expect:
        fails("verify")
        failure.assertHasDescription("Error resolving plugin [id: 'myplugin', version: '$pluginVersion'].")
        failure.assertHasCause("Dynamic plugin versions are not supported.")

        where:
        pluginVersion << ["[1.0,2.0)", "1.+", "latest.release"]
    }

    def "portal redirects are being followed"() {
        portal.expectPluginQuery("myplugin", "1.0") {
            sendRedirect("/api/gradle/${GradleVersion.current().version}/plugin/use/otherplugin/2.0")
        }
        portal.expectPluginQuery("otherplugin", "2.0", "other", "plugin", "2.0")
        publishPlugin("otherplugin", "other", "plugin", "2.0")

        buildScript applyAndVerify("otherplugin", "2.0")

        expect:
        succeeds("verify")
    }

    private void publishPlugin(String pluginId, String group, String artifact, String version) {
        def module = portal.m2repo.module(group, artifact, version) // don't know why tests are failing if this module is publish()'ed
        module.allowAll()
        pluginBuilder.addPlugin("project.ext.pluginApplied = true", pluginId)
        pluginBuilder.publishTo(executer, module.artifactFile)
    }

    private String applyAndVerify(String id, String version) {
        """
            plugins {
                id "$id" version "$version"
            }

            task verify << {
                assert pluginApplied
            }
        """
    }
}
