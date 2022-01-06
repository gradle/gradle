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
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.TestProxyServer
import org.gradle.util.SetSystemProperties
import org.hamcrest.CoreMatchers
import org.junit.Rule

class SocksProxyResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {

    @Rule
    SetSystemProperties systemProperties = new SetSystemProperties()
    protected TestProxyServer testSocksProxyServer
    MavenHttpModule module

    @Rule
    TestProxyServer getProxyServer() {
        if (testSocksProxyServer == null) {
            testSocksProxyServer = new TestProxyServer(TestProxyServer.Type.SOCKS)
        }
        return testSocksProxyServer
    }

    def setup() {
        module = mavenHttpRepo.module("org.gradle.test", "some-lib", "1.2.17").publish()
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

    def "uses configured SOCKS proxy to access remote repository"() {
        given:
        proxyServer.start()

        and:
        buildFile << """
repositories {
    maven { url "${mavenHttpRepo.uri}" }
}
"""
        when:
        proxyServer.configureSocksProxy(executer)
        module.allowAll()

        then:
        succeeds('listJars')
    }

    def "reports SOCKS proxy not running at configured location"() {
        given:
        buildFile << """
repositories {
    maven { url "${mavenHttpRepo.uri}" }
}
"""
        when:
        proxyServer.configureSocksProxy(executer)
        module.allowAll()

        then:
        fails('listJars')

        and:
        failure.assertHasCause("Could not resolve org.gradle.test:some-lib:1.2.17.")
        failure.assertHasCause("Could not get resource '${module.pom.uri}'")
        failure.assertThatCause(CoreMatchers.containsString("Connection refused"))
    }

}
