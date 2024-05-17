/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.test.fixtures.server.http;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.gradle.test.fixtures.keystore.TestKeyStore;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.function.Predicate;

/**
 * An HTTPS server fixture for tests.
 *
 * Unless you really need HTTPS, you should use a plain {@link BlockingHttpServer}.
 */
public class BlockingHttpsServer extends BlockingHttpServer {
    public BlockingHttpsServer() throws IOException {
        super(HttpsServer.create(new InetSocketAddress(0), 10), 120000, Scheme.HTTPS);
    }

    /**
     * @param testKeyStore The key store to configure this server from.
     * @param tlsProtocolFilter Used to prune the supported set of TLS versions
     */
    public void configure(TestKeyStore testKeyStore, boolean needClientAuth, Predicate<String> tlsProtocolFilter) {
        HttpsServer httpsServer = (HttpsServer) this.server;
        SSLContext context = testKeyStore.asServerSSLContext();
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(context) {
            @Override
            public void configure(HttpsParameters params) {
                SSLContext c = getSSLContext();
                SSLEngine engine = c.createSSLEngine();
                params.setNeedClientAuth(needClientAuth);
                params.setCipherSuites(engine.getEnabledCipherSuites());
                // TLS protocols need to be filtered off both the HttpsParameters & SSLParameters
                params.setProtocols(stripFilteredProtocols(engine.getEnabledProtocols()));
                SSLParameters parameters = c.getDefaultSSLParameters();
                parameters.setProtocols(stripFilteredProtocols(parameters.getProtocols()));
                parameters.setNeedClientAuth(needClientAuth);
                params.setSSLParameters(parameters);
            }

            private String[] stripFilteredProtocols(String[] allProtocols) {
                return Arrays.stream(allProtocols).filter(tlsProtocolFilter).toArray(String[]::new);
            }
        });
    }

    public void configure(TestKeyStore testKeyStore) {
        configure(testKeyStore, false, __ -> true);
    }

    public void configure(TestKeyStore testKeyStore, boolean needClientAuth) {
        configure(testKeyStore, needClientAuth, __ -> true);
    }
}
