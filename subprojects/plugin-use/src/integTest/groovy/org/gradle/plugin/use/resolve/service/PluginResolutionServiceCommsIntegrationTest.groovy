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

package org.gradle.plugin.use.resolve.service

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.plugin.use.resolve.service.internal.ErrorResponse
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.GradleVersion
import org.hamcrest.Matchers
import org.junit.Rule
import spock.lang.Unroll

import static org.gradle.util.Matchers.containsText

/**
 * Tests the communication aspects of working with the plugin resolution service.
 */
public class PluginResolutionServiceCommsIntegrationTest extends AbstractIntegrationSpec {
    def pluginBuilder = new PluginBuilder(file("plugin"))

    @Rule
    PluginResolutionServiceTestServer portal = new PluginResolutionServiceTestServer(executer, mavenRepo)

    def setup() {
        executer.requireOwnGradleUserHomeDir()
        portal.start()
    }

    @Unroll
    def "response that is not an expected service response is fatal to plugin resolution - status = #statusCode"() {
        def responseBody = "<html><bogus/></html>"
        portal.expectPluginQuery("myplugin", "1.0") {
            status = statusCode
            contentType = "text/html"
            writer.withWriter {
                it << responseBody
            }
        }

        buildScript applyAndVerify("myplugin", "1.0")

        expect:
        fails("verify", "-i")
        errorResolvingPlugin()
        failure.assertThatCause(containsText("Response from 'http://localhost.+? was not a valid plugin resolution service response"))
        failure.assertThatCause(containsText("returned content type 'text/html.+, not 'application/json.+"))
        output.contains("content:\n" + responseBody)

        where:
        statusCode << [200, 404, 500]
    }

    def "404 plugin resolution service response is not fatal to plugin resolution, but indicates plugin is not available in service"() {
        portal.expectNotFound("myplugin", "1.0")

        buildScript applyAndVerify("myplugin", "1.0")

        expect:
        fails("verify")
        failure.assertThatDescription(Matchers.startsWith("Plugin [id: 'myplugin', version: '1.0'] was not found in any of the following sources:"))
    }

    def "404 resolution that indicates plugin is known but not by that version produces indicative message"() {
        portal.expectQueryAndReturnError("myplugin", "1.0", 404) {
            errorCode = ErrorResponse.Code.UNKNOWN_PLUGIN_VERSION
            message = "anything"
        }

        buildScript applyAndVerify("myplugin", "1.0")

        expect:
        fails("verify")
        failure.assertThatDescription(Matchers.startsWith("Plugin [id: 'myplugin', version: '1.0'] was not found in any of the following sources:"))
        failure.assertThatDescription(Matchers.containsString("version '1.0' of this plugin does not exist"))
    }

    def "failed module resolution fails plugin resolution"() {
        portal.expectPluginQuery("myplugin", "1.0", "my", "plugin", "1.0")

        buildScript applyAndVerify("myplugin", "1.0")

        expect:
        fails("verify")
        errorResolvingPlugin()
        failure.assertHasCause("Failed to resolve all plugin dependencies from " + portal.m2repo.uri)
        failure.assertHasCause("Could not find my:plugin:1.0.")
    }

    def "portal JSON response with unknown implementation type fails plugin resolution"() {
        portal.expectPluginQuery("myplugin", "1.0", "foo", "foo", "foo") {
            implementationType = "SUPER_GREAT"
        }

        buildScript applyAndVerify("myplugin", "1.0")

        expect:
        fails("verify")
        errorResolvingPlugin()
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
            outputStream.withStream { it << "[}".getBytes("utf8") }
        }

        buildScript applyAndVerify("myplugin", "1.0")

        expect:
        fails("verify")
        errorResolvingPlugin()
        failure.assertHasCause("Failed to parse plugin resolution service JSON response.")
    }

    def "extra information in portal JSON response is tolerated (and neglected)"() {
        publishPlugin("myplugin", "my", "plugin", "1.0")

        portal.expectPluginQuery("myplugin", "1.0") {
            contentType = "application/json"
            outputStream.withStream {
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
                """.getBytes("utf8")
            }
        }

        buildScript applyAndVerify("myplugin", "1.0")

        expect:
        succeeds("verify")
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

    def "error message is embedded in user error message"() {
        portal.expectQueryAndReturnError("myplugin", "1.0", 500) {
            errorCode = "INTERNAL_SERVER_ERROR"
            message = "Bintray communication failure"
        }

        buildScript applyAndVerify("myplugin", "1.0")

        expect:
        fails("verify")
        errorResolvingPlugin()
        failure.assertHasCause("Plugin resolution service returned HTTP 500 with message 'Bintray communication failure'.")
    }

    def "response can contain utf8"() {
        portal.expectQueryAndReturnError("myplugin", "1.0", 500) {
            errorCode = "INTERNAL_SERVER_ERROR"
            message = "\u00E9"
        }

        buildScript applyAndVerify("myplugin", "1.0")

        expect:
        fails("verify")
        errorResolvingPlugin()
        failure.assertHasCause("Plugin resolution service returned HTTP 500 with message 'é'.")
    }

    def ExecutionFailure errorResolvingPlugin() {
        failure.assertHasDescription("Error resolving plugin [id: 'myplugin', version: '1.0'].")
    }

    def "non contactable resolution service produces error"() {
        portal.injectUrlOverride(executer) // have to do this, because only happens by default if test server is running
        portal.stop()

        buildScript applyAndVerify("myplugin", "1.0")

        expect:
        fails("verify")
        errorResolvingPlugin()
        failure.assertThatCause(containsText("Could not GET 'http://localhost:\\d+/api/gradle/.+?/plugin/use/myplugin/1\\.0'"))
        failure.assertThatCause(containsText("Connection to http://localhost:\\d+ refused"))
    }

    def "non contactable dependency repository produces error"() {
        given:
        // Get an address that isn't used
        def httpServer = new HttpServer()
        httpServer.start()
        def address = httpServer.address
        httpServer.stop()

        buildScript applyAndVerify("myplugin", "1.0")

        portal.expectPluginQuery("myplugin", "1.0", "foo", "bar", "1.0")  {
            implementation.repo = address
        }

        expect:
        fails("verify")
        errorResolvingPlugin()
        failure.assertHasCause("Failed to resolve all plugin dependencies from $address")
        failure.assertThatCause(containsText("Connection to $address refused"))
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
