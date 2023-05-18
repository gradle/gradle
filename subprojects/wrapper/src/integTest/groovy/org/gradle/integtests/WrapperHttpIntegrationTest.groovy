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

import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.fixtures.server.http.TestProxyServer
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.MatcherAssert.assertThat

@IgnoreIf({ GradleContextualExecuter.embedded }) // wrapperExecuter requires a real distribution
class WrapperHttpIntegrationTest extends AbstractWrapperIntegrationSpec {
    @Rule BlockingHttpServer server = new BlockingHttpServer()
    @Rule TestProxyServer proxyServer = new TestProxyServer()
    @Rule TestResources resources = new TestResources(temporaryFolder)

    public static final String TEST_DISTRIBUTION_URL = "gradlew/dist"

    private String getDefaultBaseUrl() {
        "http://localhost:${server.port}"
    }

    private String getDefaultAuthenticatedBaseUrl() {
        "http://jdoe:changeit@localhost:${server.port}"
    }

    def setup() {
        server.start()
        file("build.gradle") << """
    task hello {
        doLast {
            println 'hello'
        }
    }

    task echoProperty {
        doLast {
            println "fooD=" + project.findProperty("fooD")
        }
    }
"""
    }

    private prepareWrapper(String baseUrl) {
        prepareWrapper(new URI("${baseUrl}/$TEST_DISTRIBUTION_URL"))
    }

    @Issue('https://github.com/gradle/gradle-private/issues/1537')
    def "downloads wrapper from http server and caches"() {
        given:
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultBaseUrl())
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))
    }

    @Issue('https://github.com/gradle/gradle-private/issues/1537')
    def "recovers from failed download"() {
        given:
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultBaseUrl())
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").broken())

        when:
        wrapperExecuter.withStackTraceChecksDisabled()
        def failure = wrapperExecuter.runWithFailure()

        then:
        failure.error.contains('Server returned HTTP response code: 500')

        when:
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        and:
        wrapperExecuter.run()

        then:
        noExceptionThrown()
    }

    @Issue('https://github.com/gradle/gradle-private/issues/3032')
    def "fails with reasonable message when download times out"() {
        given:
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultBaseUrl())
        server.expectAndBlock(server.get("/$TEST_DISTRIBUTION_URL"))

        when:
        wrapperExecuter.withStackTraceChecksDisabled()
        def failure = wrapperExecuter.runWithFailure()

        then:
        failure.assertHasErrorOutput("Downloading from ${getDefaultBaseUrl()}/$TEST_DISTRIBUTION_URL failed: timeout (10000ms)")
        failure.assertHasErrorOutput('Read timed out')
    }

    @Issue('https://github.com/gradle/gradle-private/issues/3032')
    def "does not leak credentials when download times out"() {
        given:
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper("http://username:password@localhost:${server.port}")
        server.expectAndBlock(server.get("/$TEST_DISTRIBUTION_URL"))

        when:
        wrapperExecuter.withStackTraceChecksDisabled()
        def failure = wrapperExecuter.runWithFailure()

        then:
        failure.assertHasErrorOutput("Downloading from http://localhost:${server.port}/$TEST_DISTRIBUTION_URL failed: timeout (10000ms)")
        failure.assertHasErrorOutput('Read timed out')
        failure.assertNotOutput("username")
        failure.assertNotOutput("password")
    }

    def "reads timeout from wrapper properties"() {
        given:
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultBaseUrl())
        server.expectAndBlock(server.get("/$TEST_DISTRIBUTION_URL"))

        and:
        file('gradle/wrapper/gradle-wrapper.properties') << "networkTimeout=5000"

        when:
        wrapperExecuter.withStackTraceChecksDisabled()
        def failure = wrapperExecuter.runWithFailure()

        then:
        failure.assertHasErrorOutput("Downloading from http://localhost:${server.port}/$TEST_DISTRIBUTION_URL failed: timeout (5000ms)")
    }

    def "downloads wrapper via proxy"() {
        given:
        proxyServer.start()

        and:
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(server.uri.toString())
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        file("gradle.properties") << """
    systemProp.http.proxyHost=localhost
    systemProp.http.proxyPort=${proxyServer.port}
    systemProp.http.nonProxyHosts=
"""
        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))

        and:
        proxyServer.requestCount == 1
    }

    def "downloads wrapper via authenticated proxy"() {
        given:
        proxyServer.start('my_user', 'my_password')

        and:
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(server.uri.toString())
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        file("gradle.properties") << """
    systemProp.http.proxyHost=localhost
    systemProp.http.proxyPort=${proxyServer.port}
    systemProp.http.nonProxyHosts=
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

    def "downloads wrapper from basic authenticated server and caches"() {
        given:
        server.withBasicAuthentication("jdoe", "changeit")
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultAuthenticatedBaseUrl())
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        outputContains('hello')

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        outputContains('hello')
    }

    def "downloads wrapper from basic authenticated server using credentials from gradle.properties"() {
        given:
        file("gradle.properties") << '''
            systemProp.gradle.wrapperUser=jdoe
            systemProp.gradle.wrapperPassword=changeit
        '''.stripIndent()

        and:
        server.withBasicAuthentication("jdoe", "changeit")
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultBaseUrl())
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        outputContains('hello')
    }

    def "warns about using basic authentication over insecure connection"() {
        given:
        server.withBasicAuthentication("jdoe", "changeit")
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultAuthenticatedBaseUrl())
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        outputContains('Please consider using HTTPS')
    }

    def "does not leak basic authentication credentials in output"() {
        given:
        server.withBasicAuthentication("jdoe", "changeit")
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultAuthenticatedBaseUrl())
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        result.assertNotOutput('changeit')
    }

    def "does not leak basic authentication credentials in exception messages"() {
        given:
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultAuthenticatedBaseUrl())
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").broken())

        when:
        wrapperExecuter.withTasks('hello').run()

        then:
        def exception = thrown(Exception)
        !exception.message.contains('changeit')
    }

    def "downloads wrapper from basic authenticated http server via authenticated proxy"() {
        given:
        def proxyUsername = 'proxy_user'
        def proxyPassword = 'proxy_password'
        proxyServer.start(proxyUsername, proxyPassword)
        file("gradle.properties").writeProperties(
            'systemProp.http.proxyHost': 'localhost',
            'systemProp.http.proxyPort': proxyServer.port as String,
            'systemProp.http.nonProxyHosts': '',
            'systemProp.http.proxyUser': proxyUsername,
            'systemProp.http.proxyPassword': proxyPassword
        )

        and:
        server.withBasicAuthentication("jdoe", "changeit")
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultAuthenticatedBaseUrl())
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        outputContains('hello')
        and:
        proxyServer.requestCount == 2
    }
}
