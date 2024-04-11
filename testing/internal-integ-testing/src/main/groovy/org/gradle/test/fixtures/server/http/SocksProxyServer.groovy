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

package org.gradle.test.fixtures.server.http

import org.bbottema.javasocksproxyserver.SocksServer
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.bbottema.javasocksproxyserver.TestRecordingSocksServer
import org.gradle.util.ports.FixedAvailablePortAllocator
import org.gradle.util.ports.PortAllocator
import org.junit.rules.ExternalResource

/**
 * A SOCKS proxy Server used for testing that proxies are correctly supported.
 *
 * You need to either always call start() and stop() in each test or use a
 * JUnit @Rule with an instance of this class.
 *
 * When used as a rule, the proxy is stopped automatically at the end of the test,
 * but it is _not_ automatically started.
 *
 * To use the proxy with a build, you must call configureProxy(GradleExecuter) before
 * starting the proxy.
 *
 * Use {@link #start(SocksServer)} to start the proxy with an alternate implementation of {@link SocksServer} (for instance,
 * a {@link TestRecordingSocksServer}, which can record connections which would have been made).
 */
class SocksProxyServer extends ExternalResource {
    private PortAllocator portFinder = FixedAvailablePortAllocator.getInstance()
    private SocksServer socksServer
    private int port

    @Override
    protected void after() {
        stop()
    }

    void start() {
        start(new SocksServer())
    }

    void start(SocksServer socksServer) {
        assert port > 0
        this.socksServer = socksServer
        socksServer.start(port)
        println(this)
    }

    void configureProxy(GradleExecuter executer) {
        if (port == 0) {
            port = portFinder.assignPort()
        }
        // Daemon | Test worker
        // build -> proxy(localhost) -> repo(localhost)
        //                           -> 127.0.0.1
        //
        executer.withArgument('-DsocksProxyHost=localhost')
        executer.withArgument("-DsocksProxyPort=${port}")
        executer.withArgument("-DsocksNonProxyHosts=127.*")
    }

    void stop() {
        socksServer?.stop()
        portFinder.releasePort(port)
    }

    @Override
    String toString() {
        if (port > 0) {
            return "SOCKS proxy: $port"
        } else {
            return "SOCKS (not started)"
        }
    }
}
