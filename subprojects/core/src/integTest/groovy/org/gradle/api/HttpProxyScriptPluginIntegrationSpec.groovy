/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.TestProxyServer
import org.gradle.test.matchers.UserAgentMatcher
import org.gradle.util.GradleVersion
import org.gradle.util.SetSystemProperties
import org.hamcrest.CoreMatchers
import spock.lang.Unroll

class HttpProxyScriptPluginIntegrationSpec extends AbstractIntegrationSpec {
    @org.junit.Rule SetSystemProperties systemProperties = new SetSystemProperties()
    @org.junit.Rule TestResources resources = new TestResources(temporaryFolder)
    @org.junit.Rule TestProxyServer testProxyServer = new TestProxyServer()
    @org.junit.Rule HttpServer server = new HttpServer()

    def setup() {
        settingsFile << "rootProject.name = 'project'"
        server.expectUserAgent(UserAgentMatcher.matchesNameAndVersion("Gradle", GradleVersion.current().getVersion()))
        server.start()
        executer.requireOwnGradleUserHomeDir()
    }

    @Unroll
    def "uses #type proxy to access remote build script plugin"() {
        given:
        testProxyServer.start(userName, password)

        def script = file('external.gradle')
        server.expectGet('/external.gradle', script)

        script << """
            task doStuff
            assert buildscript.sourceFile == null
            assert "${server.uri}/external.gradle" == buildscript.sourceURI as String
"""

        buildFile << """
            apply from: '$server.uri/external.gradle'
            defaultTasks 'doStuff'
"""

        when:
        testProxyServer.configureProxy(executer,"http", userName, password)
        succeeds()

        then:
        testProxyServer.requestCount == 1

        where:
        type            | userName    | password
        "configured"    | null        | null
        "authenticated" | "proxyUser" | "proxyPassword"
    }

    def "uses authenticated proxy to access remote settings script plugin"() {
        given:
        testProxyServer.start("proxyUser", "proxyPassword")

        def settingsScriptPljugin = file('settings-script-plugin.gradle') << """
            println 'loaded settings script plugin'
        """
        server.expectGet('/settings-script-plugin.gradle', settingsScriptPljugin)
        settingsFile << """
            apply from: '$server.uri/settings-script-plugin.gradle'
        """

        when:
        testProxyServer.configureProxy(executer,"http", "proxyUser", "proxyPassword")
        succeeds()

        then:
        outputContains "loaded settings script plugin"

        and:
        testProxyServer.requestCount == 1
    }

    def "reports proxy not running at configured location"() {
        given:
        testProxyServer.start()
        testProxyServer.stop()

        buildFile << """
            apply from: '$server.uri/external.gradle'
            defaultTasks 'doStuff'
"""

        when:
        testProxyServer.configureProxy(executer, "http")

        then:
        fails()

        and:
        failure.assertHasDescription("A problem occurred evaluating root project 'project'.")
                .assertHasCause("Could not get resource '${server.uri}/external.gradle'.")
                .assertHasCause("Connect to localhost:${testProxyServer.port}")
    }

    def "reports proxy authentication failure for script plugin"() {
        given:
        testProxyServer.start("proxyUser", "proxyPassword")

        buildFile << """
            apply from: '$server.uri/external.gradle'
            defaultTasks 'doStuff'
"""

        when:
        testProxyServer.configureProxy(executer, "http", "proxyUser", "wrongPassword")

        then:
        fails()

        and:
        failure.assertHasDescription("A problem occurred evaluating root project 'project'.")
                .assertHasCause("Could not get resource '${server.uri}/external.gradle'.")
                .assertThatCause(CoreMatchers.containsString("Proxy Authentication Required"))
    }
}
