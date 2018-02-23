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
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.TestProxyServer
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import org.junit.Rule
import spock.lang.Unroll

import static org.gradle.test.matchers.UserAgentMatcher.matchesNameAndVersion
import static org.hamcrest.Matchers.containsString
import static org.junit.Assert.assertThat

class WrapperHttpIntegrationTest extends AbstractWrapperIntegrationSpec {
    @Rule HttpServer server = new HttpServer()
    @Rule TestProxyServer proxyServer = new TestProxyServer()
    @Rule TestResources resources = new TestResources(temporaryFolder)

    void setup() {
        server.start()
        server.expectUserAgent(matchesNameAndVersion("gradlew", GradleVersion.current().getVersion()))
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

    def "downloads wrapper from basic authenticated server and caches"() {
        given:
        prepareWrapper("http://jdoe:changeit@localhost:${server.port}")
        server.expectGet("/gradlew/dist", "jdoe", "changeit", distribution.binDistribution)

        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        result.output.contains('hello')

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        result.output.contains('hello')
    }

    def "downloads wrapper from basic authenticated server using credentials from gradle.properties"() {
        given:
        file("gradle.properties") << '''
            systemProp.gradle.wrapperUser=jdoe
            systemProp.gradle.wrapperPassword=changeit
        '''.stripIndent()

        and:
        prepareWrapper("http://localhost:${server.port}")
        server.expectGet("/gradlew/dist", "jdoe", "changeit", distribution.binDistribution)

        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        result.output.contains('hello')
    }

    def "warns about using basic authentication over insecure connection"() {
        given:
        prepareWrapper("http://jdoe:changeit@localhost:${server.port}")
        server.expectGet("/gradlew/dist", "jdoe", "changeit", distribution.binDistribution)

        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        result.output.contains('Please consider using HTTPS')
    }

    def "does not warn about using basic authentication over secure connection"() {
        given:
        TestKeyStore keyStore = TestKeyStore.init(resources.dir)
        keyStore.enableSslWithServerCert(server)
        // We need to set the SSL properties as arguments here even for non-embedded test mode
        // because we want them to be set on the wrapper client JVM, not the daemon one
        wrapperExecuter.withArgument("-Djavax.net.ssl.trustStore=$keyStore.trustStore.path")
        wrapperExecuter.withArgument("-Djavax.net.ssl.trustStorePassword=$keyStore.trustStorePassword")

        and:
        prepareWrapper("https://jdoe:changeit@localhost:${server.sslPort}")
        server.expectGet("/gradlew/dist", "jdoe", "changeit", distribution.binDistribution)

        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        !result.output.contains('WARNING Using HTTP Basic Authentication over an insecure connection to download the Gradle distribution. Please consider using HTTPS.')
    }

    def "does not leak basic authentication credentials in output"() {
        given:
        prepareWrapper("http://jdoe:changeit@localhost:${server.port}")
        server.expectGet("/gradlew/dist", "jdoe", "changeit", distribution.binDistribution)

        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        !result.output.contains('changeit')
        !result.error.contains('changeit')
    }

    def "does not leak basic authentication credentials in exception messages"() {
        given:
        prepareWrapper("http://jdoe:changeit@localhost:${server.port}")
        server.expectGetBroken("/gradlew/dist")

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
        server.expectGet("/gradlew/dist", "jdoe", "changeit", distribution.binDistribution)

        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        result.output.contains('hello')

        and:
        proxyServer.requestCount == 1
    }

    @Requires(adhoc = { !AvailableJavaHomes.getJdks("1.5").empty })
    @Unroll
    def "provides reasonable failure message when attempting to download authenticated distribution under java #jdk.javaVersion()"() {
        given:
        prepareWrapper("http://jdoe:changeit@localhost:${server.port}")

        and:
        wrapperExecuter.withJavaHome(jdk.javaHome)

        expect:
        def failure = wrapperExecuter.withTasks('help').withStackTraceChecksDisabled().runWithFailure()
        failure.error.contains('Downloading Gradle distributions with HTTP Basic Authentication is not supported on your JVM.')

        where:
        jdk << AvailableJavaHomes.getJdks("1.5")
    }
}
