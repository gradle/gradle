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
import org.gradle.test.fixtures.server.http.TestProxyServer
import org.gradle.util.SetSystemProperties
import org.junit.Rule

abstract class AbstractProxyResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    @Rule SetSystemProperties systemProperties = new SetSystemProperties()
    protected TestProxyServer testProxyServer

    @Rule
    TestProxyServer getProxyServer() {
        if (testProxyServer == null) {
            testProxyServer = new TestProxyServer()
        }
        return testProxyServer
    }

    abstract String getProxyScheme()
    abstract String getRepoServerUrl()
    abstract boolean isTunnel()

    def setup() {
        buildFile << """
configurations { compile }
dependencies { compile 'log4j:log4j:1.2.17' }
task listJars << {
    assert configurations.compile.collect { it.name } == ['log4j-1.2.17.jar']
}
"""
    }

    def "uses configured proxy to access remote HTTP repository"() {
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

        then:
        succeeds('listJars')

        and:
        if (isTunnel()) {
            proxyServer.requestCount == 1
        } else {
            proxyServer.requestCount == 2
        }
    }

    def "uses authenticated proxy to access remote HTTP repository"() {
        given:
        def (proxyUserName, proxyPassword) = ['proxyUser', 'proxyPassword']
        proxyServer.start(proxyUserName, proxyPassword)
        and:
        buildFile << """
repositories {
    maven { url "${repoServerUrl}" }
}
"""
        when:
        configureProxy(proxyUserName, proxyPassword)

        then:
        succeeds('listJars')

        and:
        if (isTunnel()) {
            proxyServer.requestCount == 1
        } else {
            proxyServer.requestCount == 2
        }
    }

    def configureProxyHostFor(String scheme) {
        executer.withArgument("-D${scheme}.proxyHost=localhost")
        executer.withArgument("-D${scheme}.proxyPort=${proxyServer.port}")
    }

    def configureProxy(String userName=null, String password=null) {
        configureProxyHostFor(proxyScheme)
        if (userName) {
            executer.withArgument("-D${proxyScheme}.proxyUser=${userName}")
        }
        if (password) {
            executer.withArgument("-D${proxyScheme}.proxyPassword=${password}")
        }
    }
}
