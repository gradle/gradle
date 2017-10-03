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
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.GradleVersion
import org.hamcrest.Matchers
import org.junit.Rule
import spock.lang.Unroll

import static org.gradle.util.Matchers.matchesRegexp

/**
 * Tests the communication aspects of working with the plugin resolution service.
 */
@LeaksFileHandles
public class PluginResolutionServiceCommsIntegrationTest extends AbstractIntegrationSpec {
    public static final String PLUGIN_ID = "org.my.myplugin"
    public static final String PLUGIN_VERSION = "1.0"
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
        portal.expectPluginQuery(PLUGIN_ID, PLUGIN_VERSION) {
            status = statusCode
            contentType = "text/html"
            characterEncoding = "UTF-8"
            writer.withWriter {
                it << responseBody
            }
        }

        buildScript applyAndVerify()

        expect:
        fails("verify")
        errorResolvingPlugin()
        outOfProtocolCause("content type is 'text/html; charset=utf-8', expected 'application/json' (status code: $statusCode)")

        where:
        statusCode << [200, 404, 500]
    }

    def "404 plugin resolution service response is not fatal to plugin resolution, but indicates plugin is not available in service"() {
        portal.expectNotFound(PLUGIN_ID, PLUGIN_VERSION)

        buildScript applyAndVerify()

        expect:
        fails("verify")
        failure.assertThatDescription(Matchers.startsWith("Plugin [id 'org.my.myplugin' version '1.0'] was not found in any of the following sources:"))
    }

    def "404 resolution that indicates plugin is known but not by that version produces indicative message"() {
        portal.expectQueryAndReturnError(PLUGIN_ID, PLUGIN_VERSION, 404) {
            errorCode = ErrorResponse.Code.UNKNOWN_PLUGIN_VERSION
            message = "portal message: \u03b1"
        }

        buildScript applyAndVerify()

        expect:
        fails("verify")
        failure.assertThatDescription(Matchers.startsWith("Plugin [id 'org.my.myplugin' version '1.0'] was not found in any of the following sources:"))
        failure.assertThatDescription(Matchers.containsString("portal message: \u03b1"))
    }

    def "404 resolution that indicates plugin is unknown produces indicative message"() {
        portal.expectQueryAndReturnError(PLUGIN_ID, PLUGIN_VERSION, 404) {
            errorCode = ErrorResponse.Code.UNKNOWN_PLUGIN
            message = "portal message: \u03b1"
        }

        buildScript applyAndVerify()

        expect:
        fails("verify")
        failure.assertThatDescription(Matchers.startsWith("Plugin [id 'org.my.myplugin' version '1.0'] was not found in any of the following sources:"))
        failure.assertThatDescription(Matchers.containsString("portal message: \u03b1"))
    }

    def "failed module resolution fails plugin resolution"() {
        portal.expectPluginQuery(PLUGIN_ID, PLUGIN_VERSION, "my", "plugin", PLUGIN_VERSION)

        buildScript applyAndVerify()
        portal.m2repo.module("my", "plugin", PLUGIN_VERSION).missing()

        expect:
        fails("verify")
        failure.assertHasCause("Could not resolve all files for configuration ':classpath'.")
        failureCauseContains("Searched in the following locations:")
        failureCauseContains(portal.m2repo.uri.toString())
    }

    def "portal JSON response with unknown implementation type fails plugin resolution"() {
        portal.expectPluginQuery(PLUGIN_ID, PLUGIN_VERSION, "foo", "foo", "foo") {
            implementationType = "SUPER_GREAT"
        }

        buildScript applyAndVerify()

        expect:
        fails("verify")
        errorResolvingPlugin()
        outOfProtocolCause("invalid plugin metadata - unsupported implementation type 'SUPER_GREAT'")
    }

    void outOfProtocolCause(String cause) {
        failure.assertHasCause("The response from ${portal.pluginUrl(PLUGIN_ID, PLUGIN_VERSION)} was not a valid response from a Gradle Plugin Resolution Service: $cause")
    }

    def "portal JSON response with missing repo fails plugin resolution"() {
        portal.expectPluginQuery(PLUGIN_ID, PLUGIN_VERSION, "foo", "bar", PLUGIN_VERSION) {
            implementation.repo = null
        }

        buildScript applyAndVerify()

        expect:
        fails("verify")
        failure.assertHasDescription("Error resolving plugin [id 'org.my.myplugin' version '1.0']")
        outOfProtocolCause("invalid plugin metadata - no module repository specified")
    }

    @Unroll
    def "portal JSON response with invalid JSON syntax fails plugin resolution - #statusCode"() {
        portal.expectPluginQuery(PLUGIN_ID, PLUGIN_VERSION) {
            contentType = "application/json"
            outputStream.withStream { it << "[}".getBytes("utf8") }
            status = statusCode
        }

        buildScript applyAndVerify()

        expect:
        fails("verify")
        errorResolvingPlugin()
        outOfProtocolCause("could not parse response JSON")

        where:
        statusCode << [200, 500, 410]
    }

    def "extra information in portal JSON response is tolerated (and neglected)"() {
        publishPlugin(PLUGIN_ID, "my", "plugin", PLUGIN_VERSION)

        portal.expectPluginQuery(PLUGIN_ID, PLUGIN_VERSION) {
            contentType = "application/json"
            outputStream.withStream {
                it << """
                {
                    "id": "org.my.myplugin",
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

        buildScript applyAndVerify()

        expect:
        succeeds("verify")
    }

    def "portal redirects are being followed"() {
        portal.expectPluginQuery(PLUGIN_ID, PLUGIN_VERSION) {
            sendRedirect("/${portal.API_PATH}/${GradleVersion.current().version}/plugin/use/org.my.otherplugin/2.0")
        }
        portal.expectQueryAndReturnError("org.my.otherplugin", "2.0", 500) {
            errorCode = "REDIRECTED"
            message = "redirected"
        }

        buildScript applyAndVerify()

        expect:
        fails("verify")
        failure.assertThatCause(Matchers.startsWith("Plugin resolution service returned HTTP 500 with message 'redirected'"))
    }

    def "error message is embedded in user error message"() {
        portal.expectQueryAndReturnError(PLUGIN_ID, PLUGIN_VERSION, 500) {
            errorCode = "INTERNAL_SERVER_ERROR"
            message = "Bintray communication failure"
        }

        buildScript applyAndVerify()

        expect:
        fails("verify")
        errorResolvingPlugin()
        failure.assertHasCause("Plugin resolution service returned HTTP 500 with message 'Bintray communication failure' (url: ${portal.pluginUrl(PLUGIN_ID, PLUGIN_VERSION)})")
    }

    @Unroll
    def "incompatible error message schema - status #errorStatusCode"() {
        portal.expectPluginQuery(PLUGIN_ID, PLUGIN_VERSION) {
            status = errorStatusCode
            contentType = "application/json"
            outputStream.withStream {
                it << """
                {
                    "foo": "bar"
                }
                """.getBytes("utf8")
            }
        }

        buildScript applyAndVerify()

        expect:
        fails("verify")
        errorResolvingPlugin()
        outOfProtocolCause("invalid error response - no error code specified")

        where:
        errorStatusCode << [500, 410]
    }

    @Unroll
    def "error message schema is relaxed- status #errorStatusCode"() {
        portal.expectPluginQuery(PLUGIN_ID, PLUGIN_VERSION) {
            status = errorStatusCode
            contentType = "application/json"
            outputStream.withStream {
                it << """
                {
                    "errorCode": "foo",
                    "message": "bar",
                    "extra": "boom!"

                }
                """.getBytes("utf8")
            }
        }

        buildScript applyAndVerify()

        expect:
        fails("verify")
        errorResolvingPlugin()
        failure.assertHasCause("Plugin resolution service returned HTTP $errorStatusCode with message 'bar' (url: ${portal.pluginUrl(PLUGIN_ID, PLUGIN_VERSION)})")

        where:
        errorStatusCode << [500, 410]
    }

    def "error message for 4xx is embedded in user error message"() {
        portal.expectQueryAndReturnError(PLUGIN_ID, PLUGIN_VERSION, 405) {
            errorCode = "SOME_STRANGE_ERROR"
            message = "Some strange error message"
        }

        buildScript applyAndVerify()

        expect:
        fails("verify")
        errorResolvingPlugin()
        failure.assertHasCause("Plugin resolution service returned HTTP 405 with message 'Some strange error message' (url: ${portal.pluginUrl(PLUGIN_ID, PLUGIN_VERSION)})")
    }

    def "response can contain utf8"() {
        portal.expectQueryAndReturnError(PLUGIN_ID, PLUGIN_VERSION, 500) {
            errorCode = "INTERNAL_SERVER_ERROR"
            message = "\u00E9"
        }

        buildScript applyAndVerify()

        expect:
        fails("verify")
        errorResolvingPlugin()
        failure.assertHasCause("Plugin resolution service returned HTTP 500 with message 'é' (url: ${portal.pluginUrl(PLUGIN_ID, PLUGIN_VERSION)})")
    }

    def ExecutionFailure errorResolvingPlugin() {
        failure.assertHasDescription("Error resolving plugin [id 'org.my.myplugin' version '1.0']")
    }

    def "non contactable resolution service produces error"() {
        portal.injectUrlOverride(executer) // have to do this, because only happens by default if test server is running
        portal.stop()

        buildScript applyAndVerify()

        expect:
        fails("verify")
        errorResolvingPlugin()

        failure.assertThatCause(matchesRegexp(".*?Could not GET 'http://localhost:\\d+/.+?/plugin/use/org.my.myplugin/1.0'.*?"))
        failure.assertThatCause(matchesRegexp(".*?Connect to localhost:\\d+ (\\[.*\\])? failed: Connection refused.*"))
    }

    def "non contactable dependency repository produces error"() {
        given:
        // Get an address that isn't used
        def httpServer = new HttpServer()
        httpServer.start()
        def address = httpServer.address
        httpServer.stop()

        buildScript applyAndVerify()

        portal.expectPluginQuery(PLUGIN_ID, PLUGIN_VERSION, "foo", "bar", PLUGIN_VERSION) {
            implementation.repo = address
        }

        expect:
        fails("verify")
        failure.assertHasCause("Could not resolve all files for configuration ':classpath'.")
        failure.assertThatCause(matchesRegexp(".*?Connect to localhost:\\d+ (\\[.*\\])? failed: Connection refused.*"))
    }

    private void publishPlugin(String pluginId, String group, String artifact, String version) {
        def module = portal.m2repo.module(group, artifact, version) // don't know why tests are failing if this module is publish()'ed
        module.allowAll()
        pluginBuilder.addPlugin("project.ext.pluginApplied = true", pluginId)
        pluginBuilder.publishTo(executer, module.artifactFile)
    }

    private static String applyAndVerify(String id = PLUGIN_ID, String version = PLUGIN_VERSION) {
        """
            plugins {
                id "$id" version "$version"
            }

            task verify {
                doLast {
                    assert pluginApplied
                }
            }
        """
    }

}
