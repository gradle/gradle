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

import org.bbottema.javasocksproxyserver.TestRecordingSocksServer
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
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

    @ToBeFixedForConfigurationCache
    def "uses configured SOCKS proxy to access remote repository"() {
        proxyServer.configureProxy(executer)
        when:
        fails('listJars')
        then:
        failure.assertHasCause("Could not resolve log4j:log4j:1.2.17.")
        failure.assertThatCause(CoreMatchers.containsString("Can't connect to SOCKS proxy:Connection refused"))

        when:
        def recordingServer = new TestRecordingSocksServer()
        proxyServer.start(recordingServer)
        proxyServer.configureProxy(executer)
        fails('listJars') // Don't have to succeed here, just record the attempt in the fake proxy and verify it
        then:
        result.assertTaskExecuted(":listJars")
        recordingServer.madeAnyConnection()

        when:
        proxyServer.stop()
        proxyServer.configureProxy(executer)
        fails('listJars', '--refresh-dependencies')
        then:
        failure.assertHasCause("Could not resolve log4j:log4j:1.2.17.")
        failure.assertThatCause(CoreMatchers.containsString("Can't connect to SOCKS proxy:Connection refused"))
    }
}
