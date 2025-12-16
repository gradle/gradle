/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.testdistribution.LocalOnly
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.BlockingHttpsServer
import org.gradle.test.fixtures.server.http.TestProxyServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.wrapper.Download
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.integtests.WrapperHttpIntegrationTest.TEST_DISTRIBUTION_URL
import static org.gradle.test.matchers.UserAgentMatcher.matchesNameAndVersion
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.MatcherAssert.assertThat

// wrapperExecuter requires a real distribution
@Requires(IntegTestPreconditions.NotEmbeddedExecutor)
@LocalOnly(because = "https://github.com/gradle/gradle-private/issues/3799")
class WrapperHttpsIntegrationTest extends AbstractWrapperIntegrationSpec {
    private static final String DEFAULT_USER = "jdoe"
    private static final String DEFAULT_PASSWORD = "changeit"
    private static final String DEFAULT_TOKEN = "token"
    @Rule
    BlockingHttpsServer server = new BlockingHttpsServer()
    @Rule
    TestProxyServer proxyServer = new TestProxyServer()
    @Rule
    TestResources resources = new TestResources(temporaryFolder)
    TestKeyStore keyStore

    def startServer(boolean basicAuth, boolean bearerAuth) {
        keyStore = TestKeyStore.init(resources.dir)
        // We need to set the SSL properties as arguments here even for non-embedded test mode
        // because we want them to be set on the wrapper client JVM, not the daemon one
        wrapperExecuter.withArguments(keyStore.getTrustStoreArguments())
        server.configure(keyStore)
        if (basicAuth) {
            server.withBasicAuthentication(DEFAULT_USER, DEFAULT_PASSWORD)
        }
        if (bearerAuth) {
            server.withBearerAuthentication(DEFAULT_TOKEN)
        }
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
        prepareWrapper(new URI("${baseUrl}/$TEST_DISTRIBUTION_URL"), keyStore)
    }

    def "does not warn about using basic authentication over secure connection"() {
        given:
        startServer(true, false)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        server.expect(server.get("/$TEST_DISTRIBUTION_URL")
            .expectUserAgent(matchesNameAndVersion("gradlew", Download.UNKNOWN_VERSION))
            .sendFile(distribution.binDistribution))

        and:
        prepareWrapper(getAuthenticatedBaseUrl()).run()

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        outputDoesNotContain('WARNING Using HTTP Basic Authentication over an insecure connection to download the Gradle distribution. Please consider using HTTPS.')
    }

    def "does not warn about using api token authentication over secure connection"() {
        given:
        startServer(false, true)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        server.expect(server.get("/$TEST_DISTRIBUTION_URL")
            .expectUserAgent(matchesNameAndVersion("gradlew", Download.UNKNOWN_VERSION))
            .sendFile(distribution.binDistribution))

        and:
        file("gradle.properties") << """
            systemProp.gradle.localhost.wrapperToken=$DEFAULT_TOKEN
        """.stripIndent()
        prepareWrapper(server.uri(TEST_DISTRIBUTION_URL), keyStore).run()

        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        outputDoesNotContain('WARNING Using HTTP Bearer Token Authentication over an insecure connection to download the Gradle distribution. Please consider using HTTPS.')
    }

    def "downloads wrapper via proxy"() {
        given:
        startServer(false, false)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        and:
        proxyServer.start()

        file("gradle.properties") << """
            systemProp.https.proxyHost=localhost
            systemProp.https.proxyPort=${proxyServer.port}
            systemProp.http.nonProxyHosts=
        """.stripIndent()

        and:
        proxyServer.requestCount == 0
        prepareWrapper(getAuthenticatedBaseUrl()).run()
        proxyServer.requestCount == 1

        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))

        and:
        proxyServer.requestCount == 2
    }

    @Issue('https://github.com/gradle/gradle/issues/5052')
    def "downloads wrapper via authenticated proxy"() {
        given:
        startServer(true, false)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        and:
        prepareWrapper(getAuthenticatedBaseUrl()).run()

        and:
        proxyServer.start('my_user', 'my_password')

        file("gradle.properties") << """
            systemProp.https.proxyHost=localhost
            systemProp.https.proxyPort=${proxyServer.port}
            systemProp.http.nonProxyHosts=
            systemProp.https.proxyUser=my_user
            systemProp.https.proxyPassword=my_password
            systemProp.jdk.http.auth.tunneling.disabledSchemes=
        """.stripIndent()

        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))

        and:
        proxyServer.requestCount == 1
    }

    def "downloads wrapper via authenticated proxy and valid bearer token"() {
        given:
        startServer(false, true)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        and:
        file("gradle.properties") << """
            systemProp.gradle.wrapperToken=$DEFAULT_TOKEN
        """.stripIndent()
        prepareWrapper(server.uri(TEST_DISTRIBUTION_URL), keyStore).run()

        and:
        proxyServer.start('my_user', 'my_password')

        file("gradle.properties") << """
            systemProp.gradle.wrapperToken=$DEFAULT_TOKEN
            systemProp.https.proxyHost=localhost
            systemProp.https.proxyPort=${proxyServer.port}
            systemProp.http.nonProxyHosts=
            systemProp.https.proxyUser=my_user
            systemProp.https.proxyPassword=my_password
            systemProp.jdk.http.auth.tunneling.disabledSchemes=
        """.stripIndent()

        when:
        def result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))

        and:
        proxyServer.requestCount == 1
    }

    def "download wrapper via proxy without proxy authentication but with bearer token"() {
        given:
        startServer(false, true)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))

        and:
        file("gradle.properties") << """
            systemProp.gradle.wrapperToken=$DEFAULT_TOKEN
        """.stripIndent()
        prepareWrapper(server.uri(TEST_DISTRIBUTION_URL), keyStore).run()

        and:
        proxyServer.start('my_user', 'my_password')

        file("gradle.properties") << """
            systemProp.gradle.wrapperToken=$DEFAULT_TOKEN
            systemProp.https.proxyHost=localhost
            systemProp.https.proxyPort=${proxyServer.port}
            systemProp.http.nonProxyHosts=
            # without proxy auth
            #systemProp.https.proxyUser=my_user
            #systemProp.https.proxyPassword=my_password
            systemProp.jdk.http.auth.tunneling.disabledSchemes=
        """.stripIndent()

        when:
        ExecutionFailure failure = wrapperExecuter.withTasks('hello').withStackTraceChecksDisabled().runWithFailure()

        then:
        proxyServer.requestCount == 0

        and:
        failure.error.contains("Server returned HTTP response code: 407 for URL: ${server.uri(TEST_DISTRIBUTION_URL)}")
    }

    def "download of wrapper with invalid bearer token fails"() {
        given:
        startServer(false, true)

        and:
        file("gradle.properties") << """
            systemProp.gradle.wrapperToken=invalidToken
        """.stripIndent()
        when:
        ExecutionFailure failure = prepareWrapper(server.uri(TEST_DISTRIBUTION_URL), keyStore).runWithFailure()

        then:
        failure.error.contains("Test of distribution url ${server.uri(TEST_DISTRIBUTION_URL)} failed. Please check the values set with --gradle-distribution-url and --gradle-version.")
    }

    def "download of wrapper via authenticated proxy and invalid bearer token fails"() {
        given:
        startServer(false, true)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))

        and:
        file("gradle.properties") << """
            systemProp.gradle.wrapperToken=$DEFAULT_TOKEN
        """.stripIndent()
        prepareWrapper(server.uri(TEST_DISTRIBUTION_URL), keyStore).run()

        and:
        proxyServer.start('my_user', 'my_password')

        file("gradle.properties") << """
            # invalid token is used here
            systemProp.gradle.wrapperToken=invalid
            systemProp.https.proxyHost=localhost
            systemProp.https.proxyPort=${proxyServer.port}
            systemProp.http.nonProxyHosts=
            systemProp.https.proxyUser=my_user
            systemProp.https.proxyPassword=my_password
            systemProp.jdk.http.auth.tunneling.disabledSchemes=
        """.stripIndent()

        when:
        ExecutionFailure failure = wrapperExecuter.withTasks('hello').withStackTraceChecksDisabled().runWithFailure()

        then:
        proxyServer.requestCount == 1

        and:
        failure.error.contains("Server returned HTTP response code: 401 for URL: ${server.uri(TEST_DISTRIBUTION_URL)}")
    }

    def "validate properties file content for latest"() {
        given:
        startServer(true, false)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        def baseUrl = getAuthenticatedBaseUrl()
        prepareWrapper(baseUrl).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL")
            .sendFile(distribution.binDistribution))
        server.expect(server.get("/versions/current").send("""{ "version" : "7.6" }"""))
        server.expect(server.head("/distributions/gradle-7.6-bin.zip"))

        when:
        result = runWithVersion(baseUrl, "latest")

        then:
        validateDistributionUrl("7.6", getEscapedAuthenticatedBaseUrl())
    }

    def "validate properties file content for any version"() {
        given:
        startServer(true, false)
        server.expect(server.head("/$TEST_DISTRIBUTION_URL"))
        def baseUrl = getAuthenticatedBaseUrl()
        prepareWrapper(baseUrl).run()
        server.expect(server.get("/$TEST_DISTRIBUTION_URL").sendFile(distribution.binDistribution))

        def version = "7.6"
        server.expect(server.head("/distributions/gradle-$version-bin.zip"))

        when:
        runWithVersion(baseUrl, version)

        then:
        validateDistributionUrl(version, getEscapedAuthenticatedBaseUrl())
    }

    private String getAuthenticatedBaseUrl() {
        "https://$DEFAULT_USER:$DEFAULT_PASSWORD@localhost:${server.port}"
    }

    private String getEscapedAuthenticatedBaseUrl() {
        "https\\://$DEFAULT_USER\\:$DEFAULT_PASSWORD@localhost\\:${server.port}"
    }

    private boolean validateDistributionUrl(String version, String escapedBaseUrl) {
        file("gradle/wrapper/gradle-wrapper.properties")
            .text.contains("distributionUrl=$escapedBaseUrl/distributions/gradle-$version-bin.zip")
    }

    private ExecutionResult runWithVersion(String baseUrl, String version) {
        def jvmOpts = ["-Dorg.gradle.internal.services.base.url=$baseUrl".toString()]
        jvmOpts.addAll(keyStore.getTrustStoreArguments())
        result = wrapperExecuter.withCommandLineGradleOpts(jvmOpts)
            .withArguments("wrapper", "--gradle-version", version)
            .run()
    }

}
