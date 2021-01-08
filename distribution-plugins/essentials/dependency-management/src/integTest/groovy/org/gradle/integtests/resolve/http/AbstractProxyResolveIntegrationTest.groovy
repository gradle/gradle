/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.server.http.AuthScheme
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.fixtures.server.http.TestProxyServer
import org.gradle.util.SetSystemProperties
import org.hamcrest.CoreMatchers
import org.junit.Rule
import spock.lang.Unroll

abstract class AbstractProxyResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    @Rule SetSystemProperties systemProperties = new SetSystemProperties()
    protected TestProxyServer testProxyServer
    def MavenHttpModule module

    @Rule
    TestProxyServer getProxyServer() {
        if (testProxyServer == null) {
            testProxyServer = new TestProxyServer()
        }
        return testProxyServer
    }

    abstract MavenHttpRepository getRepo()
    abstract String getProxyScheme()
    abstract String getRepoServerUrl()
    abstract boolean isTunnel()
    abstract void setupServer()

    def setup() {
        module = repo.module("org.gradle.test", "some-lib", "1.2.17").publish()
        buildFile << """
configurations { compile }
dependencies { compile 'org.gradle.test:some-lib:1.2.17' }
task listJars {
    doLast {
        assert configurations.compile.collect { it.name } == ['some-lib-1.2.17.jar']
    }
}
"""
    }

    def "uses configured proxy to access remote repository"() {
        given:
        proxyServer.start()
        setupServer()

        and:
        buildFile << """
repositories {
    maven { url "${repoServerUrl}" }
}
"""
        when:
        proxyServer.configureProxy(executer, proxyScheme)
        module.allowAll()

        then:
        succeeds('listJars')

        and:
        if (isTunnel()) {
            proxyServer.requestCount == 1
        } else {
            proxyServer.requestCount == 2
        }
    }

    def "reports proxy not running at configured location"() {
        given:
        proxyServer.start()
        proxyServer.stop()
        setupServer()

        and:
        buildFile << """
repositories {
    maven { url "${repoServerUrl}" }
}
"""
        when:
        proxyServer.configureProxy(executer, proxyScheme)
        module.allowAll()

        then:
        fails('listJars')

        and:
        failure.assertHasCause("Could not resolve org.gradle.test:some-lib:1.2.17.")
        failure.assertHasCause("Could not get resource '${module.pom.uri}'")
        failure.assertThatCause(CoreMatchers.containsString("Connection refused"))
    }

    def "uses authenticated proxy to access remote repository"() {
        given:
        def (proxyUserName, proxyPassword) = ['proxyUser', 'proxyPassword']
        proxyServer.start(proxyUserName, proxyPassword)
        setupServer()

        and:
        buildFile << """
repositories {
    maven { url "${repoServerUrl}" }
}
"""
        when:
        proxyServer.configureProxy(executer, proxyScheme, proxyUserName, proxyPassword)
        module.allowAll()

        then:
        succeeds('listJars')

        and:
        if (isTunnel()) {
            proxyServer.requestCount == 1
        } else {
            proxyServer.requestCount == 2
        }
    }

    def "reports failure due to proxy authentication failure"() {
        given:
        def (proxyUserName, proxyPassword) = ['proxyUser', 'proxyPassword']
        proxyServer.start(proxyUserName, proxyPassword)
        setupServer()

        and:
        buildFile << """
repositories {
    maven { url "${repoServerUrl}" }
}
"""
        when:
        proxyServer.configureProxy(executer, proxyScheme, proxyUserName, "not-the-password")
        module.allowAll()

        then:
        fails('listJars')

        and:
        failure.assertHasCause("Could not resolve org.gradle.test:some-lib:1.2.17.")
        failure.assertHasCause("Could not get resource '${module.pom.uri}'")
        failure.assertThatCause(CoreMatchers.containsString("Proxy Authentication Required"))
    }

    def "uses configured proxy to access remote repository when both https.proxy and http.proxy are specified"() {
        given:
        proxyServer.start()
        setupServer()

        and:
        buildFile << """
repositories {
    maven { url "${repoServerUrl}" }
}
"""
        when:
        proxyServer.configureProxy(executer, proxyScheme)
        proxyServer.configureProxyHost(executer, "http")
        proxyServer.configureProxyHost(executer, "https")
        module.allowAll()

        then:
        succeeds('listJars')

        and:
        proxyServer.requestCount == (tunnel ? 1 : 2)
    }

    def "can resolve from repo with other proxy scheme configured"() {
        given:
        proxyServer.start()
        setupServer()

        and:
        buildFile << """
repositories {
    maven { url "${repoServerUrl}" }
}
"""

        when:
        proxyServer.configureProxyHost(executer, proxyScheme == 'https' ? 'http' : 'https')
        module.allowAll()

        then:
        succeeds('listJars')

        and:
        proxyServer.requestCount == 0
    }

    @Unroll
    def "passes target credentials to #authScheme authenticated server via proxy"() {
        given:
        def (proxyUserName, proxyPassword) = ['proxyUser', 'proxyPassword']
        def (repoUserName, repoPassword) = ['targetUser', 'targetPassword']
        proxyServer.start(proxyUserName, proxyPassword)
        setupServer()

        and:
        buildFile << """
repositories {
    maven {
        url "${repoServerUrl}"
        credentials {
            username '$repoUserName'
            password '$repoPassword'
        }
    }
}
"""

        when:
        server.authenticationScheme = authScheme
        proxyServer.configureProxy(executer, proxyScheme, proxyUserName, proxyPassword)

        and:
        module.pom.expectGet(repoUserName, repoPassword)
        module.artifact.expectGet(repoUserName, repoPassword)

        then:
        succeeds('listJars')

        and:
        // authentication
        // pom and jar requests
        proxyServer.requestCount == (tunnel ? 1 : requestCount)

        where:
        authScheme        | requestCount
        AuthScheme.BASIC  | 3
        AuthScheme.DIGEST | 3
        AuthScheme.NTLM   | 4
    }
}
