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
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.fixtures.server.http.TestProxyServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Rule
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.MatcherAssert.assertThat

@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_EMBEDDED_REASON)
class WrapperHttpIntegrationTest extends AbstractWrapperIntegrationSpec {
    @Rule BlockingHttpServer server = new BlockingHttpServer()
    @Rule TestProxyServer proxyServer = new TestProxyServer()
    @Rule TestResources resources = new TestResources(temporaryFolder)

    public static final String TEST_DISTRIBUTION_URL = "gradlew/dist"

    private static final String HOST = "localhost"
    private static final String USER = "jdoe"
    private static final String PASSWORD = "changeit"
    private static final String DEFAULT_TOKEN = "token"

    private String getDefaultBaseUrl() {
        return getDefaultBaseUrl(false);
    }

    private String getDefaultBaseUrl(boolean uppercaseHost) {
        "http://${uppercaseHost ? HOST.toUpperCase(Locale.ROOT) : HOST}:${server.port}"
    }

    private String getDefaultAuthenticatedBaseUrl(String user = USER, String password = PASSWORD) {
        "http://$user:$password@$HOST:${server.port}"
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
        """.stripIndent()
    }

    private GradleExecuter prepareWrapper(String baseUrl) {
        prepareWrapper(new URI("${baseUrl}/$TEST_DISTRIBUTION_URL"))
    }

    private boolean verifyDistributionDownloaded() {
        // The wrapper downloads to user-home/wrapper/dists directory
        def userHomeDists = file("user-home/wrapper/dists")
        if (!userHomeDists.exists() || !userHomeDists.isDirectory()) {
            return false
        }

        // Check if there's an actual distribution file (zip or extracted directory with marker)
        def foundDistribution = false
        userHomeDists.eachFileRecurse { file ->
            if (file.name.endsWith('.zip') || file.name.endsWith('.ok')) {
                foundDistribution = true
            }
        }
        return foundDistribution
    }

    @Issue('https://github.com/gradle/gradle-private/issues/1537')
    def "downloads wrapper from http server"() {
        given:
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultBaseUrl()).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))
        verifyDistributionDownloaded()

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))
    }

    @Issue('https://github.com/gradle/gradle-private/issues/1537')
    def "recovers from failed download"() {
        given:
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultBaseUrl()).run()
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

    @Issue('https://github.com/gradle/gradle/issues/6557')
    def "fails fast when server returns 404 Not Found"() {
        given:
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultBaseUrl()).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").missing())

        when:
        wrapperExecuter.withStackTraceChecksDisabled()
        def failure = wrapperExecuter.runWithFailure()

        then:
        failure.error.contains('Server returned HTTP response code: 404')
        !verifyDistributionDownloaded()
    }

    @Issue('https://github.com/gradle/gradle-private/issues/3032')
    def "fails with reasonable message when download times out"() {
        given:
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultBaseUrl()).run()
        server.expectAndBlock(server.get("/$TEST_DISTRIBUTION_URL"))

        when:
        wrapperExecuter.withStackTraceChecksDisabled()
        def failure = wrapperExecuter.runWithFailure()

        then:
        failure.assertHasErrorOutput("Downloading from ${getDefaultBaseUrl()}/$TEST_DISTRIBUTION_URL failed: timeout (10000ms)")
        failure.assertHasErrorOutput('Read timed out')
    }

    @Issue('https://github.com/gradle/gradle-private/issues/3032')
    def "does not leak credentials when download times out with basic auth"() {
        given:
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultAuthenticatedBaseUrl()).run()
        server.expectAndBlock(server.get("/$TEST_DISTRIBUTION_URL"))

        when:
        wrapperExecuter.withStackTraceChecksDisabled()
        def failure = wrapperExecuter.runWithFailure()

        then:
        failure.assertHasErrorOutput("Downloading from ${getDefaultBaseUrl()}/$TEST_DISTRIBUTION_URL failed: timeout (10000ms)")
        failure.assertHasErrorOutput('Read timed out')
        failure.assertNotOutput(USER)
        failure.assertNotOutput(PASSWORD)
    }

    def "does not leak credentials when download times out with bearer auth"() {
        given:
        server.withBearerAuthentication(DEFAULT_TOKEN)
        file("gradle.properties") << """
    systemProp.gradle.localhost.wrapperToken=$DEFAULT_TOKEN
"""
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultBaseUrl()).run()
        server.expectAndBlock(server.get("/$TEST_DISTRIBUTION_URL"))

        when:
        wrapperExecuter.withStackTraceChecksDisabled()
        def failure = wrapperExecuter.runWithFailure()

        then:
        failure.assertHasErrorOutput("Downloading from http://$HOST:${server.port}/$TEST_DISTRIBUTION_URL failed: timeout (10000ms)")
        failure.assertHasErrorOutput('Read timed out')
        failure.assertNotOutput(DEFAULT_TOKEN)
    }

    def "reads timeout from wrapper properties"() {
        given:
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultBaseUrl()).run()
        server.expectAndBlock(server.get("/$TEST_DISTRIBUTION_URL"))

        and:
        file('gradle/wrapper/gradle-wrapper.properties') << "networkTimeout=5000"

        when:
        wrapperExecuter.withStackTraceChecksDisabled()
        def failure = wrapperExecuter.runWithFailure()

        then:
        failure.assertHasErrorOutput("Downloading from http://$HOST:${server.port}/$TEST_DISTRIBUTION_URL failed: timeout (5000ms)")
    }

    def "downloads wrapper via proxy"() {
        given:
        proxyServer.start()

        and:
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultBaseUrl()).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        file("gradle.properties") << """
            systemProp.http.proxyHost=$HOST
            systemProp.http.proxyPort=${proxyServer.port}
            systemProp.http.nonProxyHosts=
        """.stripIndent()
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
        prepareWrapper(getDefaultBaseUrl()).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        file("gradle.properties") << """
            systemProp.http.proxyHost=$HOST
            systemProp.http.proxyPort=${proxyServer.port}
            systemProp.http.nonProxyHosts=
            systemProp.http.proxyUser=my_user
            systemProp.http.proxyPassword=my_password
        """.stripIndent()

        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))

        and:
        proxyServer.requestCount == 1
    }

    def "downloads wrapper via authenticated proxy with bearer token"() {
        given:
        proxyServer.start('my_user', 'my_password')
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        and:
        server.withBearerAuthentication(DEFAULT_TOKEN)
        file("gradle.properties") << """
    systemProp.gradle.localhost.wrapperToken=$DEFAULT_TOKEN
"""
        and:
        prepareWrapper(getDefaultBaseUrl()).run()

        file("gradle.properties") << """
            systemProp.http.proxyHost=$HOST
            systemProp.http.proxyPort=${proxyServer.port}
            systemProp.http.nonProxyHosts=
            systemProp.http.proxyUser=my_user
            systemProp.http.proxyPassword=my_password
        """.stripIndent()

        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))

        and:
        proxyServer.requestCount == 1
    }

    def "downloads wrapper from basic authenticated http server via authenticated proxy"() {
        given:
        def proxyUsername = 'proxy_user'
        def proxyPassword = 'proxy_password'
        proxyServer.start(proxyUsername, proxyPassword)
        file("gradle.properties").writeProperties(
                'systemProp.http.proxyHost': HOST as String,
                'systemProp.http.proxyPort': proxyServer.port as String,
                'systemProp.http.nonProxyHosts': '',
                'systemProp.http.proxyUser': proxyUsername,
                'systemProp.http.proxyPassword': proxyPassword
        )

        and:
        server.withBasicAuthentication(USER, PASSWORD)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultAuthenticatedBaseUrl()).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))
        and:
        proxyServer.requestCount == 2
    }

    def "downloads wrapper from bearer authenticated http server via authenticated proxy"() {
        given:
        def proxyUsername = 'proxy_user'
        def proxyPassword = 'proxy_password'
        proxyServer.start(proxyUsername, proxyPassword)
        file("gradle.properties").writeProperties(
                'systemProp.http.proxyHost': HOST as String,
                'systemProp.http.proxyPort': proxyServer.port as String,
                'systemProp.http.nonProxyHosts': '',
                'systemProp.http.proxyUser': proxyUsername,
                'systemProp.http.proxyPassword': proxyPassword
        )

        and:
        server.withBearerAuthentication(DEFAULT_TOKEN)
        file("gradle.properties") << """
            systemProp.gradle.localhost.wrapperToken=$DEFAULT_TOKEN
        """.stripIndent()
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultBaseUrl()).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))
        and:
        proxyServer.requestCount == 2
    }

    def "downloads wrapper from basic authenticated server"() {
        given:
        server.withBasicAuthentication(USER, PASSWORD)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultAuthenticatedBaseUrl()).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))
    }

    def "downloads wrapper from bearer authenticated server"() {
        given:
        file("gradle.properties") << """
            systemProp.gradle.localhost.wrapperToken=$DEFAULT_TOKEN
        """.stripIndent()
        server.withBearerAuthentication(DEFAULT_TOKEN)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultBaseUrl(uppercaseHost)).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))

        where:
        uppercaseHost << [false, true]
    }

    def "downloads wrapper from basic authenticated server using credentials from gradle.properties"() {
        given:
        file("gradle.properties") << """
            systemProp.gradle.wrapperUser=$USER
            systemProp.gradle.wrapperPassword=$PASSWORD
        """.stripIndent()

        and:
        server.withBasicAuthentication(USER, PASSWORD)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultBaseUrl()).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))
    }

    def "basic authentication using credentials from gradle.properties take precedence over credentials included in the URL"() {
        given:
        file("gradle.properties") << """
            systemProp.gradle.wrapperUser=$USER
            systemProp.gradle.wrapperPassword=$PASSWORD
        """.stripIndent()

        and:
        server.withBasicAuthentication(USER, PASSWORD)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultAuthenticatedBaseUrl("badUser", "basPassword")).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))
    }

    def "downloads wrapper from bearer token authenticated server using token from gradle.properties"() {
        given:
        def token = "apiToken"

        and:
        file("gradle.properties") << """
            systemProp.gradle.wrapperToken=$token
        """.stripIndent()

        and:
        server.withBearerAuthentication(token)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultBaseUrl()).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))
    }

    def "bearer token authentication takes precedence over password based authentication in all forms"() {
        given:
        def token = "apiToken"

        and:
        file("gradle.properties") << """
            systemProp.gradle.wrapperUser=basUser
            systemProp.gradle.wrapperPassword=badPassword
            systemProp.gradle.wrapperToken=$token
        """.stripIndent()

        and:
        server.withBearerAuthentication(token)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultAuthenticatedBaseUrl("worseUser", "worsePassword")).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))
    }

    def "downloads wrapper from host specific bearer token authenticated server using token from gradle.properties"() {
        given:
        def token = "apiToken"

        and:
        file("gradle.properties") << """
            systemProp.gradle.wrapperToken=INVALID
            systemProp.gradle.${HOST}.wrapperToken=$token
        """.stripIndent()

        and:
        server.withBearerAuthentication(token)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultBaseUrl()).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))
    }

    def "warns about using basic authentication over insecure connection"() {
        given:
        server.withBasicAuthentication(USER, PASSWORD)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultAuthenticatedBaseUrl()).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('Please consider using HTTPS'))
    }

    def "warns about using bearer token authentication over insecure connection"() {
        given:
        def token = "apiToken"

        and:
        file("gradle.properties") << """
            systemProp.gradle.wrapperToken=$token
        """.stripIndent()

        and:
        server.withBearerAuthentication(token)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultBaseUrl()).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('Please consider using HTTPS'))
    }

    def "does not leak basic authentication credentials in output"() {
        given:
        server.withBasicAuthentication(USER, PASSWORD)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultAuthenticatedBaseUrl()).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        result.assertNotOutput(PASSWORD)
    }

    def "does not leak bearer token authentication credentials in output"() {
        given:
        def token = "apiToken"

        and:
        file("gradle.properties") << """
            systemProp.gradle.wrapperToken=$token
        """.stripIndent()

        and:
        server.withBearerAuthentication(token)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultBaseUrl()).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        result.assertNotOutput('apiToken')
    }

    def "does not leak basic authentication credentials in exception messages"() {
        given:
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultAuthenticatedBaseUrl()).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").broken())

        when:
        wrapperExecuter.withTasks('hello').run()

        then:
        def exception = thrown(Exception)
        !exception.message.contains(PASSWORD)
    }

    def "does not leak bearer token authentication credentials in exception messages"() {
        given:
        def token = "apiToken"

        and:
        file("gradle.properties") << """
            systemProp.gradle.wrapperToken=$token
        """.stripIndent()

        and:
        server.withBearerAuthentication(token)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        prepareWrapper(getDefaultBaseUrl()).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").broken())

        when:
        wrapperExecuter.withTasks('hello').run()

        then:
        def exception = thrown(Exception)
        !exception.message.contains('apiToken')
    }

    def "fails when basic authentication credentials incorrect"() {
        given:
        server.withBasicAuthentication("otherUser", "otherPassword")

        when:
        def failure = prepareWrapper(getDefaultAuthenticatedBaseUrl()).runWithFailure()

        then:
        failure.assertHasCause("Test of distribution url ${getDefaultAuthenticatedBaseUrl()}/$TEST_DISTRIBUTION_URL failed.")
    }

    def "fails when bearer token authentication credentials incorrect"() {
        given:
        def token = "apiToken"

        and:
        file("gradle.properties") << """
            systemProp.gradle.wrapperToken=$token
        """.stripIndent()

        and:
        server.withBearerAuthentication("otherToken")

        when:
        def failure = prepareWrapper(getDefaultAuthenticatedBaseUrl()).runWithFailure()

        then:
        failure.assertHasCause("Test of distribution url ${getDefaultAuthenticatedBaseUrl()}/$TEST_DISTRIBUTION_URL failed.")
    }
}
