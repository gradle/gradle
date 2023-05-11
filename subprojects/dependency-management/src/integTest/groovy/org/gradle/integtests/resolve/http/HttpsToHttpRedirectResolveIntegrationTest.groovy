/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.resolve.http

import org.gradle.api.artifacts.repositories.UrlArtifactRepository
import org.gradle.integtests.fixtures.TestResources
import org.gradle.internal.deprecation.Documentation
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.junit.Rule

class HttpsToHttpRedirectResolveIntegrationTest extends AbstractRedirectResolveIntegrationTest {

    @Rule
    TestResources resources = new TestResources(temporaryFolder)
    TestKeyStore keyStore

    @Override
    String getFrontServerBaseUrl() {
        "https://localhost:${server.sslPort}"
    }

    @Override
    boolean defaultAllowInsecureProtocol() {
        return true
    }

    void beforeServerStart() {
        keyStore = TestKeyStore.init(resources.dir)
        keyStore.enableSslWithServerCert(server)
        keyStore.configureServerCert(executer)
    }

    def "refuses resolves module artifacts via HTTPS to HTTP redirect"() {
        given:
        buildFile << configurationWithIvyDependencyAndExpectedArtifact('group:projectA:1.0', 'projectA-1.0.jar', false)

        when:
        server.expectGetRedirected('/repo/group/projectA/1.0/ivy-1.0.xml', "${backingServer.uri}/redirected/group/projectA/1.0/ivy-1.0.xml")
        backingServer.forbidGet('/redirected/group/projectA/1.0/ivy-1.0.xml', module.ivyFile)
        server.forbidGet('/repo/group/projectA/1.0/projectA-1.0.jar')

        then:
        fails('listJars')
            .assertHasCause("Redirecting from secure protocol to insecure protocol, without explicit opt-in, is unsupported. '$frontServerBaseUrl/repo' is redirecting to '${backingServer.uri}/redirected/group/projectA/1.0/ivy-1.0.xml'.")
            .assertHasResolution("Switch Ivy repository 'ivy($frontServerBaseUrl/repo)' to redirect to a secure protocol (like HTTPS) or allow insecure protocols.")
            .assertHasResolution(Documentation.dslReference(UrlArtifactRepository, "allowInsecureProtocol").consultDocumentationMessage())
    }
}
