/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests
import org.gradle.api.JavaVersion
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.TestProxyServer
import org.gradle.util.GradleVersion
import org.junit.Rule

import static org.gradle.test.matchers.UserAgentMatcher.matchesNameAndVersion
import static org.hamcrest.Matchers.containsString
import static org.junit.Assert.assertThat

class WrapperHttpIntegrationTest extends AbstractWrapperIntegrationSpec {
    @Rule HttpServer server = new HttpServer()
    @Rule TestProxyServer proxyServer = new TestProxyServer()

    void setup() {
        server.start()
        server.expectUserAgent(matchesNameAndVersion("gradlew", GradleVersion.current().getVersion()))
        file("build.gradle") << """
    task hello << {
        println 'hello'
    }

    task echoProperty << {
        println "fooD=" + project.properties["fooD"]
    }
"""
    }

    private prepareWrapper(String baseUrl) {
        prepareWrapper(new URI("${baseUrl}/gradlew/dist"))
    }

    public void "downloads wrapper from http server and caches"() {
        given:
        prepareWrapper("http://localhost:${server.port}")
        server.expectGet("/gradlew/dist", distribution.binDistribution)

        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))
    }

    public void "recovers from failed download"() {
        given:
        prepareWrapper("http://localhost:${server.port}")
        server.addBroken("/")

        when:
        wrapperExecuter.withStackTraceChecksDisabled()
        def failure = wrapperExecuter.runWithFailure()

        then:
        failure.error.contains('Server returned HTTP response code: 500')

        when:
        server.resetExpectations()
        server.expectGet("/gradlew/dist", distribution.binDistribution)

        and:
        wrapperExecuter.run()

        then:
        noExceptionThrown()
    }

    public void "downloads wrapper via proxy"() {
        given:
        proxyServer.start()
        prepareWrapper(server.uri.toString())
        file("gradle.properties") << """
    systemProp.http.proxyHost=localhost
    systemProp.http.proxyPort=${proxyServer.port}
    systemProp.http.nonProxyHosts=${JavaVersion.current() >= JavaVersion.VERSION_1_7 ? '' : '~localhost'}
"""
        server.expectGet("/gradlew/dist", distribution.binDistribution)

        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))

        and:
        proxyServer.requestCount == 1
    }

    public void "downloads wrapper via authenticated proxy"() {
        given:
        proxyServer.start('my_user', 'my_password')

        and:
        prepareWrapper(server.uri.toString())
        server.expectGet("/gradlew/dist", distribution.binDistribution)
        file("gradle.properties") << """
    systemProp.http.proxyHost=localhost
    systemProp.http.proxyPort=${proxyServer.port}
    systemProp.http.nonProxyHosts=${JavaVersion.current() >= JavaVersion.VERSION_1_7 ? '' : '~localhost'}
    systemProp.http.proxyUser=my_user
    systemProp.http.proxyPassword=my_password
"""

        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))

        and:
        proxyServer.requestCount == 1
    }
}
