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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.TestProxyServer
import org.gradle.util.GradleVersion
import org.junit.Rule

import static org.gradle.test.matchers.UserAgentMatcher.matchesNameAndVersion
import static org.hamcrest.Matchers.containsString
import static org.junit.Assert.assertThat

class WrapperHttpIntegrationTest extends AbstractIntegrationSpec {
    @Rule HttpServer server = new HttpServer()
    @Rule TestProxyServer proxyServer = new TestProxyServer(server)

    void setup() {
        assert distribution.binDistribution.exists(): "bin distribution must exist to run this test, you need to run the :distributions:binZip task"
        executer.beforeExecute(new WrapperSetup())
        server.start()
        server.expectUserAgent(matchesNameAndVersion("gradlew", GradleVersion.current().getVersion()))
    }

    GradleExecuter getWrapperExecuter() {
        executer.usingExecutable('gradlew').inDirectory(testDirectory)
    }

    private prepareWrapper(String baseUrl) {
        file("build.gradle") << """
    wrapper {
        distributionUrl = '${baseUrl}/gradlew/dist'
    }

    task hello << {
        println 'hello'
    }

    task echoProperty << {
        println "fooD=" + project.properties["fooD"]
    }
"""

        executer.withTasks('wrapper').run()
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
        prepareWrapper("http://not.a.real.domain")
        file("gradle.properties") << """
    systemProp.http.proxyHost=localhost
    systemProp.http.proxyPort=${proxyServer.port}
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
        proxyServer.start()
        proxyServer.requireAuthentication('my_user', 'my_password')

        and:
        prepareWrapper("http://not.a.real.domain")
        server.expectGet("/gradlew/dist", distribution.binDistribution)
        file("gradle.properties") << """
    systemProp.http.proxyHost=localhost
    systemProp.http.proxyPort=${proxyServer.port}
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
