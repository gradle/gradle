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
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.fixtures.server.http.TestProxyServer
import org.junit.Rule
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString
import static org.junit.Assert.assertThat

class WrapperHttpIntegrationTest extends AbstractWrapperIntegrationSpec {
    @Rule BlockingHttpServer server = new BlockingHttpServer()
    @Rule TestProxyServer proxyServer = new TestProxyServer()
    @Rule TestResources resources = new TestResources(temporaryFolder)

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
            println "fooD=" + project.properties["fooD"]
        }
    }
"""
    }

    private prepareWrapper(String baseUrl) {
        prepareWrapper(new URI("${baseUrl}/gradlew/dist"))
    }

    @Issue('https://github.com/gradle/gradle-private/issues/1537')
    def "downloads wrapper from http server and caches"() {
        given:
        prepareWrapper("http://localhost:${server.port}")
        server.expect(server.get("/gradlew/dist").sendFile(distribution.binDistribution))

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
        prepareWrapper("http://localhost:${server.port}")
        server.expect(server.get("/gradlew/dist").broken())

        when:
        wrapperExecuter.withStackTraceChecksDisabled()
        def failure = wrapperExecuter.runWithFailure()

        then:
        failure.error.contains('Server returned HTTP response code: 500')

        when:
        server.expect(server.get("/gradlew/dist").sendFile(distribution.binDistribution))

        and:
        wrapperExecuter.run()

        then:
        noExceptionThrown()
    }

    def "downloads wrapper via proxy"() {
        given:
        proxyServer.start()
        prepareWrapper(server.uri.toString())
        file("gradle.properties") << """
    systemProp.http.proxyHost=localhost
    systemProp.http.proxyPort=${proxyServer.port}
    systemProp.http.nonProxyHosts=${JavaVersion.current() >= JavaVersion.VERSION_1_7 ? '' : '~localhost'}
"""
        server.expect(server.get("/gradlew/dist").sendFile(distribution.binDistribution))

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
        prepareWrapper(server.uri.toString())
        server.expect(server.get("/gradlew/dist").sendFile(distribution.binDistribution))
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

    def "downloads wrapper from basic authenticated server and caches"() {
        given:
        prepareWrapper("http://jdoe:changeit@localhost:${server.port}")
        server.withBasicAuthentication("jdoe", "changeit")
        server.expect(server.get("/gradlew/dist").sendFile(distribution.binDistribution))

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
        prepareWrapper("http://localhost:${server.port}")
        server.withBasicAuthentication("jdoe", "changeit")
        server.expect(server.get("/gradlew/dist").sendFile(distribution.binDistribution))

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        outputContains('hello')
    }

    def "warns about using basic authentication over insecure connection"() {
        given:
        prepareWrapper("http://jdoe:changeit@localhost:${server.port}")
        server.withBasicAuthentication("jdoe", "changeit")
        server.expect(server.get("/gradlew/dist").sendFile(distribution.binDistribution))

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        outputContains('Please consider using HTTPS')
    }

    def "does not leak basic authentication credentials in output"() {
        given:
        prepareWrapper("http://jdoe:changeit@localhost:${server.port}")
        server.withBasicAuthentication("jdoe", "changeit")
        server.expect(server.get("/gradlew/dist").sendFile(distribution.binDistribution))

        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        result.assertNotOutput('changeit')
    }

    def "does not leak basic authentication credentials in exception messages"() {
        given:
        prepareWrapper("http://jdoe:changeit@localhost:${server.port}")
        server.expect(server.get("/gradlew/dist").broken())

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
            'systemProp.http.nonProxyHosts': JavaVersion.current() >= JavaVersion.VERSION_1_7 ? '' : '~localhost',
            'systemProp.http.proxyUser': proxyUsername,
            'systemProp.http.proxyPassword': proxyPassword
        )

        and:
        prepareWrapper("http://jdoe:changeit@localhost:${server.port}")
        server.withBasicAuthentication("jdoe", "changeit")
        server.expect(server.get("/gradlew/dist").sendFile(distribution.binDistribution))

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        outputContains('hello')

        and:
        proxyServer.requestCount == 1
    }
}
