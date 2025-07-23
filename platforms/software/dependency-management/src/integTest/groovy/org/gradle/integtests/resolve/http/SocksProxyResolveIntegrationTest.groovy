/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.SocksProxyServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.hamcrest.CoreMatchers
import org.junit.Rule

@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "Socks proxy for localhost breaks unrelated connections")
class SocksProxyResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    @Rule
    SocksProxyServer proxyServer = new SocksProxyServer()
    @Rule
    TestResources resources = new TestResources(temporaryFolder)
    TestKeyStore keyStore
    def projectA

    def setup() {
        keyStore = TestKeyStore.init(resources.dir)
        keyStore.enableSslWithServerCert(server)

        def repo = mavenHttpRepo("repo")
        projectA = repo.module('test', 'projectA', '1.2').publish()
        buildFile << """
repositories {
    maven {
        url = "${repo.uri}"
    }
}
configurations { compile }
dependencies { compile 'test:projectA:1.2' }
task listJars {
    doFirst {
        println System.getProperty('socksProxyHost')
        println System.getProperty('socksProxyPort')
    }
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""
        proxyServer.start()
        proxyServer.configureProxy(executer)
        keyStore.configureServerAndClientCerts(executer)
    }

    def "gives a proper error message when SOCKS proxy is not available"() {
        proxyServer.stop()

        when:
        fails('listJars')

        then:
        failure.assertHasCause("Could not resolve test:projectA:1.2.")
        failure.assertThatCause(CoreMatchers.containsString("Can't connect to SOCKS proxy:Connection refused"))
    }

    def "uses configured SOCKS proxy to access remote repository"() {
        when:
        projectA.pom.expectGet()
        projectA.artifact.expectGet()
        succeeds('listJars')

        then:
        result.assertTaskExecuted(":listJars")
        proxyServer.hadConnections()
    }

    def "gives a proper error message when SOCKS proxy is not available with refresh dependencies"() {
        proxyServer.stop()

        when:
        fails('listJars', '--refresh-dependencies')

        then:
        failure.assertHasCause("Could not resolve test:projectA:1.2.")
        failure.assertThatCause(CoreMatchers.containsString("Can't connect to SOCKS proxy:Connection refused"))
    }
}
