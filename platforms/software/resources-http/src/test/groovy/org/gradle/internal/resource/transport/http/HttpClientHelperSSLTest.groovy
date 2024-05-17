/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.resource.transport.http

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.BlockingHttpsServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.SetSystemProperties
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

import javax.net.ssl.SSLHandshakeException
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import java.security.Security
import java.security.UnrecoverableKeyException

class HttpClientHelperSSLTest extends Specification {
    @Rule
    SetSystemProperties properties = new SetSystemProperties()
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    @Rule
    TestResources resources = new TestResources(temporaryFolder)
    @Rule
    BlockingHttpsServer server = new BlockingHttpsServer()

    private HttpClientHelper client
    private TestKeyStore keyStore

    def cleanup() {
        client.close()
    }

    def "defaults in systemProperties"() {
        given:
        keyStore = TestKeyStore.init(resources.dir)
        setupSystemProperties()

        when:
        performAuthenticatedRequest()

        then:
        noExceptionThrown()
    }

    def "custom provider"() {
        given:
        Security.addProvider(new FakeKeyStore.Provider())
        keyStore = TestKeyStore.init(resources.dir, FakeKeyStore.Provider.algorithm)
        setupSystemProperties()

        when:
        performAuthenticatedRequest()

        then:
        noExceptionThrown()

        cleanup:
        Security.removeProvider(FakeKeyStore.Provider.name)
    }

    def "non-existent truststore file"() {
        given:
        keyStore = TestKeyStore.init(resources.dir)
        setupSystemProperties()
        System.properties["javax.net.ssl.trustStore"] = "will-not-exist"

        when:
        performAuthenticatedRequest(false)

        then:
        Exception exception = thrown()
        // exact exception depends on JDK version
        TestUtil.isOrIsCausedBy(exception, UnrecoverableKeyException.class) or(
            TestUtil.isOrIsCausedBy(exception, SSLHandshakeException.class) or(
                TestUtil.isOrIsCausedBy(exception, IOException.class)))
    }

    def "valid truststore file without specifying password"() {
        given:
        keyStore = TestKeyStore.init(resources.dir)
        setupSystemProperties()
        System.clearProperty("javax.net.ssl.trustStorePassword")

        when:
        performAuthenticatedRequest(false)

        then:
        Exception exception = thrown()
        TestUtil.isOrIsCausedBy(exception, InvalidAlgorithmParameterException.class)
    }

    def "valid truststore file with incorrect password"() {
        given:
        keyStore = TestKeyStore.init(resources.dir)
        setupSystemProperties()
        System.properties["javax.net.ssl.trustStorePassword"] = "totally-wrong"

        when:
        performAuthenticatedRequest(false)

        then:
        Exception exception = thrown()
        TestUtil.isOrIsCausedBy(exception, UnrecoverableKeyException.class)
    }

    def "non-existent keystore file"() {
        given:
        keyStore = TestKeyStore.init(resources.dir)
        setupSystemProperties()
        System.setProperty("javax.net.ssl.keyStore", "will-not-exist")

        when:
        performAuthenticatedRequest(false)

        then:
        Exception exception = thrown()
        TestUtil.getRootCause(exception) instanceof FileNotFoundException
    }

    def "non-existent keystore without auth"() {
        given:
        keyStore = TestKeyStore.init(resources.dir)
        setupSystemProperties()
        System.setProperty("javax.net.ssl.keyStore", "will-not-exist")

        when:
        performRequest(false)

        then:
        Exception exception = thrown()
        TestUtil.getRootCause(exception) instanceof FileNotFoundException
    }

    @Issue("gradle/gradle#7546")
    def "keystore type without keystore file"() {
        given:
        System.properties["javax.net.ssl.keyStoreType"] = KeyStore.defaultType

        when:
        performExternalRequest()

        then:
        noExceptionThrown()
    }

    def "keystore type with NONE keystore file"() {
        given:
        keyStore = TestKeyStore.init(resources.dir, "JKS")
        setupSystemProperties()
        System.properties["javax.net.ssl.keyStore"] = "NONE"

        when:
        performAuthenticatedRequest(false)

        then:
        Exception exception = thrown()
        TestUtil.isOrIsCausedBy(exception, HttpRequestException.class)
    }

    def "keystore type with NONE keystore file without auth"() {
        given:
        keyStore = TestKeyStore.init(resources.dir, "JKS")
        setupSystemProperties()
        System.properties["javax.net.ssl.keyStore"] = "NONE"

        when:
        performRequest()

        then:
        noExceptionThrown()
    }

    def "valid keystore file without specifying password"() {
        given:
        keyStore = TestKeyStore.init(resources.dir)
        setupSystemProperties()
        System.clearProperty("javax.net.ssl.keyStorePassword")

        when:
        performAuthenticatedRequest(false)

        // NOTE: This should fail, as the private keys password must match
        //       the keystore password, so a password is necessary
        then:
        Exception exception = thrown()
        TestUtil.isOrIsCausedBy(exception, UnrecoverableKeyException.class)
    }

    def "valid keystore file without specifying password and without auth"() {
        given:
        keyStore = TestKeyStore.init(resources.dir)
        setupSystemProperties()
        System.clearProperty("javax.net.ssl.keyStorePassword")

        when:
        performRequest(false)

        then:
        Exception exception = thrown()
        TestUtil.isOrIsCausedBy(exception, UnrecoverableKeyException.class)
    }

    def "valid keystore file with incorrect password and without auth"() {
        given:
        keyStore = TestKeyStore.init(resources.dir)
        setupSystemProperties()
        System.properties["javax.net.ssl.keyStorePassword"] = "totally-wrong"

        when:
        performRequest(false)

        then:
        Exception trustException = thrown()
        TestUtil.isOrIsCausedBy(trustException, UnrecoverableKeyException.class)
    }

    def "multiple different SSL contexts can coexist"() {
        given:
        keyStore = TestKeyStore.init(resources.dir)
        setupSystemProperties()

        when:
        performAuthenticatedRequest()

        then:
        noExceptionThrown()

        when:
        client.close()
        System.properties["javax.net.ssl.trustStore"] = "will-not-exist"
        createClient()
        client.performGet("${server.getUri()}/test", false)

        then:
        thrown(Exception)

        when:
        client.close()
        System.properties["javax.net.ssl.keyStore"] = "will-not-exist"
        createClient()
        client.performGet("${server.getUri()}/test", false)

        then:
        Exception keyStoreException = thrown()
        TestUtil.isOrIsCausedBy(keyStoreException, FileNotFoundException.class)
    }

    /**
     * Note that this is a smoke test
     * An actual test would require to make changes to the windows trust store,
     * E.g. by adding a certificate from internal-integ-testing/src/main/resources/test-key-store/trustStore
     * and changing performExternalRequest() to performRequest()
     **/
    @Requires(UnitTestPreconditions.Windows)
    def "can use windows-root trust store"() {
        given:
        System.properties["javax.net.ssl.trustStoreType"] = "Windows-ROOT"

        when:
        performExternalRequest("https://microsoft.com")

        then:
        noExceptionThrown()
    }

    private def setupSystemProperties() {
        keyStore.getServerAndClientCertSettings().each {
            System.setProperty(it.key, it.value)
        }
    }

    private def createClient() {
        SslContextFactory factory = new DefaultSslContextFactory()

        HttpSettings settings = DefaultHttpSettings.builder()
            .withAuthenticationSettings([])
            .withRedirectVerifier {}
            .withSslContextFactory(factory)
            .build()

        client = new HttpClientHelper(new DocumentationRegistry(), settings)
    }

    private def performRequest(boolean expectSuccess = true, boolean needClientAuth = false) {
        createClient()

        server.configure(keyStore, needClientAuth)
        server.start()
        if (expectSuccess) {
            server.expect("/test")
        }

        client.performGet("${server.getUri()}/test", false)
    }

    private def performAuthenticatedRequest(boolean expectSuccess = true) {
        performRequest(expectSuccess, true)
    }

    private def performExternalRequest(String targetWebsite = "https://gradle.org") {
        createClient()

        client.performGet(targetWebsite, false)
    }

}
