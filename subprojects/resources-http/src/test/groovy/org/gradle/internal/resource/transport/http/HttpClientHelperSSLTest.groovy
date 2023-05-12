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
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

import java.security.Security

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
        performRequest()

        then:
        noExceptionThrown()
    }

    def "custom provider"() {
        given:
        Security.addProvider(new FakeKeyStore.Provider());
        keyStore = TestKeyStore.init(resources.dir, FakeKeyStore.Provider.algorithm)
        setupSystemProperties()

        when:
        performRequest()

        then:
        noExceptionThrown()

        cleanup:
        Security.removeProvider(FakeKeyStore.Provider.name)
    }


    private def setupSystemProperties() {
        keyStore.getServerAndClientCertSettings().each {
            System.setProperty(it.key, it.value)
        }
    }

    private def performRequest(Boolean expectSuccess = true) {
        SslContextFactory factory = new DefaultSslContextFactory()

        HttpSettings settings = DefaultHttpSettings.builder()
            .withAuthenticationSettings([])
            .withRedirectVerifier {}
            .withSslContextFactory(factory)
            .build()

        client = new HttpClientHelper(new DocumentationRegistry(), settings)

        server.configure(keyStore)
        server.start()
        if (expectSuccess) {
            server.expect('/test')
        }

        client.performGet("${server.getUri()}/test", false)
    }

}
