/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.test.fixtures.file.LeaksFileHandles

@LeaksFileHandles
class HttpsProxyResolveIntegrationTest extends AbstractProxyResolveIntegrationTest {

    @Override
    String getProxyScheme() {
        return 'https'
    }

    @Override
    String getRepoServerUrl() {
        "https://repo1.maven.org/maven2/"
    }

    boolean isTunnel() { true }

    def "uses configured proxy to access remote HTTP repository when both https.proxy and http.proxy are specified"() {
        given:
        proxyServer.start()
        and:
        buildFile << """
repositories {
    maven { url "${repoServerUrl}" }
}
"""
        when:
        configureProxy()
        configureProxyHostFor("http")

        then:
        succeeds('listJars')

        and:
        proxyServer.requestCount == 1 // just tunnelling
    }

    def "can resolve from http repo with https proxy configured"() {
        given:
        proxyServer.start()
        and:
        buildFile << """
repositories {
    maven { url "http://repo1.maven.org/maven2/" }
}
"""
        when:
        configureProxy()

        then:
        succeeds('listJars')

        and:
        proxyServer.requestCount == 0
    }
}
