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
package org.gradle.test.fixtures.server.http

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpRequest
import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.util.ports.FixedAvailablePortAllocator
import org.junit.rules.ExternalResource
import org.littleshoot.proxy.HttpFilters
import org.littleshoot.proxy.HttpFiltersSourceAdapter
import org.littleshoot.proxy.HttpProxyServer
import org.littleshoot.proxy.ProxyAuthenticator
import org.littleshoot.proxy.impl.DefaultHttpProxyServer

import java.util.concurrent.atomic.AtomicInteger
/**
 * A Proxy Server used for testing that http proxies are correctly supported.
 */
class TestProxyServer extends ExternalResource {
    private HttpProxyServer proxyServer
    private portFinder = FixedAvailablePortAllocator.getInstance()

    int port
    AtomicInteger requestCountInternal = new AtomicInteger()

    @Override
    protected void after() {
        stop()
    }

    int getRequestCount() {
        requestCountInternal.get()
    }

    void start(final String expectedUsername = null, final String expectedPassword = null) {
        port = portFinder.assignPort()

        def filters = new HttpFiltersSourceAdapter() {
            HttpFilters filterRequest(HttpRequest originalRequest, ChannelHandlerContext ctx) {
                requestCountInternal.incrementAndGet()
                return super.filterRequest(originalRequest, ctx)
            }
        }

        def proxyAuthenticator = null
        if (expectedUsername!=null && expectedPassword!=null) {
            proxyAuthenticator = new ProxyAuthenticator() {
                @Override
                boolean authenticate(String userName, String password) {
                    return userName == expectedUsername && password == expectedPassword
                }

                @Override
                String getRealm() {
                    return null
                }
            }
        }

        proxyServer = DefaultHttpProxyServer.bootstrap()
            .withPort(port)
            .withFiltersSource(filters)
            .withProxyAuthenticator(proxyAuthenticator)
            .start()
    }

    void stop() {
        proxyServer?.stop()
        portFinder.releasePort(port)
    }

    void configureProxy(GradleExecuter executer, String proxyScheme, String userName = null, String password = null) {
        configureProxyHost(executer, proxyScheme)

        if (userName) {
            executer.withArgument("-D${proxyScheme}.proxyUser=${userName}")
        }
        if (password) {
            executer.withArgument("-D${proxyScheme}.proxyPassword=${password}")
        }
    }

    void configureProxyHost(GradleExecuter executer, String proxyScheme) {
        executer.withArgument("-D${proxyScheme}.proxyHost=localhost")
        executer.withArgument("-D${proxyScheme}.proxyPort=${port}")
        // use proxy even when accessing localhost
        executer.withArgument("-Dhttp.nonProxyHosts=${JavaVersion.current() >= JavaVersion.VERSION_1_7 ? '' : '~localhost'}")
    }
}

