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

import org.gradle.test.fixtures.server.http.HttpServer
import spock.lang.Unroll

class HttpProxyResolveIntegrationTest extends AbstractProxyResolveIntegrationTest {
    @Override
    String getProxyScheme() {
        return 'http'
    }

    @Override
    String getRepoServerUrl() {
        "http://repo1.maven.org/maven2/"
    }

    boolean isTunnel() { false }

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
        configureProxyHostFor("https")

        then:
        succeeds('listJars')

        and:
        proxyServer.requestCount == 2
    }

    @Unroll
    def "passes target credentials to #authScheme authenticated server via proxy"() {
        given:
        def (proxyUserName, proxyPassword) = ['proxyUser', 'proxyPassword']
        def (repoUserName, repoPassword) = ['targetUser', 'targetPassword']
        proxyServer.start(proxyUserName, proxyPassword)
        and:
        buildFile << """
repositories {
    maven {
        url "${proxyScheme}://test:${server.port}/repo"
        credentials {
            username '$repoUserName'
            password '$repoPassword'
        }
    }
}
"""
        and:
        def repo = mavenHttpRepo
        def module = repo.module('log4j', 'log4j', '1.2.17')
        module.publish()

        when:
        server.authenticationScheme = authScheme
        configureProxy(proxyUserName, proxyPassword)

        and:
        module.pom.expectGet(repoUserName, repoPassword)
        module.artifact.expectGet(repoUserName, repoPassword)

        then:
        succeeds('listJars')

        and:
        // authentication
        // pom and jar requests
        proxyServer.requestCount == 3

        where:
        authScheme << [HttpServer.AuthScheme.BASIC, HttpServer.AuthScheme.DIGEST]
    }
}
