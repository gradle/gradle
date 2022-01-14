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
import org.gradle.test.fixtures.server.http.SocksProxyServer
import org.hamcrest.CoreMatchers
import org.junit.Rule

class SocksProxyResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    @Rule
    SocksProxyServer proxyServer = new SocksProxyServer()

    def setup() {
        buildFile << """
repositories {
    ${mavenCentralRepository()}
}
configurations { compile }
dependencies { compile 'log4j:log4j:1.2.17' }
task listJars {
    doLast {
        assert configurations.compile.collect { it.name } == ['log4j-1.2.17.jar']
    }
}
"""
    }

    def "uses configured SOCKS proxy to access remote repository"() {
        proxyServer.configureProxy(executer)
        when:
        fails('listJars')
        then:
        failure.assertHasCause("Could not resolve log4j:log4j:1.2.17.")
        failure.assertThatCause(CoreMatchers.containsString("Can't connect to SOCKS proxy:Connection refused"))

        when:
        proxyServer.start()
        proxyServer.configureProxy(executer)
        succeeds('listJars')
        then:
        result.assertTaskExecuted(":listJars")

        when:
        proxyServer.stop()
        proxyServer.configureProxy(executer)
        fails('listJars', '--refresh-dependencies')
        then:
        failure.assertHasCause("Could not resolve log4j:log4j:1.2.17.")
        failure.assertThatCause(CoreMatchers.containsString("Can't connect to SOCKS proxy:Connection refused"))
    }
}
