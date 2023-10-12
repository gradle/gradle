/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

import javax.net.ssl.SSLHandshakeException

class DeprecatedTLSVersionTest extends Specification {
    private static final List<String> DEPRECATED_TLS_VERSIONS = ["TLSv1", "TLSv1.1"]
    private static final List<String> MODERN_TLS_VERSIONS = ["TLSv1.2", "TLSv1.3"]
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    @Rule
    TestResources resources = new TestResources(temporaryFolder)
    @Rule
    BlockingHttpsServer server = new BlockingHttpsServer()
    TestKeyStore keyStore = TestKeyStore.init(resources.dir)
    HttpSettings settings = DefaultHttpSettings.builder()
        .withAuthenticationSettings([])
        .withSslContextFactory(new DefaultSslContextFactory())
        .withRedirectVerifier({})
        .build()

    private final String supportedTlsVersionsString = String.join(", ", new HttpClientConfigurer(settings).supportedTlsVersions())

    @Rule
    SetSystemProperties properties = new SetSystemProperties(keyStore.getServerAndClientCertSettings())

    def "server that only supports deprecated TLS versions"() {
        given:
        HttpClientHelper client = new HttpClientHelper(new DocumentationRegistry(), settings)
        // Only support older TLS versions
        server.configure(keyStore, false) { it -> DEPRECATED_TLS_VERSIONS.contains(it) }
        server.start()
        // The server '/test' endpoint will never get called because the SSL exception will trip first.
        // Thus, we don't need to add the `expect('/test')` to the server for this test to work correctly.
        when:
        client.performGet("${server.getUri()}/test", false)
        then:
        HttpRequestException exception = thrown()
        exception.message.startsWith("Could not GET '")
        and:
        HttpRequestException humanReadableException = exception.cause as HttpRequestException
        humanReadableException.message.startsWith(
            "The server may not support the client's requested TLS protocol versions: ($supportedTlsVersionsString). You may need to configure the client to allow other protocols to be used. "
                + new DocumentationRegistry().getDocumentationRecommendationFor("on this", "build_environment", "sec:gradle_system_properties"))
        and:
        humanReadableException.cause instanceof SSLHandshakeException
        cleanup:
        client.close()
    }

    def "server that only supports current TLS versions"() {
        given:
        HttpClientHelper client = new HttpClientHelper(new DocumentationRegistry(), settings)
        // Only support modern TLS versions
        server.configure(keyStore, false) { it -> MODERN_TLS_VERSIONS.contains(it) }
        server.start()
        server.expect('/test')
        when:
        client.performGet("${server.getUri()}/test", false)
        then:
        noExceptionThrown()
        cleanup:
        client.close()
    }
}
